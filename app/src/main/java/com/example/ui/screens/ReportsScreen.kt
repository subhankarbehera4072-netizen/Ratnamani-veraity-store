package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ExpenseEntity
import com.example.domain.backup.BackupManager
import com.example.domain.calculator.WeightPriceCalculator
import com.example.ui.components.StatMetricCard
import com.example.ui.theme.GroceryAmberSecondary
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.theme.JamaGreen
import com.example.ui.theme.UdharRed
import com.example.ui.viewmodel.GroceryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val lang = settings.language
    val isHindi = lang == "hi"

    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    val allBills by viewModel.allBills.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()

    var selectedRangeIndex by remember { mutableStateOf(0) } // 0: Today, 1: This Week, 2: This Month, 3: All Time
    var showAddExpenseDialog by remember { mutableStateOf(false) }

    val rangeTimestamps = remember(selectedRangeIndex) {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        when (selectedRangeIndex) {
            0 -> {
                // Today
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                cal.timeInMillis to now
            }
            1 -> {
                // This Week (last 7 days)
                cal.add(Calendar.DAY_OF_YEAR, -7)
                cal.timeInMillis to now
            }
            2 -> {
                // This Month
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis to now
            }
            else -> 0L to Long.MAX_VALUE
        }
    }

    val filteredBills = remember(allBills, rangeTimestamps) {
        allBills.filter { it.bill.timestamp in rangeTimestamps.first..rangeTimestamps.second }
    }

    val filteredExpenses = remember(allExpenses, rangeTimestamps) {
        allExpenses.filter { it.timestamp in rangeTimestamps.first..rangeTimestamps.second }
    }

    // Calculations
    val totalRevenue = remember(filteredBills) { filteredBills.sumOf { it.bill.grandTotal } }
    val totalCogs = remember(filteredBills) {
        filteredBills.sumOf { billWithItems ->
            billWithItems.items.sumOf { it.quantityInBaseUnit * it.purchaseCostPerBaseUnit }
        }
    }
    val grossProfit = remember(totalRevenue, totalCogs) {
        if (totalCogs > 0) (totalRevenue - totalCogs).coerceAtLeast(0.0) else (totalRevenue * 0.15)
    }
    val totalExpenses = remember(filteredExpenses) { filteredExpenses.sumOf { it.amount } }
    val netProfit = (grossProfit - totalExpenses).coerceAtLeast(0.0)

    // Payment methods summary
    val cashSales = remember(filteredBills) { filteredBills.filter { it.bill.paymentMode == "CASH" }.sumOf { it.bill.grandTotal } }
    val upiSales = remember(filteredBills) { filteredBills.filter { it.bill.paymentMode == "UPI" }.sumOf { it.bill.grandTotal } }
    val udharSales = remember(filteredBills) { filteredBills.sumOf { it.bill.creditAmount } }

    // Top selling products
    val topProducts = remember(filteredBills) {
        filteredBills.flatMap { it.items }
            .groupBy { it.productName }
            .map { (name, items) ->
                val totalQty = items.sumOf { it.quantityEntered }
                val totalRev = items.sumOf { it.itemTotal }
                val unit = items.firstOrNull()?.unitEntered ?: ""
                Triple(name, "$totalQty $unit", totalRev)
            }
            .sortedByDescending { it.third }
            .take(5)
    }

    // Add Expense Dialog
    if (showAddExpenseDialog) {
        var category by remember { mutableStateOf("TEA_SNACKS") }
        var amountStr by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddExpenseDialog = false },
            title = { Text("Add Shop Expense / दुकान खर्च") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Category / खर्च प्रकार:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "TEA_SNACKS" to "Tea & Snacks",
                            "STORE_RENT" to "Rent",
                            "ELECTRICITY" to "Bijli",
                            "TRANSPORT" to "Auto/Fare",
                            "OTHER" to "Other"
                        ).forEach { (catKey, catLabel) ->
                            FilterChip(
                                selected = category == catKey,
                                onClick = { category = catKey },
                                label = { Text(catLabel, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹ राशि) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_amount_input")
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / विवरण") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("expense_description_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            viewModel.addExpense(category, amt, description) {
                                showAddExpenseDialog = false
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_expense_button")
                ) {
                    Text("Save Expense")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddExpenseDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddExpenseDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Expense") },
                containerColor = Color(0xFFE65100),
                contentColor = Color.White,
                modifier = Modifier.testTag("add_expense_fab")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
        ) {
            // Header & CSV export button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Financial & Sales Reports", "वित्तीय रिपोर्ट व मुनाफा", "ଆର୍ଥିକ ରିପୋର୍ଟ ଓ ଲାଭ"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val csv = BackupManager.exportSalesCsv(context, viewModel.getApplication<com.example.SmartGroceryApp>().database)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", csv)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Sales CSV"))
                            }
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export CSV", fontSize = 12.sp)
                    }
                }
            }

            // Date Range Tabs
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("Today", "Week", "Month", "All Time").forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedRangeIndex == index,
                            onClick = { selectedRangeIndex = index },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = 4)
                        ) {
                            Text(label, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Primary Profit & Revenue Dashboard Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = tr("Gross Total Revenue", "कुल बिक्री (Total Revenue)", "ସମୁଦାୟ ବିକ୍ରି (Total Revenue)"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = WeightPriceCalculator.formatCurrency(totalRevenue, settings.currencySymbol),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${filteredBills.size} bills generated in this period",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Gross Profit:", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = WeightPriceCalculator.formatCurrency(grossProfit, settings.currencySymbol),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFE082),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Cost: ₹${String.format(Locale.getDefault(), "%.1f", totalCogs)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net Profit (शुद्ध लाभ):", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = WeightPriceCalculator.formatCurrency(netProfit, settings.currencySymbol),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFA5D6A7),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text("Expenses: -₹${String.format(Locale.getDefault(), "%.1f", totalExpenses)}", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            // Payment Mode Breakdown
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr("Payment Breakdown", "भुगतान माध्यम सारांश", "ପେମେଣ୍ଟ ମାଧ୍ୟମ ବିବରଣୀ"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("💵 Cash Received", style = MaterialTheme.typography.labelSmall)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", cashSales)}", fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("📱 UPI Payments", style = MaterialTheme.typography.labelSmall)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", upiSales)}", fontWeight = FontWeight.Bold, color = GroceryGreenPrimary)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("📋 Credit / Udhar", style = MaterialTheme.typography.labelSmall)
                                Text("₹${String.format(Locale.getDefault(), "%.2f", udharSales)}", fontWeight = FontWeight.Bold, color = UdharRed)
                            }
                        }
                    }
                }
            }

            // Top Selling Products Section
            item {
                Text(
                    text = tr("Top Selling Products", "सबसे ज्यादा बिकने वाला सामान", "ସର୍ବାଧିକ ବିକ୍ରି ହୋଇଥିବା ସାମଗ୍ରୀ"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (topProducts.isEmpty()) {
                item {
                    Text("No product sales recorded in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(topProducts) { (prodName, qtyStr, rev) ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(prodName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Quantity Sold: $qtyStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%.2f", rev)}",
                                fontWeight = FontWeight.Bold,
                                color = GroceryGreenPrimary
                            )
                        }
                    }
                }
            }

            // Shop Operating Expenses Ledger
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr("Recorded Expenses (${filteredExpenses.size})", "दुकान खर्च विवरण (${filteredExpenses.size})", "ଦୋକାନ ଖର୍ଚ୍ଚ ବିବରଣୀ (${filteredExpenses.size})"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total: ₹${String.format(Locale.getDefault(), "%.2f", totalExpenses)}",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                }
            }

            if (filteredExpenses.isEmpty()) {
                item {
                    Text("No shop expenses recorded in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(filteredExpenses) { exp ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.category.replace("_", " "), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (exp.description.isNotBlank()) Text(exp.description, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(exp.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("₹${exp.amount}", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                IconButton(onClick = { viewModel.deleteExpense(exp.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = UdharRed, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
