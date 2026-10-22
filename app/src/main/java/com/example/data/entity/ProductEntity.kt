package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val hindiName: String = "",
    val category: String = "General Grocery",
    val barcode: String? = null,
    val unitType: String = UnitType.KG.name,
    val sellingPricePerUnit: Double, // Price per base unit (e.g. per 1 kg, per 1 L, per 1 pc)
    val purchaseCostPerUnit: Double = 0.0, // Cost price per base unit
    val currentStock: Double = 0.0, // Stock in base units
    val minStockAlert: Double = 5.0, // Low-stock threshold
    val supplier: String = "",
    val expiryDate: String? = null,
    val isEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
