package com.example.domain.backup

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FullBackupData(
    val formatVersion: Int = 1,
    val appSignature: String = "SMART_GROCERY_SHOP_MANAGER",
    val backupTimestamp: Long = System.currentTimeMillis(),
    val backupDateFormatted: String = "",
    val shopSettings: ShopSettingsEntity?,
    val products: List<ProductEntity>,
    val bills: List<BillEntity>,
    val billItems: List<BillItemEntity>,
    val customers: List<CustomerEntity>,
    val khataTransactions: List<KhataTransactionEntity>,
    val stockMovements: List<StockMovementEntity>,
    val expenses: List<ExpenseEntity>
)

object BackupManager {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val jsonAdapter = moshi.adapter(FullBackupData::class.java)

    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun createFullBackupJson(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val settings = db.shopSettingsDao().getSettings()
        val products = db.productDao().getAllProductsList()
        val bills = db.billDao().getAllBillsList()
        val billItems = db.billDao().getAllBillItemsList()
        val customers = db.customerDao().getAllCustomersList()
        val transactions = db.customerDao().getAllTransactionsList()
        val movements = db.stockDao().getAllStockMovementsList()
        val expenses = db.expenseDao().getAllExpensesList()

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val backupData = FullBackupData(
            formatVersion = 1,
            appSignature = "SMART_GROCERY_SHOP_MANAGER",
            backupTimestamp = System.currentTimeMillis(),
            backupDateFormatted = sdf.format(Date()),
            shopSettings = settings,
            products = products,
            bills = bills,
            billItems = billItems,
            customers = customers,
            khataTransactions = transactions,
            stockMovements = movements,
            expenses = expenses
        )

        jsonAdapter.toJson(backupData)
    }

    suspend fun createLocalBackupFile(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val json = createFullBackupJson(db)
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val fileName = "grocery_backup_${sdf.format(Date())}.json"
        val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
        val file = File(backupDir, fileName)
        FileOutputStream(file).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        file
    }

    suspend fun validateAndRestoreBackup(jsonString: String, db: AppDatabase): Result<String> = withContext(Dispatchers.IO) {
        try {
            val data = jsonAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(Exception("Invalid or corrupted backup JSON."))

            if (data.appSignature != "SMART_GROCERY_SHOP_MANAGER") {
                return@withContext Result.failure(Exception("Incompatible backup file format."))
            }

            // Restore in clean transactional steps
            if (data.shopSettings != null) {
                db.shopSettingsDao().insertOrUpdate(data.shopSettings)
            }

            if (data.products.isNotEmpty()) {
                db.productDao().deleteAll()
                db.productDao().insertAll(data.products)
            }

            if (data.bills.isNotEmpty()) {
                db.billDao().deleteAllBills()
                db.billDao().deleteAllBillItems()
                for (bill in data.bills) {
                    db.billDao().insertBill(bill)
                }
                if (data.billItems.isNotEmpty()) {
                    db.billDao().insertBillItems(data.billItems)
                }
            }

            if (data.customers.isNotEmpty()) {
                db.customerDao().deleteAllCustomers()
                db.customerDao().deleteAllTransactions()
                for (cust in data.customers) {
                    db.customerDao().insertCustomer(cust)
                }
                for (tx in data.khataTransactions) {
                    db.customerDao().insertTransaction(tx)
                }
            }

            if (data.stockMovements.isNotEmpty()) {
                db.stockDao().deleteAllMovements()
                for (m in data.stockMovements) {
                    db.stockDao().insertMovement(m)
                }
            }

            if (data.expenses.isNotEmpty()) {
                db.expenseDao().deleteAllExpenses()
                for (e in data.expenses) {
                    db.expenseDao().insertExpense(e)
                }
            }

            Result.success("Restored ${data.products.size} products, ${data.bills.size} bills, and ${data.customers.size} customers successfully.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // CSV Exports
    suspend fun exportProductsCsv(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val products = db.productDao().getAllProductsList()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val file = File(context.cacheDir, "products_${sdf.format(Date())}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("ID,Name,Hindi Name,Category,Barcode,Unit,Selling Price,Cost Price,Stock,Min Stock Alert\n")
            for (p in products) {
                writer.write("${p.id},\"${p.name}\",\"${p.hindiName}\",\"${p.category}\",\"${p.barcode ?: ""}\",${p.unitType},${p.sellingPricePerUnit},${p.purchaseCostPerUnit},${p.currentStock},${p.minStockAlert}\n")
            }
        }
        file
    }

    suspend fun exportSalesCsv(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val bills = db.billDao().getAllBillsList()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val file = File(context.cacheDir, "sales_${sdf.format(Date())}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("Bill No,Date,Customer,Phone,Subtotal,Discount,Grand Total,Payment Mode,Credit Amount\n")
            for (b in bills) {
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(b.timestamp))
                writer.write("\"${b.billNumber}\",\"$dateStr\",\"${b.customerName}\",\"${b.customerPhone}\",${b.subtotal},${b.discount},${b.grandTotal},${b.paymentMode},${b.creditAmount}\n")
            }
        }
        file
    }

    suspend fun exportCustomersCsv(context: Context, db: AppDatabase): File = withContext(Dispatchers.IO) {
        val customers = db.customerDao().getAllCustomersList()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val file = File(context.cacheDir, "customers_${sdf.format(Date())}.csv")
        file.bufferedWriter().use { writer ->
            writer.write("ID,Name,Phone,Address,Outstanding Balance\n")
            for (c in customers) {
                writer.write("${c.id},\"${c.name}\",\"${c.phone}\",\"${c.address}\",${c.outstandingBalance}\n")
            }
        }
        file
    }

    fun listLocalBackups(context: Context): List<File> {
        val backupDir = File(context.filesDir, "backups")
        if (!backupDir.exists()) return emptyList()
        return backupDir.listFiles { f -> f.extension == "json" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
