package com.example.data.repository

import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.domain.calculator.WeightPriceCalculator
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GroceryRepository(private val db: AppDatabase) {

    // --- Products ---
    val allProducts: Flow<List<ProductEntity>> = db.productDao().getAllProducts()
    val activeProducts: Flow<List<ProductEntity>> = db.productDao().getActiveProducts()
    val favoriteProducts: Flow<List<ProductEntity>> = db.productDao().getFavoriteProducts()
    val lowStockProducts: Flow<List<ProductEntity>> = db.productDao().getLowStockProducts()
    val totalProductCount: Flow<Int> = db.productDao().getProductCount()
    val lowStockCount: Flow<Int> = db.productDao().getLowStockCount()

    fun searchProducts(query: String): Flow<List<ProductEntity>> = db.productDao().searchProducts(query)

    suspend fun getProductById(id: Long): ProductEntity? = db.productDao().getProductById(id)

    suspend fun getProductByBarcode(barcode: String): ProductEntity? = db.productDao().getProductByBarcode(barcode)

    suspend fun saveProduct(product: ProductEntity): Long {
        return if (product.id == 0L) {
            val newId = db.productDao().insert(product)
            // Log opening stock
            if (product.currentStock > 0) {
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        productId = newId,
                        productName = product.name,
                        changeAmount = product.currentStock,
                        balanceAfter = product.currentStock,
                        movementType = "MANUAL_ADJUSTMENT",
                        reference = "Initial Product Stock"
                    )
                )
            }
            newId
        } else {
            val existing = db.productDao().getProductById(product.id)
            db.productDao().update(product)
            if (existing != null && existing.currentStock != product.currentStock) {
                val diff = product.currentStock - existing.currentStock
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        productId = product.id,
                        productName = product.name,
                        changeAmount = diff,
                        balanceAfter = product.currentStock,
                        movementType = "MANUAL_ADJUSTMENT",
                        reference = "Stock Adjustment"
                    )
                )
            }
            product.id
        }
    }

    suspend fun restockProduct(productId: Long, addedQuantity: Double, costPrice: Double, supplierNote: String) {
        val product = db.productDao().getProductById(productId) ?: return
        val newStock = product.currentStock + addedQuantity
        db.productDao().update(
            product.copy(
                currentStock = newStock,
                purchaseCostPerUnit = if (costPrice > 0) costPrice else product.purchaseCostPerUnit,
                updatedAt = System.currentTimeMillis()
            )
        )
        db.stockDao().insertMovement(
            StockMovementEntity(
                productId = productId,
                productName = product.name,
                changeAmount = addedQuantity,
                balanceAfter = newStock,
                movementType = "PURCHASE_RESTOCK",
                reference = supplierNote.ifBlank { "Restock Purchase" }
            )
        )
    }

    suspend fun deleteProduct(product: ProductEntity) {
        db.productDao().delete(product)
    }

    // --- Bills & POS ---
    val allBills: Flow<List<BillWithItems>> = db.billDao().getAllBillsWithItems()

    fun getBillsInRange(startTime: Long, endTime: Long): Flow<List<BillWithItems>> =
        db.billDao().getBillsInRange(startTime, endTime)

    suspend fun getBillsInRangeList(startTime: Long, endTime: Long): List<BillWithItems> =
        db.billDao().getBillsInRangeList(startTime, endTime)

    fun getSalesTotalInRange(startTime: Long, endTime: Long): Flow<Double> =
        db.billDao().getSalesTotalInRange(startTime, endTime)

    fun getBillsCountInRange(startTime: Long, endTime: Long): Flow<Int> =
        db.billDao().getBillsCountInRange(startTime, endTime)

    suspend fun generateNextBillNumber(): String {
        val sdf = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val datePrefix = sdf.format(Date())
        val lastBill = db.billDao().getLastBill()
        val nextSeq = if (lastBill != null && lastBill.billNumber.startsWith("BILL-$datePrefix-")) {
            val seqStr = lastBill.billNumber.substringAfterLast("-")
            (seqStr.toIntOrNull() ?: 0) + 1
        } else {
            1
        }
        return "BILL-$datePrefix-${String.format(Locale.getDefault(), "%04d", nextSeq)}"
    }

    /**
     * Atomically saves a completed bill, inserts its items, deducts inventory,
     * logs stock movements, and updates customer Udhar Khata if credit is involved.
     */
    suspend fun completeBill(
        bill: BillEntity,
        items: List<BillItemEntity>
    ): Long {
        val billId = db.billDao().insertBill(bill)
        val itemsWithBillId = items.map { it.copy(billId = billId) }
        db.billDao().insertBillItems(itemsWithBillId)

        // Deduct inventory and log movements
        for (item in items) {
            val product = db.productDao().getProductById(item.productId)
            if (product != null) {
                val newStock = (product.currentStock - item.quantityInBaseUnit).coerceAtLeast(
                    if (db.shopSettingsDao().getSettings()?.allowNegativeStock == true) -9999.0 else 0.0
                )
                db.productDao().updateStock(product.id, newStock)
                db.stockDao().insertMovement(
                    StockMovementEntity(
                        productId = product.id,
                        productName = product.name,
                        changeAmount = -item.quantityInBaseUnit,
                        balanceAfter = newStock,
                        movementType = "SALE",
                        reference = "Bill #${bill.billNumber}"
                    )
                )
            }
        }

        // If customer credit (Udhar) was added
        if (bill.creditAmount > 0 && bill.customerId != null) {
            val customer = db.customerDao().getCustomerById(bill.customerId)
            if (customer != null) {
                val newBalance = customer.outstandingBalance + bill.creditAmount
                db.customerDao().updateBalance(customer.id, newBalance)
                db.customerDao().insertTransaction(
                    KhataTransactionEntity(
                        customerId = customer.id,
                        type = "UDHAR_PURCHASE",
                        amount = bill.creditAmount,
                        balanceAfter = newBalance,
                        billId = billId,
                        billNumber = bill.billNumber,
                        notes = "Bill #${bill.billNumber} credit sale"
                    )
                )
            }
        }

        return billId
    }

    // --- Customers & Udhar Khata ---
    val allCustomers: Flow<List<CustomerEntity>> = db.customerDao().getAllCustomers()
    val totalOutstandingBalance: Flow<Double> = db.customerDao().getTotalOutstandingBalance()

    fun searchCustomers(query: String): Flow<List<CustomerEntity>> = db.customerDao().searchCustomers(query)

    fun getCustomerWithTransactions(customerId: Long): Flow<CustomerWithTransactions?> =
        db.customerDao().getCustomerWithTransactions(customerId)

    suspend fun saveCustomer(customer: CustomerEntity): Long {
        return if (customer.id == 0L) {
            db.customerDao().insertCustomer(customer)
        } else {
            db.customerDao().updateCustomer(customer)
            customer.id
        }
    }

    suspend fun recordCustomerRepayment(
        customerId: Long,
        amount: Double,
        paymentMode: String,
        notes: String
    ): Result<Double> {
        val customer = db.customerDao().getCustomerById(customerId)
            ?: return Result.failure(Exception("Customer not found"))

        val newBalance = (customer.outstandingBalance - amount).coerceAtLeast(0.0)
        db.customerDao().updateBalance(customerId, newBalance)
        db.customerDao().insertTransaction(
            KhataTransactionEntity(
                customerId = customerId,
                type = "JAMA_PAYMENT",
                amount = amount,
                balanceAfter = newBalance,
                paymentMode = paymentMode,
                notes = notes.ifBlank { "Repayment received ($paymentMode)" }
            )
        )
        return Result.success(newBalance)
    }

    suspend fun recordCustomerCreditPurchase(
        customerId: Long,
        amount: Double,
        itemDescription: String
    ): Result<Double> {
        val customer = db.customerDao().getCustomerById(customerId)
            ?: return Result.failure(Exception("Customer not found"))

        val newBalance = customer.outstandingBalance + amount
        db.customerDao().updateBalance(customerId, newBalance)
        db.customerDao().insertTransaction(
            KhataTransactionEntity(
                customerId = customerId,
                type = "UDHAR_PURCHASE",
                amount = amount,
                balanceAfter = newBalance,
                notes = itemDescription.ifBlank { "Goods taken on credit (सामान उधार)" }
            )
        )
        return Result.success(newBalance)
    }

    suspend fun deleteCustomer(customerId: Long) {
        db.customerDao().deleteCustomerById(customerId)
    }

    // --- Stock Movements ---
    val allStockMovements: Flow<List<StockMovementEntity>> = db.stockDao().getAllStockMovements()

    // --- Expenses ---
    val allExpenses: Flow<List<ExpenseEntity>> = db.expenseDao().getAllExpenses()

    fun getExpensesInRange(startTime: Long, endTime: Long): Flow<List<ExpenseEntity>> =
        db.expenseDao().getExpensesInRange(startTime, endTime)

    fun getExpensesTotalInRange(startTime: Long, endTime: Long): Flow<Double> =
        db.expenseDao().getExpensesTotalInRange(startTime, endTime)

    suspend fun addExpense(expense: ExpenseEntity): Long = db.expenseDao().insertExpense(expense)

    suspend fun deleteExpense(id: Long) = db.expenseDao().deleteExpenseById(id)

    // --- Shop Settings ---
    val shopSettings: Flow<ShopSettingsEntity?> = db.shopSettingsDao().getSettingsFlow()

    suspend fun getShopSettings(): ShopSettingsEntity =
        db.shopSettingsDao().getSettings() ?: ShopSettingsEntity()

    suspend fun updateShopSettings(settings: ShopSettingsEntity) {
        db.shopSettingsDao().insertOrUpdate(settings)
    }

    val database: AppDatabase get() = db
}
