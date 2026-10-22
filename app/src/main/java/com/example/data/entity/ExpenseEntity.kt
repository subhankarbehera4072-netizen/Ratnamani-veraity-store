package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
    indices = [Index(value = ["timestamp"])]
)
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val category: String, // "STORE_RENT", "ELECTRICITY", "TRANSPORT", "TEA_SNACKS", "MAINTENANCE", "PACKAGING", "OTHER"
    val amount: Double,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
