package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.*
import com.example.domain.calculator.WeightPriceCalculator
import com.example.domain.pdf.ReceiptPdfGenerator
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.EmptyStateWidget
import com.example.ui.navigation.Screen
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.theme.UdharRed
import com.example.ui.viewmodel.GroceryViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillingScreen(
    viewModel: GroceryViewModel,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val lang = settings.language
    val isHindi = lang == "hi"

    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    val products by viewModel.activeProducts.collectAsStateWithLifecycle()
    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val billItems by viewModel.currentBillItems.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val billDiscount by viewModel.billDiscount.collectAsStateWithLifecycle()
    val paymentMode by viewModel.paymentMode.collectAsStateWithLifecycle()
    val amountReceived by viewModel.amountReceived.collectAsStateWithLifecycle()
    val lastCompletedBill by viewModel.lastCompletedBill.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showBarcodeScanner by remember { mutableStateOf(false) }
    var showProductPickerSheet by remember { mutableStateOf(false) }
    var showCustomerPickerSheet by remember { mutableStateOf(false) }
    var showAddWeightDialogForProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var showCompletedReceiptDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(searchQuery, products) {
        if (searchQuery.isBlank()) products else {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.hindiName.contains(searchQuery, ignoreCase = true) ||
                it.barcode?.contains(searchQuery) == true
            }
        }
    }

    val subtotal = remember(billItems) { billItems.sumOf { it.itemTotal } }
    val grandTotal = (subtotal - billDiscount).coerceAtLeast(0.0)
    val changeToReturn = if (paymentMode == "CASH" && amountReceived > grandTotal) amountReceived - grandTotal else 0.0

    // Barcode scanner trigger
    if (showBarcodeScanner) {
        BarcodeScannerDialog(
            onBarcodeScanned = { barcode ->
                showBarcodeScanner = false
                val matched = products.firstOrNull { it.barcode == barcode }
                if (matched != null) {
                    val uType = UnitType.fromString(matched.unitType)
                    val defaultSubUnit = when (uType) {
                        UnitType.KG -> SubUnit.KG
                        UnitType.LITRE -> SubUnit.LITRE
                        UnitType.PIECE -> SubUnit.PIECE
                    }
                    viewModel.addItemToBill(matched, 1.0, defaultSubUnit)
                } else {
                    errorMessage = "No product found with barcode: $barcode"
                }
            },
            onDismiss = { showBarcodeScanner = false }
        )
    }

    // Weight/Quantity Dialog when picking a product
    showAddWeightDialogForProduct?.let { product ->
        val uType = UnitType.fromString(product.unitType)
        var enteredQty by remember { mutableStateOf(if (uType == UnitType.PIECE) "1" else "250") }
        var selectedSubUnit by remember {
            mutableStateOf(if (uType == UnitType.KG) SubUnit.GRAM else if (uType == UnitType.LITRE) SubUnit.ML else SubUnit.PIECE)
        }

        val qtyDouble = enteredQty.toDoubleOrNull() ?: 0.0
        val linePrice = WeightPriceCalculator.calculatePrice(product.sellingPricePerUnit, qtyDouble, selectedSubUnit)

        AlertDialog(
            onDismissRequest = { showAddWeightDialogForProduct = null },
            title = {
                Column {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Rate: ₹${product.sellingPricePerUnit} / ${uType.baseUnit}", style = MaterialTheme.typography.bodySmall)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(if (isHindi) "इकाई चुनें:" else "Select Unit:")
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubUnit.forUnitType(uType).forEach { sub ->
                            FilterChip(
                                selected = selectedSubUnit == sub,
                                onClick = { selectedSubUnit = sub },
                                label = { Text("${sub.hindiName} (${sub.symbol})") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = enteredQty,
                        onValueChange = { enteredQty = it },
                        label = { Text("Quantity (${selectedSubUnit.symbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_quantity_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Calculated Price: ₹${String.format(Locale.getDefault(), "%.2f", linePrice)}",
                        fontWeight = FontWeight.Bold,
                        color = GroceryGreenPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Quick presets
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(WeightPriceCalculator.getCommonQuantityPresets(uType)) { (label, pair) ->
                            SuggestionChip(
                                onClick = {
                                    enteredQty = if (pair.first % 1.0 == 0.0) pair.first.toInt().toString() else pair.first.toString()
                                    selectedSubUnit = pair.second
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (qtyDouble > 0) {
                            viewModel.addItemToBill(product, qtyDouble, selectedSubUnit)
                            showAddWeightDialogForProduct = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_add_product_button")
                ) {
                    Text("Add to Bill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddWeightDialogForProduct = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bill Completed Dialog
    if (showCompletedReceiptDialog && lastCompletedBill != null) {
        val completedBill = lastCompletedBill!!
        AlertDialog(
            onDismissRequest = {
                showCompletedReceiptDialog = false
                viewModel.clearLastCompletedBill()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GroceryGreenPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isHindi) "बिल सुरक्षित हो गया!" else "Bill Completed!")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Bill No: ${completedBill.bill.billNumber}", fontWeight = FontWeight.Bold)
                    Text("Customer: ${completedBill.bill.customerName}")
                    Text("Items: ${completedBill.items.size}")
                    Text(
                        text = "Total: ₹${String.format(Locale.getDefault(), "%.2f", completedBill.bill.grandTotal)}",
                        fontWeight = FontWeight.Bold,
                        color = GroceryGreenPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("Mode: ${completedBill.bill.paymentMode}")
                    if (completedBill.bill.changeReturned > 0) {
                        Text("Change to Return: ₹${completedBill.bill.changeReturned}", fontWeight = FontWeight.SemiBold)
                    }
                    if (completedBill.bill.creditAmount > 0) {
                        Text("Added to Khata: ₹${completedBill.bill.creditAmount}", color = UdharRed, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // Generate and share PDF
                        try {
                            val pdfFile = ReceiptPdfGenerator.generateBillPdf(context, completedBill, settings)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                pdfFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Receipt PDF"))
                        } catch (e: Exception) {
                            errorMessage = "PDF export error: ${e.message}"
                        }
                    },
                    modifier = Modifier.testTag("share_receipt_pdf_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share PDF")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCompletedReceiptDialog = false
                        viewModel.clearLastCompletedBill()
                    },
                    modifier = Modifier.testTag("new_bill_button")
                ) {
                    Text("New Bill")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            // Persistent Bottom Billing Summary & Action Bar
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Items: ${billItems.size} | Subtotal: ₹${String.format(Locale.getDefault(), "%.2f", subtotal)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "Total: ₹${String.format(Locale.getDefault(), "%.2f", grandTotal)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = GroceryGreenPrimary
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.submitBill(
                                    onSuccess = { completed ->
                                        showCompletedReceiptDialog = true
                                    },
                                    onError = { err ->
                                        errorMessage = err
                                    }
                                )
                            },
                            enabled = billItems.isNotEmpty(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("complete_bill_submit_button")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(tr("Complete Bill", "बिल पूरा करें (Save)", "ବିଲ୍ ପୂରା କରନ୍ତୁ"))
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 20.dp)
        ) {
            // Search & Barcode row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isHindi) "सामान खोजें (Search)..." else "Search products...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pos_search_product_input")
                    )

                    // Barcode scan button
                    FilledIconButton(
                        onClick = { showBarcodeScanner = true },
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("open_barcode_scanner_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                    }
                }
            }

            // Quick Product Selection Row (Filtered or Favorites)
            item {
                Column {
                    Text(
                        text = if (searchQuery.isBlank()) (if (isHindi) "लोकप्रिय सामान (टैप करें)" else "Quick Tap Products:") else "Matching Products (${filteredProducts.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(filteredProducts.take(10)) { p ->
                            ElevatedCard(
                                modifier = Modifier
                                    .clickable { showAddWeightDialogForProduct = p }
                                    .testTag("product_chip_${p.id}"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                    Text(
                                        "₹${p.sellingPricePerUnit}/${UnitType.fromString(p.unitType).baseUnit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GroceryGreenPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Customer Selector & Udhar toggle
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (isHindi) "ग्राहक (Customer):" else "Customer Account:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = selectedCustomer?.let { "${it.name} (${it.phone})" } ?: "Walk-in Cash Customer",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (selectedCustomer != null && selectedCustomer!!.outstandingBalance > 0) {
                                Text(
                                    "Pending Udhar: ₹${selectedCustomer!!.outstandingBalance}",
                                    color = UdharRed,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        Row {
                            if (selectedCustomer != null) {
                                TextButton(onClick = { viewModel.selectCustomer(null) }) {
                                    Text("Clear")
                                }
                            }
                            OutlinedButton(
                                onClick = { showCustomerPickerSheet = true },
                                modifier = Modifier.testTag("select_customer_button")
                            ) {
                                Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (selectedCustomer == null) "Select" else "Change")
                            }
                        }
                    }
                }
            }

            // Customer picker dropdown / list if open
            if (showCustomerPickerSheet) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Select Customer / ग्राहक चुनें", fontWeight = FontWeight.Bold)
                                IconButton(onClick = { showCustomerPickerSheet = false }) {
                                    Icon(Icons.Default.Close, contentDescription = null)
                                }
                            }
                            customers.forEach { cust ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.selectCustomer(cust)
                                            showCustomerPickerSheet = false
                                        }
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${cust.name} (${cust.phone})")
                                    if (cust.outstandingBalance > 0) {
                                        Text("₹${cust.outstandingBalance}", color = UdharRed, fontWeight = FontWeight.Bold)
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // Line items header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHindi) "बिल के सामान (${billItems.size})" else "Bill Items (${billItems.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (billItems.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearBill() }) {
                            Text("Clear All", color = UdharRed)
                        }
                    }
                }
            }

            // Empty state if no items
            if (billItems.isEmpty()) {
                item {
                    EmptyStateWidget(
                        icon = Icons.Default.ShoppingCart,
                        title = if (isHindi) "बिल खाली है" else "No Items in Bill",
                        subtitle = if (isHindi) "ऊपर दिए सामान पर टैप करें या बारकोड स्कैन करें" else "Tap a product above or scan barcode to add items",
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                }
            } else {
                // Line items list
                itemsIndexed(billItems) { index, item ->
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.productName,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${item.quantityEntered} ${item.unitEntered} @ ₹${item.ratePerBaseUnit}/${item.unitType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Stepper buttons
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val decrement = if (item.unitEntered == "g" || item.unitEntered == "ml") 50.0 else 1.0
                                        viewModel.updateBillItem(index, item.quantityEntered - decrement)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                                }

                                Text(
                                    text = "${item.quantityEntered} ${item.unitEntered}",
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    fontSize = 13.sp
                                )

                                IconButton(
                                    onClick = {
                                        val increment = if (item.unitEntered == "g" || item.unitEntered == "ml") 50.0 else 1.0
                                        viewModel.updateBillItem(index, item.quantityEntered + increment)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%.2f", item.itemTotal)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = { viewModel.removeBillItem(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = UdharRed, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Payment Options & Discount Card
            if (billItems.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isHindi) "भुगतान का प्रकार (Payment Mode)" else "Payment & Discount",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Payment mode chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "CASH" to "Cash (नकद)",
                                    "UPI" to "UPI (QR)",
                                    "CARD" to "Card",
                                    "UDHAR_CREDIT" to "Udhar (उधार)"
                                ).forEach { (modeKey, modeLabel) ->
                                    FilterChip(
                                        selected = paymentMode == modeKey,
                                        onClick = { viewModel.setPaymentMode(modeKey) },
                                        label = { Text(modeLabel, fontSize = 12.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Cash received calculator
                            if (paymentMode == "CASH") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = if (amountReceived > 0) amountReceived.toString() else "",
                                        onValueChange = { viewModel.setAmountReceived(it.toDoubleOrNull() ?: 0.0) },
                                        label = { Text("Cash Received (₹)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("cash_received_input")
                                    )
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text("Change to Return:", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            text = "₹${String.format(Locale.getDefault(), "%.2f", changeToReturn)}",
                                            fontWeight = FontWeight.Bold,
                                            color = GroceryGreenPrimary,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }
                                }

                                // Quick cash buttons
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(100.0, 200.0, 500.0, 1000.0).forEach { note ->
                                        SuggestionChip(
                                            onClick = { viewModel.setAmountReceived(note) },
                                            label = { Text("₹${note.toInt()}") }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Discount field
                            OutlinedTextField(
                                value = if (billDiscount > 0) billDiscount.toString() else "",
                                onValueChange = { viewModel.setBillDiscount(it.toDoubleOrNull() ?: 0.0) },
                                label = { Text("Bill Discount (₹ छूट)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bill_discount_input")
                            )
                        }
                    }
                }
            }

            // Error display
            errorMessage?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
