package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.entity.CustomerEntity
import com.example.data.entity.KhataTransactionEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ShopSettingsEntity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Exception, operationType: OperationType, path: String?): String {
    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", exception.message ?: exception.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@IgnoreExtraProperties
data class CloudProduct(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "General Grocery",
    val price: Double = 0.0,
    val costPrice: Double = 0.0,
    val stockQuantity: Double = 0.0,
    val unit: String = "pcs",
    val barcode: String = "",
    val minStockAlert: Double = 5.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudCustomer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val address: String = "",
    val totalUdhar: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val customerId: String = "",
    val amount: Double = 0.0,
    val type: String = "UDHAR",
    val note: String = "",
    val itemsSummary: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudShopSettings(
    @DocumentId val id: String = "current",
    val userId: String = "",
    val shopName: String = "",
    val ownerName: String = "",
    val phone: String = "",
    val address: String = "",
    val upiId: String = "",
    val currencySymbol: String = "₹",
    val lowStockThreshold: Double = 5.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class CloudRestoreData(
    val products: List<CloudProduct>,
    val customers: List<CloudCustomer>,
    val transactions: List<CloudTransaction>,
    val settings: CloudShopSettings?
)

class FirestoreSyncRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = FirebaseAuth.getInstance()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // Save or update product in Firestore
    suspend fun saveProduct(userId: String, product: CloudProduct) {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val docId = if (product.id.isNotBlank()) product.id else "prod_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(targetUid).collection("products").document(docId)

        val payload = hashMapOf<String, Any>(
            "userId" to targetUid,
            "name" to product.name,
            "category" to product.category,
            "price" to product.price,
            "costPrice" to product.costPrice,
            "stockQuantity" to product.stockQuantity,
            "unit" to product.unit,
            "barcode" to product.barcode,
            "minStockAlert" to product.minStockAlert,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (product.createdAt == null) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }

        try {
            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Delete product from Firestore
    suspend fun deleteProduct(userId: String, productId: String) {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val docRef = db.collection("users").document(targetUid).collection("products").document(productId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // Save or update customer in Firestore
    suspend fun saveCustomer(userId: String, customer: CloudCustomer) {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val docId = if (customer.id.isNotBlank()) customer.id else "cust_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(targetUid).collection("customers").document(docId)

        val payload = hashMapOf<String, Any>(
            "userId" to targetUid,
            "name" to customer.name,
            "phone" to customer.phone,
            "address" to customer.address,
            "totalUdhar" to customer.totalUdhar,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (customer.createdAt == null) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }

        try {
            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Save transaction in Firestore
    suspend fun saveTransaction(userId: String, tx: CloudTransaction) {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val docId = if (tx.id.isNotBlank()) tx.id else "tx_${System.currentTimeMillis()}"
        val docRef = db.collection("users").document(targetUid).collection("transactions").document(docId)

        val payload = hashMapOf<String, Any>(
            "userId" to targetUid,
            "customerId" to tx.customerId,
            "amount" to tx.amount,
            "type" to tx.type,
            "note" to tx.note,
            "itemsSummary" to tx.itemsSummary,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (tx.createdAt == null) {
            payload["createdAt"] = FieldValue.serverTimestamp()
        }

        try {
            docRef.set(payload, SetOptions.merge()).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    // Delete customer from Firestore
    suspend fun deleteCustomer(userId: String, customerId: String) {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val docId = if (customerId.startsWith("cust_")) customerId else "cust_$customerId"
        val docRef = db.collection("users").document(targetUid).collection("customers").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // Get transactions for customer from Firestore
    suspend fun getTransactionsForCustomer(userId: String, customerId: String): List<CloudTransaction> {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val custDocId = if (customerId.startsWith("cust_")) customerId else "cust_$customerId"
        return try {
            val snapshot = db.collection("users").document(targetUid).collection("transactions")
                .whereEqualTo("customerId", custDocId)
                .get()
                .await()
            snapshot.toObjects(CloudTransaction::class.java)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "users/$targetUid/transactions")
            emptyList()
        }
    }

    // Backup all local room data to cloud
    suspend fun backupAllToCloud(
        userId: String,
        products: List<ProductEntity>,
        customers: List<CustomerEntity>,
        transactions: List<KhataTransactionEntity>,
        settings: ShopSettingsEntity
    ): Result<Int> {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        return try {
            var count = 0
            val batch = db.batch()

            // Shop Settings
            val settingsRef = db.collection("users").document(targetUid).collection("shop_settings").document("current")
            batch.set(
                settingsRef,
                hashMapOf(
                    "userId" to targetUid,
                    "shopName" to settings.shopName,
                    "ownerName" to settings.ownerName,
                    "phone" to settings.phone,
                    "address" to settings.address,
                    "upiId" to settings.upiId,
                    "currencySymbol" to settings.currencySymbol,
                    "lowStockThreshold" to 5.0,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            count++

            // Products
            for (p in products) {
                val pRef = db.collection("users").document(targetUid).collection("products").document("prod_${p.id}")
                batch.set(
                    pRef,
                    hashMapOf(
                        "userId" to targetUid,
                        "name" to p.name,
                        "category" to p.category,
                        "price" to p.sellingPricePerUnit,
                        "costPrice" to p.purchaseCostPerUnit,
                        "stockQuantity" to p.currentStock,
                        "unit" to p.unitType,
                        "barcode" to (p.barcode ?: ""),
                        "minStockAlert" to p.minStockAlert,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                count++
            }

            // Customers
            for (c in customers) {
                val cRef = db.collection("users").document(targetUid).collection("customers").document("cust_${c.id}")
                batch.set(
                    cRef,
                    hashMapOf(
                        "userId" to targetUid,
                        "name" to c.name,
                        "phone" to c.phone,
                        "address" to c.address,
                        "totalUdhar" to c.outstandingBalance,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                count++
            }

            // Transactions
            for (t in transactions) {
                val tRef = db.collection("users").document(targetUid).collection("transactions").document("tx_${t.id}")
                val mappedType = if (t.type.contains("JAMA")) "JAMA" else "UDHAR"
                batch.set(
                    tRef,
                    hashMapOf(
                        "userId" to targetUid,
                        "customerId" to "cust_${t.customerId}",
                        "amount" to t.amount,
                        "type" to mappedType,
                        "note" to t.notes,
                        "itemsSummary" to t.notes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                count++
            }

            batch.commit().await()
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "users/$targetUid")
            Result.failure(e)
        }
    }

    // Restore all cloud data
    suspend fun restoreAllFromCloud(userId: String): Result<CloudRestoreData> {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        return try {
            val userDoc = db.collection("users").document(targetUid)

            val pDocs = userDoc.collection("products").get().await()
            val productList = pDocs.toObjects(CloudProduct::class.java)

            val cDocs = userDoc.collection("customers").get().await()
            val customerList = cDocs.toObjects(CloudCustomer::class.java)

            val tDocs = userDoc.collection("transactions").get().await()
            val transactionList = tDocs.toObjects(CloudTransaction::class.java)

            val sDoc = userDoc.collection("shop_settings").document("current").get().await()
            val shopSettings = sDoc.toObject(CloudShopSettings::class.java)

            Result.success(
                CloudRestoreData(
                    products = productList,
                    customers = customerList,
                    transactions = transactionList,
                    settings = shopSettings
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "users/$targetUid")
            Result.failure(e)
        }
    }

    // Realtime observation flows
    fun observeCloudProducts(userId: String): Flow<List<CloudProduct>> = callbackFlow {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val path = "users/$targetUid/products"
        val registration = db.collection("users").document(targetUid).collection("products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(CloudProduct::class.java))
                }
            }
        awaitClose { registration.remove() }
    }

    fun observeCloudCustomers(userId: String): Flow<List<CloudCustomer>> = callbackFlow {
        val targetUid = if (userId.isNotBlank()) userId else requireUserId()
        val path = "users/$targetUid/customers"
        val registration = db.collection("users").document(targetUid).collection("customers")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    handleFirestoreError(error, OperationType.LIST, path)
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    trySend(snapshot.toObjects(CloudCustomer::class.java))
                }
            }
        awaitClose { registration.remove() }
    }
}
