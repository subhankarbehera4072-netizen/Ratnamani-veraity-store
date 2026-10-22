package com.example.data.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "bills",
    indices = [Index(value = ["billNumber"], unique = true), Index(value = ["timestamp"])]
)
data class BillEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val billNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val customerId: Long? = null,
    val customerName: String = "Cash Customer",
    val customerPhone: String = "",
    val subtotal: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val grandTotal: Double = 0.0,
    val paymentMode: String = "CASH", // CASH, UPI, CARD, UDHAR_CREDIT, SPLIT
    val amountReceived: Double = 0.0,
    val changeReturned: Double = 0.0,
    val creditAmount: Double = 0.0,
    val notes: String = ""
)

@Entity(
    tableName = "bill_items",
    foreignKeys = [
        ForeignKey(
            entity = BillEntity::class,
            parentColumns = ["id"],
            childColumns = ["billId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["billId"]), Index(value = ["productId"])]
)
data class BillItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val billId: Long = 0L,
    val productId: Long,
    val productName: String,
    val unitType: String,
    val unitEntered: String, // "g", "kg", "ml", "L", "pc"
    val quantityEntered: Double,
    val quantityInBaseUnit: Double,
    val ratePerBaseUnit: Double,
    val purchaseCostPerBaseUnit: Double = 0.0,
    val discount: Double = 0.0,
    val itemTotal: Double
)

data class BillWithItems(
    @Embedded val bill: BillEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "billId"
    )
    val items: List<BillItemEntity>
)
