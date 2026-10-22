package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BillDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.ProductDao
import com.example.data.dao.ShopSettingsDao
import com.example.data.dao.StockDao
import com.example.data.entity.BillEntity
import com.example.data.entity.BillItemEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.KhataTransactionEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ShopSettingsEntity
import com.example.data.entity.StockMovementEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ProductEntity::class,
        BillEntity::class,
        BillItemEntity::class,
        CustomerEntity::class,
        KhataTransactionEntity::class,
        StockMovementEntity::class,
        ExpenseEntity::class,
        ShopSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun billDao(): BillDao
    abstract fun customerDao(): CustomerDao
    abstract fun stockDao(): StockDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shopSettingsDao(): ShopSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "smart_grocery_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(db: AppDatabase) {
                // Initialize default shop settings
                val settingsDao = db.shopSettingsDao()
                if (settingsDao.getSettings() == null) {
                    settingsDao.insertOrUpdate(ShopSettingsEntity())
                }

                // Initialize sample products
                val productDao = db.productDao()
                if (productDao.getAllProductsList().isEmpty()) {
                    productDao.insertAll(InitialData.sampleProducts)
                }

                // Initialize sample customers & khata
                val customerDao = db.customerDao()
                if (customerDao.getAllCustomersList().isEmpty()) {
                    for (c in InitialData.sampleCustomers) {
                        val cId = customerDao.insertCustomer(c)
                        if (c.outstandingBalance > 0) {
                            customerDao.insertTransaction(
                                KhataTransactionEntity(
                                    customerId = cId,
                                    type = "UDHAR_PURCHASE",
                                    amount = c.outstandingBalance,
                                    balanceAfter = c.outstandingBalance,
                                    notes = "Previous balance carried forward",
                                    timestamp = System.currentTimeMillis() - 86400000L
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
