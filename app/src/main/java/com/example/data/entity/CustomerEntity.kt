package com.example.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "customers",
    indices = [Index(value = ["phone"])]
)
data class CustomerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phone: String,
    val address: String = "",
    val outstandingBalance: Double = 0.0, // Positive means customer owes money (Udhar)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "khata_transactions",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["customerId"]), Index(value = ["timestamp"])]
)
data class KhataTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val customerId: Long,
    val type: String, // "UDHAR_PURCHASE" (debit), "JAMA_PAYMENT" (credit repayment)
    val amount: Double,
    val balanceAfter: Double,
    val billId: Long? = null,
    val billNumber: String? = null,
    val paymentMode: String = "CASH", // CASH, UPI, etc.
    val notes: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomerWithTransactions(
    @Embedded val customer: CustomerEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "customerId"
    )
    val transactions: List<KhataTransactionEntity>
)
