package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SmartGroceryApp
import com.example.data.entity.*
import com.example.data.repository.GroceryRepository
import com.example.domain.backup.BackupManager
import com.example.domain.backup.DriveBackupFile
import com.example.domain.backup.GoogleDriveService
import com.example.domain.calculator.WeightPriceCalculator
import com.example.domain.security.SecurityHelper
import com.example.ui.navigation.Screen
import com.example.data.repository.CloudCustomer
import com.example.data.repository.CloudProduct
import com.example.data.repository.CloudTransaction
import com.example.data.repository.FirestoreSyncRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class GroceryViewModel(
    application: Application,
    private val repository: GroceryRepository,
    private val firestoreSync: FirestoreSyncRepository
) : AndroidViewModel(application) {

    // Current Screen
    private val _currentScreen = MutableStateFlow(Screen.DASHBOARD)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    // Admin Auth State (for Admin panel / settings)
    private val _isAdminAuthenticated = MutableStateFlow(false)
    val isAdminAuthenticated: StateFlow<Boolean> = _isAdminAuthenticated.asStateFlow()

    fun authenticateAdmin(success: Boolean) {
        _isAdminAuthenticated.value = success
    }

    // Shop Settings
    val shopSettings: StateFlow<ShopSettingsEntity> = repository.shopSettings
        .filterNotNull()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ShopSettingsEntity()
        )

    fun updateSettings(settings: ShopSettingsEntity) {
        viewModelScope.launch {
            repository.updateShopSettings(settings)
        }
    }

    fun verifyPin(pin: String): Boolean {
        val settings = shopSettings.value
        return SecurityHelper.verifyPin(pin, settings.adminPinHash, settings.adminPinSalt)
    }

    fun changePin(newPin: String) {
        val salt = SecurityHelper.generateSalt()
        val hash = SecurityHelper.hashPin(newPin, salt)
        val updated = shopSettings.value.copy(adminPinHash = hash, adminPinSalt = salt)
        updateSettings(updated)
    }

    // Products & Inventory
    val allProducts: StateFlow<List<ProductEntity>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProducts: StateFlow<List<ProductEntity>> = repository.activeProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteProducts: StateFlow<List<ProductEntity>> = repository.favoriteProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<ProductEntity>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalProductCount: StateFlow<Int> = repository.totalProductCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lowStockCount: StateFlow<Int> = repository.lowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Customers & Udhar Khata
    val allCustomers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalOutstandingBalance: StateFlow<Double> = repository.totalOutstandingBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Bills
    val allBills: StateFlow<List<BillWithItems>> = repository.allBills
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stock Movements
    val allStockMovements: StateFlow<List<StockMovementEntity>> = repository.allStockMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expenses
    val allExpenses: StateFlow<List<ExpenseEntity>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Today's range metrics
    private val todayRange: Pair<Long, Long>
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val end = cal.timeInMillis
            return start to end
        }

    val todaySalesTotal: StateFlow<Double> = repository.getSalesTotalInRange(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayBillsCount: StateFlow<Int> = repository.getBillsCountInRange(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayExpensesTotal: StateFlow<Double> = repository.getExpensesTotalInRange(todayRange.first, todayRange.second)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // ==========================================
    // BILLING / POS ACTIVE STATE
    // ==========================================
    private val _currentBillItems = MutableStateFlow<List<BillItemEntity>>(emptyList())
    val currentBillItems: StateFlow<List<BillItemEntity>> = _currentBillItems.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _billDiscount = MutableStateFlow(0.0)
    val billDiscount: StateFlow<Double> = _billDiscount.asStateFlow()

    private val _paymentMode = MutableStateFlow("CASH") // CASH, UPI, CARD, UDHAR_CREDIT
    val paymentMode: StateFlow<String> = _paymentMode.asStateFlow()

    private val _amountReceived = MutableStateFlow(0.0)
    val amountReceived: StateFlow<Double> = _amountReceived.asStateFlow()

    private val _lastCompletedBill = MutableStateFlow<BillWithItems?>(null)
    val lastCompletedBill: StateFlow<BillWithItems?> = _lastCompletedBill.asStateFlow()

    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    fun setBillDiscount(discount: Double) {
        _billDiscount.value = discount.coerceAtLeast(0.0)
    }

    fun setPaymentMode(mode: String) {
        _paymentMode.value = mode
    }

    fun setAmountReceived(amount: Double) {
        _amountReceived.value = amount
    }

    fun clearLastCompletedBill() {
        _lastCompletedBill.value = null
    }

    fun addItemToBill(
        product: ProductEntity,
        quantityEntered: Double,
        subUnit: SubUnit,
        customRate: Double? = null
    ) {
        val rate = customRate ?: product.sellingPricePerUnit
        val itemTotal = WeightPriceCalculator.calculatePrice(rate, quantityEntered, subUnit)
        val baseQuantity = WeightPriceCalculator.convertToBaseQuantity(quantityEntered, subUnit)

        val existingIndex = _currentBillItems.value.indexOfFirst {
            it.productId == product.id && it.unitEntered == subUnit.symbol
        }

        if (existingIndex >= 0) {
            val existing = _currentBillItems.value[existingIndex]
            val newQty = existing.quantityEntered + quantityEntered
            val newBaseQty = WeightPriceCalculator.convertToBaseQuantity(newQty, subUnit)
            val newTotal = WeightPriceCalculator.calculatePrice(rate, newQty, subUnit)
            val updatedList = _currentBillItems.value.toMutableList()
            updatedList[existingIndex] = existing.copy(
                quantityEntered = newQty,
                quantityInBaseUnit = newBaseQty,
                itemTotal = newTotal
            )
            _currentBillItems.value = updatedList
        } else {
            val newItem = BillItemEntity(
                productId = product.id,
                productName = product.name,
                unitType = product.unitType,
                unitEntered = subUnit.symbol,
                quantityEntered = quantityEntered,
                quantityInBaseUnit = baseQuantity,
                ratePerBaseUnit = rate,
                purchaseCostPerBaseUnit = product.purchaseCostPerUnit,
                itemTotal = itemTotal
            )
            _currentBillItems.value = _currentBillItems.value + newItem
        }
    }

    fun updateBillItem(index: Int, newQuantity: Double) {
        if (index !in _currentBillItems.value.indices) return
        val list = _currentBillItems.value.toMutableList()
        val item = list[index]
        if (newQuantity <= 0) {
            list.removeAt(index)
        } else {
            val subUnit = SubUnit.entries.firstOrNull { it.symbol == item.unitEntered } ?: SubUnit.KG
            val newBaseQty = WeightPriceCalculator.convertToBaseQuantity(newQuantity, subUnit)
            val newTotal = WeightPriceCalculator.calculatePrice(item.ratePerBaseUnit, newQuantity, subUnit)
            list[index] = item.copy(
                quantityEntered = newQuantity,
                quantityInBaseUnit = newBaseQty,
                itemTotal = newTotal
            )
        }
        _currentBillItems.value = list
    }

    fun removeBillItem(index: Int) {
        if (index in _currentBillItems.value.indices) {
            val list = _currentBillItems.value.toMutableList()
            list.removeAt(index)
            _currentBillItems.value = list
        }
    }

    fun clearBill() {
        _currentBillItems.value = emptyList()
        _selectedCustomer.value = null
        _billDiscount.value = 0.0
        _amountReceived.value = 0.0
        _paymentMode.value = "CASH"
    }

    fun submitBill(onSuccess: (BillWithItems) -> Unit, onError: (String) -> Unit) {
        val items = _currentBillItems.value
        if (items.isEmpty()) {
            onError("Bill is empty. Please add items.")
            return
        }

        viewModelScope.launch {
            try {
                val billNumber = repository.generateNextBillNumber()
                val subtotal = items.sumOf { it.itemTotal }
                val discount = _billDiscount.value
                val grandTotal = (subtotal - discount).coerceAtLeast(0.0)

                val mode = _paymentMode.value
                val customer = _selectedCustomer.value
                val received = if (mode == "CASH") _amountReceived.value else grandTotal
                val change = if (mode == "CASH" && received > grandTotal) received - grandTotal else 0.0
                val creditAmount = if (mode == "UDHAR_CREDIT") {
                    grandTotal
                } else if (mode == "CASH" && received < grandTotal && customer != null) {
                    grandTotal - received
                } else {
                    0.0
                }

                if (mode == "UDHAR_CREDIT" && customer == null) {
                    onError("Please select a customer for Udhar (Credit) bill.")
                    return@launch
                }

                val bill = BillEntity(
                    billNumber = billNumber,
                    timestamp = System.currentTimeMillis(),
                    customerId = customer?.id,
                    customerName = customer?.name ?: "Cash Customer",
                    customerPhone = customer?.phone ?: "",
                    subtotal = subtotal,
                    discount = discount,
                    tax = 0.0,
                    grandTotal = grandTotal,
                    paymentMode = mode,
                    amountReceived = received,
                    changeReturned = change,
                    creditAmount = creditAmount
                )

                val billId = repository.completeBill(bill, items)
                val completed = BillWithItems(bill.copy(id = billId), items.map { it.copy(billId = billId) })
                _lastCompletedBill.value = completed
                clearBill()
                onSuccess(completed)
            } catch (e: Exception) {
                onError("Failed to save bill: ${e.message}")
            }
        }
    }

    // ==========================================
    // PRODUCT ACTIONS
    // ==========================================
    fun saveProduct(product: ProductEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            val newId = repository.saveProduct(product)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                try {
                    val pId = if (product.id != 0L) product.id else newId
                    firestoreSync.saveProduct(
                        uid,
                        CloudProduct(
                            id = "prod_$pId",
                            userId = uid,
                            name = product.name,
                            category = product.category,
                            price = product.sellingPricePerUnit,
                            costPrice = product.purchaseCostPerUnit,
                            stockQuantity = product.currentStock,
                            unit = product.unitType,
                            barcode = product.barcode ?: "",
                            minStockAlert = product.minStockAlert
                        )
                    )
                } catch (e: Exception) {
                    // Handled inside firestoreSync
                }
            }
            onDone()
        }
    }

    fun deleteProduct(product: ProductEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                try {
                    firestoreSync.deleteProduct(uid, "prod_${product.id}")
                } catch (e: Exception) {
                    // Handled inside firestoreSync
                }
            }
            onDone()
        }
    }

    fun restockProduct(productId: Long, quantity: Double, costPrice: Double, note: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.restockProduct(productId, quantity, costPrice, note)
            val updated = repository.getProductById(productId)
            if (updated != null) {
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    try {
                        firestoreSync.saveProduct(
                            uid,
                            CloudProduct(
                                id = "prod_$productId",
                                userId = uid,
                                name = updated.name,
                                category = updated.category,
                                price = updated.sellingPricePerUnit,
                                costPrice = updated.purchaseCostPerUnit,
                                stockQuantity = updated.currentStock,
                                unit = updated.unitType,
                                barcode = updated.barcode ?: "",
                                minStockAlert = updated.minStockAlert
                            )
                        )
                    } catch (e: Exception) {
                        // Handled
                    }
                }
            }
            onDone()
        }
    }

    // ==========================================
    // CUSTOMER & KHATA ACTIONS
    // ==========================================
    fun saveCustomer(customer: CustomerEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            val isNew = customer.id == 0L
            val newId = repository.saveCustomer(customer)
            val cId = if (isNew) newId else customer.id

            // If new customer with opening balance, record transaction
            if (isNew && customer.outstandingBalance > 0) {
                try {
                    repository.database.customerDao().insertTransaction(
                        KhataTransactionEntity(
                            customerId = newId,
                            type = "UDHAR_PURCHASE",
                            amount = customer.outstandingBalance,
                            balanceAfter = customer.outstandingBalance,
                            notes = "Opening balance (पिछला बकाया / ପୂର୍ବ ବାକି)"
                        )
                    )
                } catch (e: Exception) {
                    // Handled
                }
            }

            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                try {
                    firestoreSync.saveCustomer(
                        uid,
                        CloudCustomer(
                            id = "cust_$cId",
                            userId = uid,
                            name = customer.name,
                            phone = customer.phone,
                            address = customer.address,
                            totalUdhar = customer.outstandingBalance
                        )
                    )
                    if (isNew && customer.outstandingBalance > 0) {
                        firestoreSync.saveTransaction(
                            uid,
                            CloudTransaction(
                                id = "tx_${System.currentTimeMillis()}",
                                userId = uid,
                                customerId = "cust_$cId",
                                amount = customer.outstandingBalance,
                                type = "UDHAR",
                                note = "Opening balance (उधार)",
                                itemsSummary = "Opening balance"
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Handled
                }
            }
            onDone()
        }
    }

    fun recordCustomerRepayment(
        customerId: Long,
        amount: Double,
        mode: String,
        notes: String,
        onSuccess: (Double) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.recordCustomerRepayment(customerId, amount, mode, notes)
            result.onSuccess { newBal ->
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    try {
                        val cust = repository.database.customerDao().getCustomerById(customerId)
                        if (cust != null) {
                            firestoreSync.saveCustomer(
                                uid,
                                CloudCustomer(
                                    id = "cust_$customerId",
                                    userId = uid,
                                    name = cust.name,
                                    phone = cust.phone,
                                    address = cust.address,
                                    totalUdhar = newBal
                                )
                            )
                            firestoreSync.saveTransaction(
                                uid,
                                CloudTransaction(
                                    id = "tx_${System.currentTimeMillis()}",
                                    userId = uid,
                                    customerId = "cust_$customerId",
                                    amount = amount,
                                    type = "JAMA",
                                    note = notes.ifBlank { "Payment received ($mode)" },
                                    itemsSummary = "Payment received ($mode)"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Handled
                    }
                }
                onSuccess(newBal)
            }
            result.onFailure { err -> onError(err.message ?: "Failed") }
        }
    }

    fun recordCustomerCreditPurchase(
        customerId: Long,
        amount: Double,
        itemDescription: String,
        onSuccess: (Double) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.recordCustomerCreditPurchase(customerId, amount, itemDescription)
            result.onSuccess { newBal ->
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    try {
                        val cust = repository.database.customerDao().getCustomerById(customerId)
                        if (cust != null) {
                            firestoreSync.saveCustomer(
                                uid,
                                CloudCustomer(
                                    id = "cust_$customerId",
                                    userId = uid,
                                    name = cust.name,
                                    phone = cust.phone,
                                    address = cust.address,
                                    totalUdhar = newBal
                                )
                            )
                            firestoreSync.saveTransaction(
                                uid,
                                CloudTransaction(
                                    id = "tx_${System.currentTimeMillis()}",
                                    userId = uid,
                                    customerId = "cust_$customerId",
                                    amount = amount,
                                    type = "UDHAR",
                                    note = itemDescription.ifBlank { "Credit purchase" },
                                    itemsSummary = itemDescription.ifBlank { "Credit purchase" }
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Handled
                    }
                }
                onSuccess(newBal)
            }
            result.onFailure { err -> onError(err.message ?: "Failed") }
        }
    }

    fun adjustCustomerBalance(
        customerId: Long,
        newBalance: Double,
        reason: String,
        onSuccess: (Double) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val cust = repository.database.customerDao().getCustomerById(customerId)
            if (cust == null) {
                onError("Customer not found")
                return@launch
            }
            val diff = newBalance - cust.outstandingBalance
            if (Math.abs(diff) < 0.01) {
                onSuccess(newBalance)
                return@launch
            }
            if (diff > 0) {
                recordCustomerCreditPurchase(
                    customerId = customerId,
                    amount = diff,
                    itemDescription = reason.ifBlank { "Balance adjustment (+Udhar)" },
                    onSuccess = onSuccess,
                    onError = onError
                )
            } else {
                recordCustomerRepayment(
                    customerId = customerId,
                    amount = -diff,
                    mode = "ADJUSTMENT",
                    notes = reason.ifBlank { "Balance adjustment (-Jama)" },
                    onSuccess = onSuccess,
                    onError = onError
                )
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCustomer(customer.id)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                try {
                    firestoreSync.deleteCustomer(uid, "cust_${customer.id}")
                } catch (e: Exception) {
                    // Handled
                }
            }
            onDone()
        }
    }

    // ==========================================
    // CLOUD SYNC & MULTI-PHONE RESTORE
    // ==========================================
    suspend fun syncLocalToCloud(userId: String): Result<Int> {
        val products = repository.database.productDao().getAllProductsList()
        val customers = repository.database.customerDao().getAllCustomersList()
        val transactions = repository.database.customerDao().getAllTransactionsList()
        val settings = repository.getShopSettings()
        return firestoreSync.backupAllToCloud(userId, products, customers, transactions, settings)
    }

    suspend fun restoreFromCloud(userId: String): Result<Int> {
        val res = firestoreSync.restoreAllFromCloud(userId)
        return res.mapCatching { cloudData ->
            var count = 0
            for (cp in cloudData.products) {
                val entity = ProductEntity(
                    id = cp.id.removePrefix("prod_").toLongOrNull() ?: 0L,
                    name = cp.name,
                    category = cp.category,
                    sellingPricePerUnit = cp.price,
                    purchaseCostPerUnit = cp.costPrice,
                    currentStock = cp.stockQuantity,
                    unitType = cp.unit,
                    barcode = cp.barcode.ifBlank { null },
                    minStockAlert = cp.minStockAlert,
                    updatedAt = System.currentTimeMillis()
                )
                repository.database.productDao().insert(entity)
                count++
            }
            for (cc in cloudData.customers) {
                val entity = CustomerEntity(
                    id = cc.id.removePrefix("cust_").toLongOrNull() ?: 0L,
                    name = cc.name,
                    phone = cc.phone,
                    address = cc.address,
                    outstandingBalance = cc.totalUdhar,
                    updatedAt = System.currentTimeMillis()
                )
                repository.database.customerDao().insertCustomer(entity)
                count++
            }
            for (ct in cloudData.transactions) {
                val custId = ct.customerId.removePrefix("cust_").toLongOrNull() ?: 0L
                if (custId > 0) {
                    val tx = KhataTransactionEntity(
                        id = ct.id.removePrefix("tx_").toLongOrNull() ?: 0L,
                        customerId = custId,
                        type = if (ct.type.contains("JAMA")) "JAMA_PAYMENT" else "UDHAR_PURCHASE",
                        amount = ct.amount,
                        balanceAfter = 0.0,
                        notes = ct.note,
                        timestamp = ct.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                    )
                    repository.database.customerDao().insertTransaction(tx)
                    count++
                }
            }
            count
        }
    }

    // ==========================================
    // EXPENSES
    // ==========================================
    fun addExpense(category: String, amount: Double, desc: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.addExpense(
                ExpenseEntity(
                    category = category,
                    amount = amount,
                    description = desc
                )
            )
            onDone()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            repository.deleteExpense(id)
        }
    }

    // ==========================================
    // BACKUP & RESTORE
    // ==========================================
    private val _backupStatusMessage = MutableStateFlow<String?>(null)
    val backupStatusMessage: StateFlow<String?> = _backupStatusMessage.asStateFlow()

    private val _isBackupInProgress = MutableStateFlow(false)
    val isBackupInProgress: StateFlow<Boolean> = _isBackupInProgress.asStateFlow()

    private val _remoteBackupsList = MutableStateFlow<List<DriveBackupFile>>(emptyList())
    val remoteBackupsList: StateFlow<List<DriveBackupFile>> = _remoteBackupsList.asStateFlow()

    fun createLocalBackup(onSuccess: (File) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            try {
                val file = BackupManager.createLocalBackupFile(getApplication(), repository.database)
                _backupStatusMessage.value = "Local backup saved: ${file.name}"
                repository.updateShopSettings(
                    shopSettings.value.copy(
                        lastBackupTimestamp = System.currentTimeMillis(),
                        lastBackupStatus = "Local backup saved: ${file.name}"
                    )
                )
                onSuccess(file)
            } catch (e: Exception) {
                onError("Failed: ${e.message}")
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }

    fun restoreFromLocalJson(jsonString: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            try {
                val res = BackupManager.validateAndRestoreBackup(jsonString, repository.database)
                res.onSuccess { msg ->
                    _backupStatusMessage.value = msg
                    onSuccess(msg)
                }.onFailure { err ->
                    onError(err.message ?: "Restore failed")
                }
            } catch (e: Exception) {
                onError("Failed: ${e.message}")
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }

    fun uploadToGoogleDrive(
        accessToken: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val app = getApplication<SmartGroceryApp>()
        if (!BackupManager.isInternetAvailable(app)) {
            onError("Internet is required for Google Drive backup. Please check Wi-Fi/Mobile Data.")
            return
        }

        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = "Connecting to Google Drive..."
            try {
                val folderName = shopSettings.value.googleDriveFolderName
                val folderRes = GoogleDriveService.getOrCreateBackupFolder(accessToken, folderName)
                val folderId = folderRes.getOrThrow()

                _backupStatusMessage.value = "Generating backup payload..."
                val json = BackupManager.createFullBackupJson(repository.database)

                _backupStatusMessage.value = "Uploading to '$folderName'..."
                val uploadRes = GoogleDriveService.uploadBackupFile(accessToken, folderId, json)
                val uploadMsg = uploadRes.getOrThrow()

                _backupStatusMessage.value = "Backup completed! $uploadMsg"
                repository.updateShopSettings(
                    shopSettings.value.copy(
                        lastBackupTimestamp = System.currentTimeMillis(),
                        lastBackupStatus = "Google Drive: $uploadMsg"
                    )
                )
                onSuccess(uploadMsg)
            } catch (e: Exception) {
                _backupStatusMessage.value = "Upload error: ${e.message}"
                onError("Drive upload error: ${e.message}")
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }

    fun fetchRemoteDriveBackups(accessToken: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val folderName = shopSettings.value.googleDriveFolderName
                val folderRes = GoogleDriveService.getOrCreateBackupFolder(accessToken, folderName)
                val folderId = folderRes.getOrThrow()
                val listRes = GoogleDriveService.listRemoteBackups(accessToken, folderId)
                _remoteBackupsList.value = listRes.getOrThrow()
            } catch (e: Exception) {
                onError("Failed to list backups: ${e.message}")
            }
        }
    }

    fun restoreFromDrive(
        accessToken: String,
        fileId: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBackupInProgress.value = true
            _backupStatusMessage.value = "Downloading and restoring backup..."
            try {
                val res = GoogleDriveService.downloadAndRestoreBackup(accessToken, fileId, repository.database)
                res.onSuccess { msg ->
                    _backupStatusMessage.value = msg
                    onSuccess(msg)
                }.onFailure { err ->
                    onError(err.message ?: "Failed to restore")
                }
            } catch (e: Exception) {
                onError("Failed: ${e.message}")
            } finally {
                _isBackupInProgress.value = false
            }
        }
    }
}
