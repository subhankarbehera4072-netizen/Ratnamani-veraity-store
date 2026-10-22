package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.calculator.WeightPriceCalculator
import com.example.ui.components.StatMetricCard
import com.example.ui.navigation.Screen
import com.example.ui.theme.*
import com.example.ui.viewmodel.GroceryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: GroceryViewModel,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val lang = settings.language
    val isHindi = lang == "hi"

    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    val todaySales by viewModel.todaySalesTotal.collectAsStateWithLifecycle()
    val todayBillsCount by viewModel.todayBillsCount.collectAsStateWithLifecycle()
    val totalProducts by viewModel.totalProductCount.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val totalOutstanding by viewModel.totalOutstandingBalance.collectAsStateWithLifecycle()
    val todayExpenses by viewModel.todayExpensesTotal.collectAsStateWithLifecycle()
    val allBills by viewModel.allBills.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    // Estimated today's profit: calculate from today's bills items cost price
    val todayBills = remember(allBills) {
        val todayStart = CalendarStartOfDay()
        allBills.filter { it.bill.timestamp >= todayStart }
    }
    val todayGrossProfit = remember(todayBills) {
        todayBills.sumOf { billWithItems ->
            val billRevenue = billWithItems.bill.grandTotal
            val billCost = billWithItems.items.sumOf { item ->
                item.quantityInBaseUnit * item.purchaseCostPerBaseUnit
            }
            if (billCost > 0) (billRevenue - billCost).coerceAtLeast(0.0) else (billRevenue * 0.15)
        }
    }
    val todayNetProfit = (todayGrossProfit - todayExpenses).coerceAtLeast(0.0)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
    ) {
        // Hero Store Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hero_banner_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.shopName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            if (settings.tagline.isNotBlank()) {
                                Text(
                                    text = settings.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date()),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = tr("Today's Sales", "आज की कुल बिक्री", "ଆଜିର ମୋଟ ବିକ୍ରି"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = WeightPriceCalculator.formatCurrency(todaySales, settings.currencySymbol),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("Est. Net Profit", "अनुमानित मुनाफा", "ଆନୁମାନିକ ଲାଭ"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = WeightPriceCalculator.formatCurrency(todayNetProfit, settings.currencySymbol),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFE082)
                            )
                        }
                    }
                }
            }
        }

        // Key Metrics 2x3 Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = tr("Today's Bills", "आज के बिल", "ଆଜିର ବିଲ୍ ସଂଖ୍ୟା"),
                        value = "$todayBillsCount",
                        subtitle = if (todayBillsCount > 0) "Avg: ₹${String.format(Locale.getDefault(), "%.1f", todaySales / todayBillsCount)}" else tr("No sales yet", "अभी बिक्री नहीं हुई", "ଏଯାଏଁ ବିକ୍ରି ହୋଇନାହିଁ"),
                        icon = Icons.Default.ReceiptLong,
                        iconTint = GroceryGreenPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.REPORTS) }
                    )
                    StatMetricCard(
                        title = tr("Low Stock Items", "कम स्टॉक वाले", "କମ୍ ଷ୍ଟକ୍ ସାମଗ୍ରୀ"),
                        value = "$lowStockCount",
                        subtitle = if (lowStockCount > 0) tr("Restock required", "स्टॉक लाना जरूरी", "ଷ୍ଟକ୍ ଆଣିବା ଜରୁରୀ") else tr("Stock healthy", "स्टॉक पर्याप्त", "ଷ୍ଟକ୍ ଠିକ୍ ଅଛି"),
                        icon = Icons.Default.WarningAmber,
                        iconTint = if (lowStockCount > 0) UdharRed else JamaGreen,
                        containerColor = if (lowStockCount > 0) UdharRedContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.PRODUCTS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatMetricCard(
                        title = tr("Customer Udhar", "ग्राहक उधारी खाता", "ଗ୍ରାହକ ଉଧାର ଖାତା"),
                        value = WeightPriceCalculator.formatCurrency(totalOutstanding, settings.currencySymbol),
                        subtitle = tr("Total pending", "बाकी रकम", "ସମୁଦାୟ ବାକି"),
                        icon = Icons.Default.AccountBalanceWallet,
                        iconTint = UdharRed,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.KHATA) }
                    )
                    StatMetricCard(
                        title = tr("Today's Expenses", "आज के खर्चे", "ଆଜିର ଖର୍ଚ୍ଚ"),
                        value = WeightPriceCalculator.formatCurrency(todayExpenses, settings.currencySymbol),
                        subtitle = tr("Shop operating", "दुकान खर्च", "ଦୋକାନ ଖର୍ଚ୍ଚ"),
                        icon = Icons.Default.MoneyOff,
                        iconTint = Color(0xFFE65100),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigate(Screen.REPORTS) }
                    )
                }
            }
        }

        // Quick Actions Section Title
        item {
            Text(
                text = tr("Quick Actions", "त्वरित कार्य / मुख्य मेनू", "ମୁଖ୍ୟ କାର୍ଯ୍ୟ ସୂଚୀ"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Quick Action Buttons
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        title = tr("Quick Billing", "नई बिलिंग (POS)", "ନୂଆ ବିଲିଂ (POS)"),
                        subtitle = tr("Create new bill", "नया बिल बनाएं", "ନୂଆ ବିଲ୍ କରନ୍ତୁ"),
                        icon = Icons.Default.PointOfSale,
                        color = GroceryGreenPrimary,
                        modifier = Modifier.weight(1f),
                        testTag = "quick_billing_action_tile",
                        onClick = { onNavigate(Screen.BILLING) }
                    )
                    ActionTile(
                        title = tr("Weight Calculator", "वज़न कैलकुलेटर", "ଓଜନ କ୍ୟାଲକୁଲେଟର"),
                        subtitle = tr("Price per g/kg/L", "ग्राम/किलो का भाव", "ଗ୍ରାମ/କିଲୋ ଦର ହିସାବ"),
                        icon = Icons.Default.Calculate,
                        color = Color(0xFF00796B),
                        modifier = Modifier.weight(1f),
                        testTag = "weight_calculator_action_tile",
                        onClick = { onNavigate(Screen.WEIGHT_CALCULATOR) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        title = tr("Products & Stock", "सामान व स्टॉक", "ସାମଗ୍ରୀ ଓ ଷ୍ଟକ୍"),
                        subtitle = "$totalProducts " + tr("products", "सामान उपलब्ध", "ସାମଗ୍ରୀ"),
                        icon = Icons.Default.Inventory2,
                        color = Color(0xFF3949AB),
                        modifier = Modifier.weight(1f),
                        testTag = "products_action_tile",
                        onClick = { onNavigate(Screen.PRODUCTS) }
                    )
                    ActionTile(
                        title = tr("Customer Khata", "उधारी बही खाता", "ଉଧାର ବହି ଖାତା"),
                        subtitle = tr("Customer credit ledger", "उधार व जमा हिसाब", "ବାକି ଓ ଜମା ହିସାବ"),
                        icon = Icons.Default.People,
                        color = Color(0xFFD84315),
                        modifier = Modifier.weight(1f),
                        testTag = "customer_khata_action_tile",
                        onClick = { onNavigate(Screen.KHATA) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        title = tr("Sales Reports", "बिक्री व मुनाफा रिपोर्ट", "ବିକ୍ରି ଓ ଲାଭ ରିପୋର୍ଟ"),
                        subtitle = tr("Daily & monthly profit", "दैनिक / मासिक लाभ", "ଦୈନିକ ଓ ମାସିକ ଲାଭ"),
                        icon = Icons.Default.QueryStats,
                        color = Color(0xFF6A1B9A),
                        modifier = Modifier.weight(1f),
                        testTag = "sales_reports_action_tile",
                        onClick = { onNavigate(Screen.REPORTS) }
                    )
                    ActionTile(
                        title = tr("Cloud Backup", "गूगल ड्राइव बैकअप", "ଗୁଗୁଲ୍ ଡ୍ରାଇଭ୍ ବ୍ୟାକଅପ୍"),
                        subtitle = tr("Drive sync & restore", "सुरक्षित डेटा बैकअप", "ସୁରକ୍ଷିତ ଡାଟା ବ୍ୟାକଅପ୍"),
                        icon = Icons.Default.CloudSync,
                        color = Color(0xFF00838F),
                        modifier = Modifier.weight(1f),
                        testTag = "backup_restore_action_tile",
                        onClick = { onNavigate(Screen.BACKUP_SETTINGS) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ActionTile(
                        title = tr("Admin & Multi-Device", "एडमिन व अन्य फोन सिंक", "ଆଡମିନ୍ ଓ ଅନ୍ୟ ଫୋନ୍ ସିଙ୍କ୍"),
                        subtitle = tr("Stock, Udhar, Cloud PIN", "स्टॉक, उधार व डेटा सुरक्षित", "ଷ୍ଟକ୍, ଉଧାର ଓ ଡାଟା ସୁରକ୍ଷିତ"),
                        icon = Icons.Default.Security,
                        color = Color(0xFFC2185B),
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "admin_panel_action_tile",
                        onClick = { onNavigate(Screen.ADMIN_PANEL) }
                    )
                }
            }
        }

        // Low stock alerts list if any
        if (lowStockProducts.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Low Stock Alerts (${lowStockProducts.size})", "कम स्टॉक अलर्ट (${lowStockProducts.size})", "କମ୍ ଷ୍ଟକ୍ ସତର୍କତା (${lowStockProducts.size})"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = UdharRed
                    )
                    TextButton(onClick = { onNavigate(Screen.PRODUCTS) }) {
                        Text(tr("View All", "सामान देखें", "ସବୁ ଦେଖନ୍ତୁ"))
                    }
                }
            }

            items(lowStockProducts.take(3)) { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = UdharRedContainer.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = product.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Current: ${product.currentStock} ${product.unitType} (Min alert: ${product.minStockAlert})",
                                style = MaterialTheme.typography.bodySmall,
                                color = UdharRed
                            )
                        }
                        Button(
                            onClick = { onNavigate(Screen.PRODUCTS) },
                            colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Restock", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    testTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun CalendarStartOfDay(): Long {
    val cal = java.util.Calendar.getInstance()
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
