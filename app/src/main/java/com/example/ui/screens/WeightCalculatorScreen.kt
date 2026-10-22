package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.entity.ProductEntity
import com.example.data.entity.SubUnit
import com.example.data.entity.UnitType
import com.example.domain.calculator.WeightPriceCalculator
import com.example.ui.navigation.Screen
import com.example.ui.theme.GroceryAmberSecondary
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.viewmodel.GroceryViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightCalculatorScreen(
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

    val products by viewModel.activeProducts.collectAsStateWithLifecycle()

    var selectedProduct by remember { mutableStateOf<ProductEntity?>(null) }
    var customProductName by remember { mutableStateOf("Sugar (Cheeni)") }
    var rateInput by remember { mutableStateOf("48") }
    var quantityInput by remember { mutableStateOf("250") }
    var selectedUnitType by remember { mutableStateOf(UnitType.KG) }
    var selectedSubUnit by remember { mutableStateOf(SubUnit.GRAM) }

    // Mode: Calculate Price by Quantity, or Calculate Quantity by Budget
    var isReverseBudgetMode by remember { mutableStateOf(false) }
    var budgetAmountInput by remember { mutableStateOf("20") }

    var snackbarMessage by remember { mutableStateOf<String?>(null) }

    val rate = rateInput.toDoubleOrNull() ?: 0.0
    val quantity = quantityInput.toDoubleOrNull() ?: 0.0
    val budget = budgetAmountInput.toDoubleOrNull() ?: 0.0

    // Live calculated values
    val calculatedPrice = remember(rate, quantity, selectedSubUnit) {
        WeightPriceCalculator.calculatePrice(rate, quantity, selectedSubUnit)
    }

    val calculatedQuantityForBudget = remember(rate, budget, selectedSubUnit) {
        WeightPriceCalculator.calculateQuantityForBudget(budget, rate, selectedSubUnit)
    }

    Scaffold(
        snackbarHost = {
            snackbarMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = { snackbarMessage = null }) { Text("OK") }
                    }
                ) { Text(msg) }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            // Mode Selector Tabs (By Weight vs By Budget)
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isReverseBudgetMode,
                        onClick = { isReverseBudgetMode = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(tr("Calculate Price", "वज़न से भाव (₹)", "ଓଜନରୁ ଦର (₹)"))
                    }
                    SegmentedButton(
                        selected = isReverseBudgetMode,
                        onClick = { isReverseBudgetMode = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(tr("Budget to Qty", "रुपये से वज़न (बजट)", "ଟଙ୍କାରୁ ଓଜନ (ବଜେଟ୍)"))
                    }
                }
            }

            // Quick Product Selector Chips
            item {
                Column {
                    Text(
                        text = tr("Select Store Product (Quick)", "दुकान का सामान चुनें (त्वरित)", "ଦୋକାନ ସାମଗ୍ରୀ ବାଛନ୍ତୁ (ଶୀଘ୍ର)"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(products) { prod ->
                            val isSelected = selectedProduct?.id == prod.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProduct = prod
                                    customProductName = prod.name
                                    rateInput = prod.sellingPricePerUnit.toString()
                                    val uType = UnitType.fromString(prod.unitType)
                                    selectedUnitType = uType
                                    selectedSubUnit = when (uType) {
                                        UnitType.KG -> SubUnit.GRAM
                                        UnitType.LITRE -> SubUnit.ML
                                        UnitType.PIECE -> SubUnit.PIECE
                                    }
                                },
                                label = { Text("${prod.name} (₹${prod.sellingPricePerUnit})") }
                            )
                        }
                    }
                }
            }

            // Rate and Unit Type card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr("Rate & Unit Setting", "दर व इकाई (Rate & Unit)", "ଦର ଓ ଇକାଇ (Rate & Unit)"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = rateInput,
                                onValueChange = { rateInput = it },
                                label = { Text(tr("Rate (₹ per base unit)", "भाव (₹ प्रति इकाई)", "ଦର (₹ ପ୍ରତି ଇକାଇ)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("calculator_rate_input")
                            )

                            // Unit Type selector (kg / litre / piece)
                            var unitMenuExpanded by remember { mutableStateOf(false) }
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = selectedUnitType.displayName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Base Unit") },
                                    trailingIcon = {
                                        IconButton(onClick = { unitMenuExpanded = true }) {
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { unitMenuExpanded = true }
                                )
                                DropdownMenu(
                                    expanded = unitMenuExpanded,
                                    onDismissRequest = { unitMenuExpanded = false }
                                ) {
                                    UnitType.entries.forEach { u ->
                                        DropdownMenuItem(
                                            text = { Text(u.displayName) },
                                            onClick = {
                                                selectedUnitType = u
                                                selectedSubUnit = when (u) {
                                                    UnitType.KG -> SubUnit.GRAM
                                                    UnitType.LITRE -> SubUnit.ML
                                                    UnitType.PIECE -> SubUnit.PIECE
                                                }
                                                unitMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Target SubUnit (e.g. Gram vs Kilogram)
                        Text(
                            text = tr("Target Unit:", "तोलने की इकाई चुनें", "ଓଜନ ଇକାଇ ବାଛନ୍ତୁ:"),
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SubUnit.forUnitType(selectedUnitType).forEach { sub ->
                                FilterChip(
                                    selected = selectedSubUnit == sub,
                                    onClick = { selectedSubUnit = sub },
                                    label = { Text("${if (lang == "or") sub.odiaName else if (lang == "hi") sub.hindiName else sub.displayName} (${sub.symbol})") }
                                )
                            }
                        }
                    }
                }
            }

            // Calculation input & Results Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!isReverseBudgetMode) {
                            // Weight to Price mode
                            Text(
                                text = tr("Enter Quantity / Weight", "मात्रा / वज़न दर्ज करें", "ପରିମାଣ / ଓଜନ ଲେଖନ୍ତୁ"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = quantityInput,
                                onValueChange = { quantityInput = it },
                                label = { Text("Quantity in ${selectedSubUnit.symbol}") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calculator_quantity_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = tr("Total Amount Payable", "कुल रकम (Total Amount)", "ସମୁଦାୟ ପ୍ରଦେୟ ଟଙ୍କା"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = WeightPriceCalculator.formatCurrency(calculatedPrice, settings.currencySymbol),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Formula: (₹$rate / ${selectedUnitType.baseUnit}) × $quantity ${selectedSubUnit.symbol}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        } else {
                            // Budget to Weight mode (e.g. ₹20 of sugar)
                            Text(
                                text = tr("Customer Budget (₹)", "ग्राहक का बजट (रुपये)", "ଗ୍ରାହକଙ୍କ ବଜେଟ୍ (ଟଙ୍କା)"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = budgetAmountInput,
                                onValueChange = { budgetAmountInput = it },
                                label = { Text("Amount in Rupees (₹)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calculator_budget_input")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = tr("Quantity Customer Gets", "ग्राहक को मिलने वाला वज़न", "ଗ୍ରାହକ ପାଇବା ଓଜନ"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = WeightPriceCalculator.formatQuantity(calculatedQuantityForBudget, selectedSubUnit.symbol),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "₹$budget at ₹$rate/${selectedUnitType.baseUnit} = ${calculatedQuantityForBudget} ${selectedSubUnit.symbol}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Add to Bill button
                        Button(
                            onClick = {
                                val targetProd = selectedProduct ?: ProductEntity(
                                    id = 999999L,
                                    name = customProductName.ifBlank { "Custom Grocery Item" },
                                    unitType = selectedUnitType.name,
                                    sellingPricePerUnit = rate
                                )
                                val finalQty = if (!isReverseBudgetMode) quantity else calculatedQuantityForBudget
                                viewModel.addItemToBill(targetProd, finalQty, selectedSubUnit, rate)
                                snackbarMessage = "Added ${finalQty} ${selectedSubUnit.symbol} of ${targetProd.name} to Bill!"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_calculated_item_to_bill_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr("Add to Active Bill", "बिल में जोड़ें (Add to Bill)", "ବିଲ୍‌ରେ ଯୋଡ଼ନ୍ତୁ (Add to Bill)"))
                        }
                    }
                }
            }

            // Quick Presets
            item {
                Text(
                    text = tr("Common Grocery Presets", "लोकप्रिय वजन बटन (Quick Presets)", "ପ୍ରମୁଖ ଓଜନ ବଟନ୍ (Quick Presets)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                val presets = WeightPriceCalculator.getCommonQuantityPresets(selectedUnitType)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.chunked(3).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (label, pair) ->
                                val (qty, subUnit) = pair
                                val itemPrice = WeightPriceCalculator.calculatePrice(rate, qty, subUnit)
                                OutlinedCard(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            quantityInput = if (qty % 1.0 == 0.0) qty.toInt().toString() else qty.toString()
                                            selectedSubUnit = subUnit
                                            isReverseBudgetMode = false
                                        },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "₹${String.format(Locale.getDefault(), "%.2f", itemPrice)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = GroceryGreenPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            // Filler if chunk < 3
                            repeat(3 - rowPresets.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
