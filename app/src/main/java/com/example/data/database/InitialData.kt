package com.example.data.database

import com.example.data.entity.CustomerEntity
import com.example.data.entity.ProductEntity
import com.example.data.entity.ShopSettingsEntity
import com.example.data.entity.UnitType

object InitialData {
    val sampleProducts = listOf(
        ProductEntity(
            id = 1L,
            name = "Sugar (Cheeni)",
            hindiName = "चीनी (शुद्ध)",
            category = "Grains & Essentials",
            barcode = "890103000001",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 48.0,
            purchaseCostPerUnit = 42.0,
            currentStock = 50.0,
            minStockAlert = 10.0,
            supplier = "Local Mandi",
            isFavorite = true
        ),
        ProductEntity(
            id = 2L,
            name = "Basmati Rice (Chawal)",
            hindiName = "बासमती चावल",
            category = "Grains & Essentials",
            barcode = "890103000002",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 60.0,
            purchaseCostPerUnit = 50.0,
            currentStock = 45.0,
            minStockAlert = 8.0,
            supplier = "Punjab Rice Mill",
            isFavorite = true
        ),
        ProductEntity(
            id = 3L,
            name = "Wheat Flour (Sharbati Aata)",
            hindiName = "शरबती गेहूँ आटा",
            category = "Grains & Essentials",
            barcode = "890103000003",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 38.0,
            purchaseCostPerUnit = 32.0,
            currentStock = 80.0,
            minStockAlert = 15.0,
            supplier = "Aashirvaad Wholesaler",
            isFavorite = true
        ),
        ProductEntity(
            id = 4L,
            name = "Arhar / Toor Dal (Desi)",
            hindiName = "अरहर / तुवर दाल",
            category = "Pulses & Dals",
            barcode = "890103000004",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 160.0,
            purchaseCostPerUnit = 135.0,
            currentStock = 25.0,
            minStockAlert = 5.0,
            supplier = "Dal Mandi",
            isFavorite = true
        ),
        ProductEntity(
            id = 5L,
            name = "Moong Dal Dhuli",
            hindiName = "मूंग दाल धुली",
            category = "Pulses & Dals",
            barcode = "890103000005",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 110.0,
            purchaseCostPerUnit = 92.0,
            currentStock = 20.0,
            minStockAlert = 5.0,
            supplier = "Dal Mandi",
            isFavorite = false
        ),
        ProductEntity(
            id = 6L,
            name = "Chana Dal (Special)",
            hindiName = "चना दाल",
            category = "Pulses & Dals",
            barcode = "890103000006",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 90.0,
            purchaseCostPerUnit = 75.0,
            currentStock = 30.0,
            minStockAlert = 6.0,
            supplier = "Dal Mandi",
            isFavorite = false
        ),
        ProductEntity(
            id = 7L,
            name = "Masoor Dal (Malka)",
            hindiName = "मलका मसूर दाल",
            category = "Pulses & Dals",
            barcode = "890103000007",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 95.0,
            purchaseCostPerUnit = 80.0,
            currentStock = 18.0,
            minStockAlert = 4.0,
            supplier = "Dal Mandi",
            isFavorite = false
        ),
        ProductEntity(
            id = 8L,
            name = "Tata Salt (Iodized 1kg)",
            hindiName = "टाटा नमक 1kg",
            category = "Spices & Salt",
            barcode = "890103000008",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 28.0,
            purchaseCostPerUnit = 24.0,
            currentStock = 50.0,
            minStockAlert = 10.0,
            supplier = "Tata Consumer",
            isFavorite = true
        ),
        ProductEntity(
            id = 9L,
            name = "Mustard Oil (Kachi Ghani)",
            hindiName = "कच्ची घानी सरसों तेल",
            category = "Oils & Ghee",
            barcode = "890103000009",
            unitType = UnitType.LITRE.name,
            sellingPricePerUnit = 150.0,
            purchaseCostPerUnit = 130.0,
            currentStock = 40.0,
            minStockAlert = 8.0,
            supplier = "Fortune Distr.",
            isFavorite = true
        ),
        ProductEntity(
            id = 10L,
            name = "Refined Sunflower Oil",
            hindiName = "रिफाइंड सूर्यमुखी तेल",
            category = "Oils & Ghee",
            barcode = "890103000010",
            unitType = UnitType.LITRE.name,
            sellingPricePerUnit = 135.0,
            purchaseCostPerUnit = 118.0,
            currentStock = 35.0,
            minStockAlert = 7.0,
            supplier = "Fortune Distr.",
            isFavorite = false
        ),
        ProductEntity(
            id = 11L,
            name = "Tata Tea Gold (250g Pouch)",
            hindiName = "टाटा टी गोल्ड चाय",
            category = "Beverages & Tea",
            barcode = "890103000011",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 140.0,
            purchaseCostPerUnit = 120.0,
            currentStock = 30.0,
            minStockAlert = 6.0,
            supplier = "Tata Consumer",
            isFavorite = true
        ),
        ProductEntity(
            id = 12L,
            name = "Parle-G Glucose Biscuits",
            hindiName = "पारले-जी बिस्कुट",
            category = "Snacks & Biscuits",
            barcode = "890103000012",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 10.0,
            purchaseCostPerUnit = 8.5,
            currentStock = 100.0,
            minStockAlert = 20.0,
            supplier = "Parle Products",
            isFavorite = true
        ),
        ProductEntity(
            id = 13L,
            name = "Dettol Bath Soap (75g)",
            hindiName = "डेटॉल साबुन 75g",
            category = "Cleaning & Personal",
            barcode = "890103000013",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 40.0,
            purchaseCostPerUnit = 34.0,
            currentStock = 45.0,
            minStockAlert = 10.0,
            supplier = "Reckitt Benckiser",
            isFavorite = false
        ),
        ProductEntity(
            id = 14L,
            name = "Fresh Potatoes (Aaloo)",
            hindiName = "ताजा आलू (Aaloo)",
            category = "Vegetables",
            barcode = "890103000014",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 30.0,
            purchaseCostPerUnit = 22.0,
            currentStock = 60.0,
            minStockAlert = 12.0,
            supplier = "Sabzi Mandi",
            isFavorite = true
        ),
        ProductEntity(
            id = 15L,
            name = "Fresh Onions (Pyaaz)",
            hindiName = "ताजा प्याज (Pyaaz)",
            category = "Vegetables",
            barcode = "890103000015",
            unitType = UnitType.KG.name,
            sellingPricePerUnit = 40.0,
            purchaseCostPerUnit = 32.0,
            currentStock = 50.0,
            minStockAlert = 10.0,
            supplier = "Sabzi Mandi",
            isFavorite = true
        ),
        ProductEntity(
            id = 16L,
            name = "Turmeric Powder (Haldi 200g)",
            hindiName = "हल्दी पाउडर 200g",
            category = "Spices & Salt",
            barcode = "890103000016",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 55.0,
            purchaseCostPerUnit = 45.0,
            currentStock = 25.0,
            minStockAlert = 5.0,
            supplier = "Catch Spices",
            isFavorite = false
        ),
        ProductEntity(
            id = 17L,
            name = "Red Chilli Powder (Lal Mirch 200g)",
            hindiName = "लाल मिर्च पाउडर 200g",
            category = "Spices & Salt",
            barcode = "890103000017",
            unitType = UnitType.PIECE.name,
            sellingPricePerUnit = 65.0,
            purchaseCostPerUnit = 52.0,
            currentStock = 22.0,
            minStockAlert = 5.0,
            supplier = "Catch Spices",
            isFavorite = false
        )
    )

    val sampleCustomers = listOf(
        CustomerEntity(
            id = 1L,
            name = "Ramesh Kumar",
            phone = "9876543210",
            address = "Gali No. 3, Near Temple",
            outstandingBalance = 300.0
        ),
        CustomerEntity(
            id = 2L,
            name = "Suresh Gupta",
            phone = "9812345678",
            address = "Flat 204, Green Heights",
            outstandingBalance = 550.0
        ),
        CustomerEntity(
            id = 3L,
            name = "Sunita Sharma",
            phone = "9988776655",
            address = "Shop 12, Main Bazaar",
            outstandingBalance = 0.0
        )
    )
}
