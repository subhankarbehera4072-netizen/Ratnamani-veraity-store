package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val title: String,
    val hindiTitle: String,
    val odiaTitle: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", "डैशबोर्ड", "ଡ୍ୟାସବୋର୍ଡ", Icons.Default.Dashboard),
    BILLING("Billing", "बिलिंग (POS)", "ବିଲିଂ (POS)", Icons.Default.PointOfSale),
    WEIGHT_CALCULATOR("Calculator", "वज़न कैलकुलेटर", "ଓଜନ କ୍ୟାଲକୁଲେଟର", Icons.Default.Calculate),
    PRODUCTS("Products", "सामान (स्टॉक)", "ସାମଗ୍ରୀ (ଷ୍ଟକ୍)", Icons.Default.Inventory2),
    KHATA("Udhar Khata", "उधार खाता", "ଉଧାର ଖାତା", Icons.Default.People),
    REPORTS("Reports", "बिक्री रिपोर्ट", "ବିକ୍ରି ରିପୋର୍ଟ", Icons.Default.QueryStats),
    ADMIN_PANEL("Admin Panel", "एडमिन पैनल", "ଆଡମିନ୍ ପ୍ୟାନେଲ୍", Icons.Default.Security),
    BACKUP_SETTINGS("Settings & Backup", "सेटिंग्स व बैकअप", "ସେଟିଙ୍ଗ୍ସ ଓ ବ୍ୟାକଅପ୍", Icons.Default.CloudSync);

    fun getLocalizedTitle(lang: String): String = when (lang) {
        "or" -> odiaTitle
        "hi" -> hindiTitle
        else -> title
    }
}
