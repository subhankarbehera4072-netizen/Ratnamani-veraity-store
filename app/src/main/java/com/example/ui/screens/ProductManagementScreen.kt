package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.entity.ProductEntity
import com.example.data.entity.UnitType
import com.example.domain.backup.BackupManager
import com.example.ui.components.BarcodeScannerDialog
import com.example.ui.components.EmptyStateWidget
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.theme.UdharRed
import com.example.ui.viewmodel.GroceryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagementScreen(
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

    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }
    var showOnlyLowStock by remember { mutableStateOf(false) }

    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productToRestock by remember { mutableStateOf<ProductEntity?>(null) }

    var showBarcodeScannerForProduct by remember { mutableStateOf(false) }
    var scannedBarcodeDestination by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val categories = remember(allProducts) {
        listOf("All") + allProducts.map { it.category }.distinct().filter { it.isNotBlank() }
    }

    val displayedProducts = remember(allProducts, searchQuery, selectedCategoryFilter, showOnlyLowStock) {
        allProducts.filter { p ->
            val matchesQuery = searchQuery.isBlank() ||
                    p.name.contains(searchQuery, ignoreCase = true) ||
                    p.hindiName.contains(searchQuery, ignoreCase = true) ||
                    p.barcode?.contains(searchQuery) == true
            val matchesCategory = selectedCategoryFilter == null || selectedCategoryFilter == "All" || p.category == selectedCategoryFilter
            val matchesStock = !showOnlyLowStock || p.currentStock <= p.minStockAlert
            matchesQuery && matchesCategory && matchesStock
        }
    }

    // Barcode scanner dialog
    if (showBarcodeScannerForProduct && scannedBarcodeDestination != null) {
        BarcodeScannerDialog(
            onBarcodeScanned = { barcode ->
                showBarcodeScannerForProduct = false
                scannedBarcodeDestination?.invoke(barcode)
            },
            onDismiss = { showBarcodeScannerForProduct = false }
        )
    }

    // Add / Edit Product Dialog
    if (isCreatingNew || productToEdit != null) {
        val editing = productToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var hindiName by remember { mutableStateOf(editing?.hindiName ?: "") }
        var category by remember { mutableStateOf(editing?.category ?: "Grains & Pulses") }
        var barcode by remember { mutableStateOf(editing?.barcode ?: "") }
        var unitType by remember { mutableStateOf(editing?.unitType ?: UnitType.KG.name) }
        var sellingPrice by remember { mutableStateOf(editing?.sellingPricePerUnit?.toString() ?: "0.0") }
        var costPrice by remember { mutableStateOf(editing?.purchaseCostPerUnit?.toString() ?: "0.0") }
        var currentStock by remember { mutableStateOf(editing?.currentStock?.toString() ?: "0.0") }
        var minStockAlert by remember { mutableStateOf(editing?.minStockAlert?.toString() ?: "5.0") }
        var supplier by remember { mutableStateOf(editing?.supplier ?: "") }
        var isFavorite by remember { mutableStateOf(editing?.isFavorite ?: false) }

        AlertDialog(
            onDismissRequest = {
                isCreatingNew = false
                productToEdit = null
            },
            title = {
                Text(if (editing == null) "Add New Product / नया सामान" else "Edit Product / सामान सुधारें")
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Product Name (English) *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("product_name_input")
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = hindiName,
                            onValueChange = { hindiName = it },
                            label = { Text("Hindi Name (हिंदी नाम)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("product_hindi_name_input")
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category (श्रेणी)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text("Barcode") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("product_barcode_input")
                            )
                            IconButton(onClick = {
                                scannedBarcodeDestination = { code -> barcode = code }
                                showBarcodeScannerForProduct = true
                            }) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan")
                            }
                        }
                    }
                    item {
                        // Unit Type selector
                        Text("Base Unit Type:", style = MaterialTheme.typography.labelSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            UnitType.entries.forEach { u ->
                                FilterChip(
                                    selected = unitType == u.name,
                                    onClick = { unitType = u.name },
                                    label = { Text(u.displayName) }
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = sellingPrice,
                                onValueChange = { sellingPrice = it },
                                label = { Text("Selling Rate (₹) *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("product_selling_price_input")
                            )
                            OutlinedTextField(
                                value = costPrice,
                                onValueChange = { costPrice = it },
                                label = { Text("Purchase Cost (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("product_cost_price_input")
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = currentStock,
                                onValueChange = { currentStock = it },
                                label = { Text("Current Stock") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f).testTag("product_current_stock_input")
                            )
                            OutlinedTextField(
                                value = minStockAlert,
                                onValueChange = { minStockAlert = it },
                                label = { Text("Low Alert Qty") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = supplier,
                            onValueChange = { supplier = it },
                            label = { Text("Supplier Info") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                            Text("Mark as Favorite (Shows first on POS billing)")
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val newProduct = ProductEntity(
                                id = editing?.id ?: 0L,
                                name = name.trim(),
                                hindiName = hindiName.trim(),
                                category = category.trim().ifBlank { "General" },
                                barcode = barcode.trim().ifBlank { null },
                                unitType = unitType,
                                sellingPricePerUnit = sellingPrice.toDoubleOrNull() ?: 0.0,
                                purchaseCostPerUnit = costPrice.toDoubleOrNull() ?: 0.0,
                                currentStock = currentStock.toDoubleOrNull() ?: 0.0,
                                minStockAlert = minStockAlert.toDoubleOrNull() ?: 5.0,
                                supplier = supplier.trim(),
                                isFavorite = isFavorite
                            )
                            viewModel.saveProduct(newProduct) {
                                isCreatingNew = false
                                productToEdit = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_product_button")
                ) {
                    Text("Save Product")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isCreatingNew = false
                    productToEdit = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restock Dialog
    productToRestock?.let { product ->
        var addedQty by remember { mutableStateOf("10") }
        var costRate by remember { mutableStateOf(product.purchaseCostPerUnit.toString()) }
        var supplierNote by remember { mutableStateOf("Restock from wholesaler") }

        AlertDialog(
            onDismissRequest = { productToRestock = null },
            title = { Text("Restock / नया माल जोड़ें") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${product.name} (Current: ${product.currentStock} ${product.unitType})", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = addedQty,
                        onValueChange = { addedQty = it },
                        label = { Text("Quantity to Add (${product.unitType})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("restock_quantity_input")
                    )
                    OutlinedTextField(
                        value = costRate,
                        onValueChange = { costRate = it },
                        label = { Text("New Purchase Cost (₹ / unit)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = supplierNote,
                        onValueChange = { supplierNote = it },
                        label = { Text("Notes / Wholesaler Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = addedQty.toDoubleOrNull() ?: 0.0
                        val cost = costRate.toDoubleOrNull() ?: 0.0
                        if (qty > 0) {
                            viewModel.restockProduct(product.id, qty, cost, supplierNote) {
                                productToRestock = null
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm_restock_button")
                ) {
                    Text("Update Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToRestock = null }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    productToDelete?.let { product ->
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product / सामान हटाएं?") },
            text = { Text("Are you sure you want to delete '${product.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(product) { productToDelete = null }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                    modifier = Modifier.testTag("confirm_delete_product_button")
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    productToEdit = null
                    isCreatingNew = true
                },
                containerColor = GroceryGreenPrimary,
                contentColor = Color.White,
                modifier = Modifier.testTag("add_product_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
        ) {
            // Header & CSV export button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tr("Product & Stock Admin", "सामान व स्टॉक प्रबंधन", "ସାମଗ୍ରୀ ଓ ଷ୍ଟକ୍ ପରିଚାଳନା"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${allProducts.size} items | Low Stock: ${lowStockProducts.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val csv = BackupManager.exportProductsCsv(context, viewModel.getApplication<com.example.SmartGroceryApp>().database)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", csv)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export Products CSV"))
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

            // Search bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search products by name or barcode...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_search_product_input")
                )
            }

            // Category filter chips
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = showOnlyLowStock,
                            onClick = { showOnlyLowStock = !showOnlyLowStock },
                            label = { Text("⚠️ Low Stock Only (${lowStockProducts.size})") },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = UdharRed.copy(alpha = 0.2f))
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategoryFilter == cat || (selectedCategoryFilter == null && cat == "All"),
                            onClick = { selectedCategoryFilter = if (cat == "All") null else cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Products list
            if (displayedProducts.isEmpty()) {
                item {
                    EmptyStateWidget(
                        icon = Icons.Default.Inventory2,
                        title = "No Products Found",
                        subtitle = "Try adjusting your search or add a new product using the + button.",
                        actionButtonText = "Add New Product",
                        onActionClick = { isCreatingNew = true }
                    )
                }
            } else {
                items(displayedProducts) { product ->
                    val isLowStock = product.currentStock <= product.minStockAlert
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("product_admin_card_${product.id}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLowStock) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = product.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        if (product.isFavorite) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = "Favorite",
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (product.hindiName.isNotBlank()) {
                                        Text(
                                            text = product.hindiName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${product.category} | ${product.unitType} | Barcode: ${product.barcode ?: "None"}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "₹${product.sellingPricePerUnit}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = GroceryGreenPrimary
                                    )
                                    Text(
                                        text = "Cost: ₹${product.purchaseCostPerUnit}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Stock: ${product.currentStock} ${UnitType.fromString(product.unitType).baseUnit}",
                                        fontWeight = FontWeight.Bold,
                                        color = if (isLowStock) UdharRed else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (isLowStock) {
                                        Text("⚠️ Low Stock Alert!", color = UdharRed, style = MaterialTheme.typography.labelSmall)
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilledTonalButton(
                                        onClick = { productToRestock = product },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Restock", fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = { productToEdit = product },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                                    }

                                    IconButton(
                                        onClick = { productToDelete = product },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = UdharRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
