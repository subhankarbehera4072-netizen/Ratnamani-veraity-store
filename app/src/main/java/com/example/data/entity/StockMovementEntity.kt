package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_movements",
    indices = [Index(value = ["productId"]), Index(value = ["timestamp"])]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val productId: Long,
    val productName: String,
    val changeAmount: Double, // +ve for restock/returns, -ve for sales/damages
    val balanceAfter: Double,
    val movementType: String, // "SALE", "PURCHASE_RESTOCK", "DAMAGE_LOSS", "RETURN", "MANUAL_ADJUSTMENT"
    val reference: String = "", // e.g., "Bill #101" or "Wholesaler Invoice #554"
    val unit: String = "kg",
    val timestamp: Long = System.currentTimeMillis()
)
