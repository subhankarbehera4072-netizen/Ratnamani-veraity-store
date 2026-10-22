package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_settings")
data class ShopSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val shopName: String = "Ratnamani Variety Store",
    val tagline: String = "Shuddhata aur Vishwas (Purity & Trust)",
    val ownerName: String = "Subhankar Behera",
    val phone: String = "+91 98765 43210",
    val address: String = "Main Market, Station Road, India",
    val gstin: String = "",
    val upiId: String = "grocerystore@upi",
    val currencySymbol: String = "₹",
    val language: String = "en", // "en" or "hi"
    val allowNegativeStock: Boolean = false,
    val taxRatePercent: Double = 0.0,
    val receiptFooter: String = "Thank you for shopping! Dhanyawad, Phir aaiye!",
    val adminPinHash: String = "", // Hashed PIN (e.g. default "1234")
    val adminPinSalt: String = "",
    val googleAccountEmail: String = "subhankarbehera4072@gmail.com",
    val googleDriveFolderName: String = "Smart Grocery Shop Manager Backup",
    val lastBackupTimestamp: Long = 0L,
    val lastBackupStatus: String = "No backup yet"
)
