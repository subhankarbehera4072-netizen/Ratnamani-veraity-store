package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.SmartGroceryApp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.CustomerWithTransactions
import com.example.data.entity.KhataTransactionEntity
import com.example.domain.calculator.WeightPriceCalculator
import com.example.domain.pdf.ReceiptPdfGenerator
import com.example.ui.components.EmptyStateWidget
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.theme.JamaGreen
import com.example.ui.theme.UdharRed
import com.example.ui.viewmodel.GroceryViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CustomerFilter {
    ALL,
    PENDING_UDHAR,
    SETTLED
}

enum class TransactionFilter {
    ALL,
    UDHAR_ONLY,
    JAMA_ONLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerKhataScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val lang = settings.language

    // Trilingual text helper
    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    val customers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val totalOutstanding by viewModel.totalOutstandingBalance.collectAsStateWithLifecycle()

    // Screen states
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(CustomerFilter.ALL) }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    // Dialog states
    var isAddingNewCustomer by remember { mutableStateOf(false) }
    var customerToEdit by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToDelete by remember { mutableStateOf<CustomerEntity?>(null) }

    // Transaction action dialogs
    var customerForCreditPurchase by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForRepayment by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForBalanceAdjustment by remember { mutableStateOf<CustomerEntity?>(null) }
    var showCustomerPickerForCredit by remember { mutableStateOf(false) }

    // Firebase Auth current user for Firestore sync confirmation
    val currentUser = remember { FirebaseAuth.getInstance().currentUser }

    // Filter customers
    val filteredCustomers = remember(customers, searchQuery, selectedFilter) {
        customers.filter { cust ->
            val matchesQuery = if (searchQuery.isBlank()) true else {
                cust.name.contains(searchQuery, ignoreCase = true) ||
                cust.phone.contains(searchQuery) ||
                cust.address.contains(searchQuery, ignoreCase = true)
            }
            val matchesFilter = when (selectedFilter) {
                CustomerFilter.ALL -> true
                CustomerFilter.PENDING_UDHAR -> cust.outstandingBalance > 0
                CustomerFilter.SETTLED -> cust.outstandingBalance <= 0
            }
            matchesQuery && matchesFilter
        }
    }

    // Keep selectedCustomer updated if customer data changes
    val currentSelectedCustomer = remember(customers, selectedCustomer) {
        selectedCustomer?.let { sel ->
            customers.find { it.id == sel.id } ?: sel
        }
    }

    // Hardware back handler for phone layout when in detail view
    BackHandler(enabled = currentSelectedCustomer != null) {
        selectedCustomer = null
    }

    // ============================================================
    // DIALOGS
    // ============================================================

    // 1. Add / Edit Customer Dialog
    if (isAddingNewCustomer || customerToEdit != null) {
        val editing = customerToEdit
        var name by remember { mutableStateOf(editing?.name ?: "") }
        var phone by remember { mutableStateOf(editing?.phone ?: "") }
        var address by remember { mutableStateOf(editing?.address ?: "") }
        var openingBalance by remember { mutableStateOf(editing?.outstandingBalance?.toString() ?: "0.0") }
        var nameError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {
                isAddingNewCustomer = false
                customerToEdit = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (editing == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (editing == null) tr("Add Customer", "नया ग्राहक जोड़ें", "ନୂଆ ଗ୍ରାହକ ଯୋଡ଼ନ୍ତୁ")
                        else tr("Edit Customer", "ग्राहक सुधारें", "ଗ୍ରାହକ ସଂଶୋଧନ କରନ୍ତୁ")
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            nameError = it.isBlank()
                        },
                        label = { Text(tr("Customer Name *", "ग्राहक का नाम *", "ଗ୍ରାହକଙ୍କ ନାମ *")) },
                        isError = nameError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("customer_name_input")
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(tr("Phone Number", "फ़ोन नंबर", "ଫୋନ୍ ନମ୍ବର")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("customer_phone_input")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(tr("Address / Village / Colony", "पता / गाँव / मोहल्ला", "ଠିକଣା / ଗାଁ / ସାହି")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("customer_address_input")
                    )
                    if (editing == null) {
                        OutlinedTextField(
                            value = openingBalance,
                            onValueChange = { openingBalance = it },
                            label = { Text(tr("Opening Udhar Balance (₹)", "पिछला उधार (₹)", "ପୂର୍ବ ଉଧାର ବାକି (₹)")) },
                            placeholder = { Text("0.0") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("customer_opening_balance_input")
                        )
                    }

                    // Firestore sync badge in dialog
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = GroceryGreenPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tr(
                                "Auto-persisted to Firestore Cloud",
                                "Firestore क्लाउड में सुरक्षित होगा",
                                "Firestore କ୍ଲାଉଡରେ ସେଭ୍ ହେବ"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            nameError = true
                            return@Button
                        }
                        val cust = CustomerEntity(
                            id = editing?.id ?: 0L,
                            name = name.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            outstandingBalance = if (editing == null) (openingBalance.toDoubleOrNull() ?: 0.0) else editing.outstandingBalance
                        )
                        viewModel.saveCustomer(cust) {
                            isAddingNewCustomer = false
                            customerToEdit = null
                            if (editing != null && selectedCustomer?.id == editing.id) {
                                selectedCustomer = cust
                            }
                        }
                    },
                    modifier = Modifier.testTag("save_customer_button")
                ) {
                    Text(tr("Save Customer", "सुरक्षित करें", "ସେଭ୍ କରନ୍ତୁ"))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingNewCustomer = false
                    customerToEdit = null
                }) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // 2. Delete Customer Confirmation Dialog
    customerToDelete?.let { cust ->
        AlertDialog(
            onDismissRequest = { customerToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(tr("Delete Customer?", "खाता हटाएं?", "ଗ୍ରାହକ ଖାତା କାଟିବେ କି?")) },
            text = {
                Text(
                    tr(
                        "Are you sure you want to delete ${cust.name}? All transaction history will also be removed from the device and Firestore.",
                        "क्या आप सचमुच ${cust.name} का खाता हटाना चाहते हैं? सभी लेन-देन इतिहास भी हट जाएगा।",
                        "ଆପଣ ନିଶ୍ଚିତ ଭାବରେ ${cust.name} ଙ୍କ ଖାତା କାଟିବାକୁ ଚାହାଁନ୍ତି କି? ସମସ୍ତ ହିସାବ ଇତିହାସ କ୍ଲାଉଡରୁ ମଧ୍ୟ ହଟିଯିବ।"
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(cust) {
                            if (selectedCustomer?.id == cust.id) {
                                selectedCustomer = null
                            }
                            customerToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_delete_customer_button")
                ) {
                    Text(tr("Delete", "हटाएं", "ଡିଲିଟ୍ କରନ୍ତୁ"))
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToDelete = null }) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // 3. Quick Customer Picker Dialog for "Saman Leke Gaya"
    if (showCustomerPickerForCredit) {
        var pickerQuery by remember { mutableStateOf("") }
        val matchingCustomers = remember(customers, pickerQuery) {
            if (pickerQuery.isBlank()) customers else customers.filter {
                it.name.contains(pickerQuery, ignoreCase = true) || it.phone.contains(pickerQuery)
            }
        }
        AlertDialog(
            onDismissRequest = { showCustomerPickerForCredit = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = UdharRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tr("Select Customer for Udhar", "उधार के लिए ग्राहक चुनें", "ଉଧାର ପାଇଁ ଗ୍ରାହକ ବାଛନ୍ତୁ"))
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = pickerQuery,
                        onValueChange = { pickerQuery = it },
                        placeholder = { Text(tr("Search by name or phone...", "नाम या फोन से खोजें...", "ନାମ କିମ୍ବା ଫୋନ ନମ୍ବର ଖୋଜନ୍ତୁ...")) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("picker_customer_search_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (matchingCustomers.isEmpty()) {
                        Text(
                            tr("No matching customer found.", "कोई ग्राहक नहीं मिला।", "କୌଣସି ଗ୍ରାହକ ମିଳିଲେ ନାହିଁ।"),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(matchingCustomers) { cust ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomer = cust
                                            customerForCreditPurchase = cust
                                            showCustomerPickerForCredit = false
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cust.name, fontWeight = FontWeight.Bold)
                                            if (cust.phone.isNotBlank()) {
                                                Text(cust.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                "₹${cust.outstandingBalance}",
                                                fontWeight = FontWeight.Bold,
                                                color = if (cust.outstandingBalance > 0) UdharRed else JamaGreen
                                            )
                                            Text(
                                                if (cust.outstandingBalance > 0) tr("Udhar", "बाकी", "ବାକି") else tr("Settled", "चुकता", "ଚୁକ୍ତା"),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCustomerPickerForCredit = false }) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // 4. Record Udhar (Credit Purchase) Dialog
    customerForCreditPurchase?.let { cust ->
        var amountStr by remember { mutableStateOf("") }
        var itemDescription by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) customerForCreditPurchase = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = UdharRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tr("Add Udhar (Credit)", "उधार जोड़ें (सामान दिया)", "ଉଧାର ଯୋଡ଼ନ୍ତୁ (ସାମଗ୍ରୀ ଦେଲି)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${tr("Customer", "ग्राहक", "ଗ୍ରାହକ")}: ${cust.name} (${cust.phone.ifBlank { tr("No phone", "बिना फ़ोन", "ଫୋନ୍ ନାହିଁ") }})",
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${tr("Current Balance", "वर्तमान बकाया", "ବର୍ତ୍ତମାନ ବାକି")}: ₹${cust.outstandingBalance}",
                        color = UdharRed,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(tr("Amount (₹) *", "उधार राशि (₹) *", "ଉଧାର ଟଙ୍କା (₹) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("credit_purchase_amount_input")
                    )

                    OutlinedTextField(
                        value = itemDescription,
                        onValueChange = { itemDescription = it },
                        label = { Text(tr("Items / Note (Optional)", "सामान का विवरण / नोट (वैकल्पिक)", "ସାମଗ୍ରୀ ବିବରଣୀ / ନୋଟ୍ (ଇଚ୍ଛାଧୀନ)")) },
                        placeholder = { Text(tr("e.g. 2 packet biscuit, 1kg sugar", "उदा: 2 पैकेट बिस्कुट, 1 किलो चीनी", "ଉଦା: ୨ ପ୍ୟାକେଟ ବିସ୍କୁଟ, ୧ କିଲୋ ଚିନି")) },
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("credit_purchase_description_input")
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GroceryGreenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            tr("Syncs to Firestore & updates customer ledger", "Firestore में सिंक होगा व खाता अपडेट होगा", "Firestore କ୍ଲାଉଡରେ ସିଙ୍କ୍ ହୋଇ ଖାତା ଅପଡେଟ୍ ହେବ"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    errorMsg?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            isSaving = true
                            viewModel.recordCustomerCreditPurchase(
                                customerId = cust.id,
                                amount = amt,
                                itemDescription = itemDescription.trim(),
                                onSuccess = {
                                    isSaving = false
                                    customerForCreditPurchase = null
                                },
                                onError = {
                                    isSaving = false
                                    errorMsg = it
                                }
                            )
                        } else {
                            errorMsg = tr("Please enter a valid amount.", "कृपया सही राशि दर्ज करें।", "ଦୟାକରି ସଠିକ୍ ଟଙ୍କା ଲେଖନ୍ତୁ।")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                    enabled = !isSaving,
                    modifier = Modifier.testTag("confirm_credit_purchase_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(tr("+ Add to Udhar", "+ खाते में जोड़ें", "+ ଉଧାର ଯୋଡ଼ନ୍ତୁ"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { customerForCreditPurchase = null }, enabled = !isSaving) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // 5. Record Jama (Repayment) Dialog
    customerForRepayment?.let { cust ->
        var amountStr by remember { mutableStateOf(if (cust.outstandingBalance > 0) cust.outstandingBalance.toString() else "") }
        var paymentMode by remember { mutableStateOf("CASH") }
        var notes by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) customerForRepayment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Payment, contentDescription = null, tint = JamaGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        tr("Record Jama (Payment)", "जमा दर्ज करें (भुगतान मिला)", "ଜମା ଟଙ୍କା ରେକର୍ଡ କରନ୍ତୁ (ପାଇଲି)"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${tr("Customer", "ग्राहक", "ଗ୍ରାହକ")}: ${cust.name} (${cust.phone.ifBlank { tr("No phone", "बिना फ़ोन", "ଫୋନ୍ ନାହିଁ") }})", fontWeight = FontWeight.Bold)
                    Text(
                        "${tr("Current Outstanding", "वर्तमान बाकी", "ବର୍ତ୍ତମାନ ବାକି")}: ₹${cust.outstandingBalance}",
                        color = UdharRed,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text(tr("Amount Paid (₹) *", "जमा राशि (₹) *", "ଜମା ରାଶି (₹) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repayment_amount_input")
                    )

                    // Payment Mode Chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "CASH" to tr("Cash", "नकद (Cash)", "ନଗଦ (Cash)"),
                            "UPI" to "UPI / Online"
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = paymentMode == mode,
                                onClick = { paymentMode = mode },
                                label = { Text(label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text(tr("Notes / Remarks (Optional)", "टिप्पणी (वैकल्पिक)", "ଟିପ୍ପଣୀ (ଇଚ୍ଛାଧୀନ)")) },
                        placeholder = { Text(tr("e.g. Cleared full balance", "उदा: पूरा हिसाब चुकता", "ଉଦା: ସମ୍ପୂର୍ଣ୍ଣ ବାକି ଚୁକ୍ତା")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("repayment_notes_input")
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = GroceryGreenPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            tr("Syncs to Firestore & updates balance", "Firestore में सिंक होगा", "Firestore କ୍ଲାଉଡରେ ସିଙ୍କ୍ ହେବ"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountStr.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            isSaving = true
                            viewModel.recordCustomerRepayment(
                                customerId = cust.id,
                                amount = amt,
                                mode = paymentMode,
                                notes = notes.trim().ifBlank { "Payment received ($paymentMode)" },
                                onSuccess = {
                                    isSaving = false
                                    customerForRepayment = null
                                },
                                onError = {
                                    isSaving = false
                                    errorMsg = it
                                }
                            )
                        } else {
                            errorMsg = tr("Please enter a valid amount.", "कृपया सही राशि दर्ज करें।", "ଦୟାକରି ସଠିକ୍ ରାଶି ଲେଖନ୍ତୁ।")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                    enabled = !isSaving,
                    modifier = Modifier.testTag("confirm_repayment_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(tr("Record Jama", "जमा सुरक्षित करें", "ଜମା ସେଭ୍ କରନ୍ତୁ"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { customerForRepayment = null }, enabled = !isSaving) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // 6. Direct Balance Adjustment Dialog
    customerForBalanceAdjustment?.let { cust ->
        var newBalanceStr by remember { mutableStateOf(cust.outstandingBalance.toString()) }
        var reason by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }
        var isSaving by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSaving) customerForBalanceAdjustment = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        tr("Adjust Pending Balance", "बाकी राशि संशोधित करें", "ବାକି ଟଙ୍କା ସଂଶୋଧନ କରନ୍ତୁ"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${tr("Customer", "ग्राहक", "ଗ୍ରାହକ")}: ${cust.name}", fontWeight = FontWeight.Bold)
                    Text(
                        "${tr("Current Balance", "वर्तमान बाकी", "ବର୍ତ୍ତମାନ ବାକି")}: ₹${cust.outstandingBalance}",
                        color = UdharRed,
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = newBalanceStr,
                        onValueChange = { newBalanceStr = it },
                        label = { Text(tr("New Total Pending Balance (₹) *", "नई कुल बाकी राशि (₹) *", "ନୂଆ ସମୁଦାୟ ବାକି ଟଙ୍କା (₹) *")) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adjust_balance_amount_input")
                    )

                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(tr("Reason for Adjustment", "संशोधन का कारण", "ସଂଶୋଧନର କାରଣ")) },
                        placeholder = { Text(tr("e.g. Account reconciliation", "उदा: पुराना हिसाब मिलाना", "ଉଦା: ପୂର୍ବ ହିସାବ ସଂଶୋଧନ")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("adjust_balance_reason_input")
                    )

                    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBal = newBalanceStr.toDoubleOrNull()
                        if (newBal != null && newBal >= 0) {
                            isSaving = true
                            viewModel.adjustCustomerBalance(
                                customerId = cust.id,
                                newBalance = newBal,
                                reason = reason.trim(),
                                onSuccess = {
                                    isSaving = false
                                    customerForBalanceAdjustment = null
                                },
                                onError = {
                                    isSaving = false
                                    errorMsg = it
                                }
                            )
                        } else {
                            errorMsg = tr("Please enter a valid balance.", "कृपया सही बाकी राशि दर्ज करें।", "ଦୟାକରି ସଠିକ୍ ବାକି ଟଙ୍କା ଲେଖନ୍ତୁ।")
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.testTag("confirm_adjust_balance_button")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    } else {
                        Text(tr("Update Balance", "बाकी अपडेट करें", "ବାକି ଅପଡେଟ୍ କରନ୍ତୁ"))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { customerForBalanceAdjustment = null }, enabled = !isSaving) {
                    Text(tr("Cancel", "रद्द करें", "ରଦ୍ଦ କରନ୍ତୁ"))
                }
            }
        )
    }

    // ============================================================
    // MAIN SCREEN CONTENT
    // ============================================================
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isTablet = maxWidth >= 600.dp

        if (isTablet) {
            // Tablet Side-by-Side Split View (Master-Detail)
            Row(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // Left 40%: Customer List Pane
                Card(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    CustomerListPane(
                        customers = filteredCustomers,
                        totalOutstanding = totalOutstanding,
                        totalDebtors = customers.count { it.outstandingBalance > 0 },
                        selectedCustomerId = currentSelectedCustomer?.id,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        onSelectCustomer = { selectedCustomer = it },
                        onAddNewCustomer = {
                            customerToEdit = null
                            isAddingNewCustomer = true
                        },
                        onQuickCredit = { showCustomerPickerForCredit = true },
                        currencySymbol = settings.currencySymbol,
                        lang = lang,
                        userEmail = currentUser?.email
                    )
                }

                // Right 58%: Customer Detail & Transaction Ledger Pane
                Card(
                    modifier = Modifier.weight(0.58f).fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    if (currentSelectedCustomer != null) {
                        CustomerLedgerDetailPane(
                            customer = currentSelectedCustomer,
                            viewModel = viewModel,
                            shopName = settings.shopName,
                            currencySymbol = settings.currencySymbol,
                            lang = lang,
                            onClose = { selectedCustomer = null },
                            onAddCredit = { customerForCreditPurchase = currentSelectedCustomer },
                            onAddJama = { customerForRepayment = currentSelectedCustomer },
                            onAdjustBalance = { customerForBalanceAdjustment = currentSelectedCustomer },
                            onEdit = { customerToEdit = currentSelectedCustomer },
                            onDelete = { customerToDelete = currentSelectedCustomer }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                                Icon(
                                    imageVector = Icons.Default.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(72.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = tr("Select a Customer", "ग्राहक चुनें", "ଗ୍ରାହକ ବାଛନ୍ତୁ"),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = tr(
                                        "Select any customer from the list on the left to view their detailed transaction ledger and record Udhar/Jama.",
                                        "बाएं तरफ सूची में से किसी ग्राहक को चुनकर उनका पूरा खाता और लेन-देन देखें।",
                                        "ବାମ ପାର୍ଶ୍ୱ ତାଲିକାରୁ ଯେକୌଣସି ଗ୍ରାହକଙ୍କୁ ବାଛି ତାଙ୍କର ଉଧାର ଖାତା ଓ ହିସାବ ଦେଖନ୍ତୁ।"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Phone Single View (Customer List OR Customer Detail)
            if (currentSelectedCustomer != null) {
                CustomerLedgerDetailPane(
                    customer = currentSelectedCustomer,
                    viewModel = viewModel,
                    shopName = settings.shopName,
                    currencySymbol = settings.currencySymbol,
                    lang = lang,
                    onClose = { selectedCustomer = null },
                    onAddCredit = { customerForCreditPurchase = currentSelectedCustomer },
                    onAddJama = { customerForRepayment = currentSelectedCustomer },
                    onAdjustBalance = { customerForBalanceAdjustment = currentSelectedCustomer },
                    onEdit = { customerToEdit = currentSelectedCustomer },
                    onDelete = { customerToDelete = currentSelectedCustomer }
                )
            } else {
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = {
                                customerToEdit = null
                                isAddingNewCustomer = true
                            },
                            containerColor = GroceryGreenPrimary,
                            contentColor = Color.White,
                            modifier = Modifier.testTag("add_customer_fab")
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Customer")
                        }
                    }
                ) { innerPadding ->
                    CustomerListPane(
                        customers = filteredCustomers,
                        totalOutstanding = totalOutstanding,
                        totalDebtors = customers.count { it.outstandingBalance > 0 },
                        selectedCustomerId = null,
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        selectedFilter = selectedFilter,
                        onFilterChange = { selectedFilter = it },
                        onSelectCustomer = { selectedCustomer = it },
                        onAddNewCustomer = {
                            customerToEdit = null
                            isAddingNewCustomer = true
                        },
                        onQuickCredit = { showCustomerPickerForCredit = true },
                        currencySymbol = settings.currencySymbol,
                        lang = lang,
                        userEmail = currentUser?.email,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

// ============================================================
// COMPONENT 1: CUSTOMER DIRECTORY LIST PANE
// ============================================================
@Composable
fun CustomerListPane(
    customers: List<CustomerEntity>,
    totalOutstanding: Double,
    totalDebtors: Int,
    selectedCustomerId: Long?,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFilter: CustomerFilter,
    onFilterChange: (CustomerFilter) -> Unit,
    onSelectCustomer: (CustomerEntity) -> Unit,
    onAddNewCustomer: () -> Unit,
    onQuickCredit: () -> Unit,
    currencySymbol: String,
    lang: String,
    userEmail: String?,
    modifier: Modifier = Modifier
) {
    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
    ) {
        // 1. Outstanding Banner & Cloud Status
        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("udhar_khata_summary_banner"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = UdharRed)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr("Total Customer Outstanding (Udhar)", "कुल बाज़ार उधारी (मार्केट में बाकी)", "ସମୁଦାୟ ବାକି ଉଧାର (ମାର୍କେଟରେ ବାକି)"),
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = WeightPriceCalculator.formatCurrency(totalOutstanding, currencySymbol),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountBalanceWallet,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$totalDebtors " + tr("customers have pending balance", "ग्राहकों का उधार बाकी है", "ଗ୍ରାହକଙ୍କ ବାକି ରହିଛି"),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )

                        // Firestore Persistence status badge
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (userEmail != null) "Firestore: ${userEmail.substringBefore("@")}" else "Firestore Synced",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.95f),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // 2. Action Bar: Quick Udhar + New Customer
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onQuickCredit,
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                    modifier = Modifier.weight(1f).testTag("quick_saman_leke_gaya_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("+ Give Udhar", "+ सामान दिया (उधार)", "+ ଉଧାର ଦେଲି"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onAddNewCustomer,
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryGreenPrimary),
                    modifier = Modifier.weight(1f).testTag("add_new_customer_quick_button"),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr("+ New Customer", "+ नया ग्राहक", "+ ନୂଆ ଖାତା"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 3. Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text(tr("Search by name, phone or village...", "नाम, फोन या गाँव से खोजें...", "ନାମ, ଫୋନ୍ କିମ୍ବା ଗାଁ ଖୋଜନ୍ତୁ...")) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("search_customer_input")
            )
        }

        // 4. Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == CustomerFilter.ALL,
                    onClick = { onFilterChange(CustomerFilter.ALL) },
                    label = { Text(tr("All", "सभी", "ସମସ୍ତ")) },
                    modifier = Modifier.testTag("filter_all_customers")
                )
                FilterChip(
                    selected = selectedFilter == CustomerFilter.PENDING_UDHAR,
                    onClick = { onFilterChange(CustomerFilter.PENDING_UDHAR) },
                    label = { Text(tr("Pending Udhar", "उधार बाकी", "ଉଧାର ବାକି")) },
                    leadingIcon = {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(UdharRed))
                    },
                    modifier = Modifier.testTag("filter_pending_udhar")
                )
                FilterChip(
                    selected = selectedFilter == CustomerFilter.SETTLED,
                    onClick = { onFilterChange(CustomerFilter.SETTLED) },
                    label = { Text(tr("Settled (₹0)", "हिसाब चुकता", "ହିସାବ ଚୁକ୍ତା")) },
                    leadingIcon = {
                        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(JamaGreen))
                    },
                    modifier = Modifier.testTag("filter_settled_customers")
                )
            }
        }

        // 5. Customer Cards List
        if (customers.isEmpty()) {
            item {
                EmptyStateWidget(
                    icon = Icons.Default.People,
                    title = tr("No Customers Found", "कोई खाता नहीं मिला", "କୌଣସି ଗ୍ରାହକ ଖାତା ମିଳିଲା ନାହିଁ"),
                    subtitle = tr(
                        "Add customers to manage their pending balances and view transaction history.",
                        "ग्राहकों को जोड़कर उनका उधारी खाता और लेन-देन इतिहास देखें।",
                        "ଗ୍ରାହକଙ୍କୁ ଯୋଡ଼ି ସେମାନଙ୍କର ଉଧାର ବାକି ଓ ହିସାବ ଇତିହାସ ରଖନ୍ତୁ।"
                    ),
                    actionButtonText = tr("+ Add Customer", "+ नया ग्राहक जोड़ें", "+ ନୂଆ ଖାତା ଯୋଡ଼ନ୍ତୁ"),
                    onActionClick = onAddNewCustomer
                )
            }
        } else {
            items(customers, key = { it.id }) { cust ->
                val isSelected = selectedCustomerId == cust.id
                val hasUdhar = cust.outstandingBalance > 0

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_card_${cust.id}")
                        .clickable { onSelectCustomer(cust) }
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            else Modifier
                        ),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                         else MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Customer Avatar & Details
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (hasUdhar) UdharRed.copy(alpha = 0.15f)
                                        else JamaGreen.copy(alpha = 0.15f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cust.name.take(1).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = if (hasUdhar) UdharRed else JamaGreen
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = cust.name,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = cust.phone.ifBlank { tr("No phone", "बिना फ़ोन", "ଫୋନ୍ ନାହିଁ") },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (cust.address.isNotBlank()) {
                                    Text(
                                        text = cust.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Outstanding Balance Pill & Indicator
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "₹${cust.outstandingBalance}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (hasUdhar) UdharRed else JamaGreen
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (hasUdhar) UdharRed.copy(alpha = 0.12f) else JamaGreen.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (hasUdhar) tr("Udhar", "बाकी", "ବାକି") else tr("Settled", "चुकता", "ଚୁକ୍ତା"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasUdhar) UdharRed else JamaGreen,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// COMPONENT 2: CUSTOMER DETAILED LEDGER & TRANSACTION HISTORY PANE
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLedgerDetailPane(
    customer: CustomerEntity,
    viewModel: GroceryViewModel,
    shopName: String,
    currencySymbol: String,
    lang: String,
    onClose: () -> Unit,
    onAddCredit: () -> Unit,
    onAddJama: () -> Unit,
    onAdjustBalance: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun tr(en: String, hi: String, od: String): String = when (lang) {
        "or" -> od
        "hi" -> hi
        else -> en
    }

    // Query transactions for this customer from database Flow
    val customerWithTxFlow = remember(customer.id) {
        viewModel.getApplication<SmartGroceryApp>().database.customerDao().getCustomerWithTransactions(customer.id)
    }
    val customerWithTx by customerWithTxFlow.collectAsStateWithLifecycle(initialValue = null)
    val transactions = customerWithTx?.transactions ?: emptyList()

    var txFilter by remember { mutableStateOf(TransactionFilter.ALL) }

    val filteredTransactions = remember(transactions, txFilter) {
        when (txFilter) {
            TransactionFilter.ALL -> transactions
            TransactionFilter.UDHAR_ONLY -> transactions.filter { it.type.contains("UDHAR") }
            TransactionFilter.JAMA_ONLY -> transactions.filter { it.type.contains("JAMA") }
        }
    }

    val totalUdharGiven = remember(transactions) {
        transactions.filter { it.type.contains("UDHAR") }.sumOf { it.amount }
    }
    val totalJamaReceived = remember(transactions) {
        transactions.filter { it.type.contains("JAMA") }.sumOf { it.amount }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("customer_ledger_detail_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Bar: Back, Customer Name & Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("back_to_customer_list_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = customer.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = customer.phone.ifBlank { tr("No phone registered", "बिना फ़ोन", "ଫୋନ୍ ପଞ୍ଜିକୃତ ହୋଇନାହିଁ") },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Edit Customer Button
                    IconButton(onClick = onEdit, modifier = Modifier.testTag("edit_selected_customer_button")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Customer", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Delete Customer Button
                    IconButton(onClick = onDelete, modifier = Modifier.testTag("delete_selected_customer_button")) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Customer", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // 2. Customer Credit Ledger Overview Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (customer.outstandingBalance > 0) UdharRed else JamaGreen
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = tr("Current Pending Balance (Udhar)", "वर्तमान बकाया राशि (उधार)", "ବର୍ତ୍ତମାନ ବାକି ଟଙ୍କା (ଉଧାର)"),
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "₹${customer.outstandingBalance}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = if (customer.outstandingBalance > 0) tr("Payment Due", "बकाया बाकी", "ବାକି ଅଛି") else tr("All Clear", "चुकता", "ସବୁ ଚୁକ୍ତା"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.25f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Metrics Row: Total Udhar vs Total Jama
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = tr("Total Udhar Given", "कुल उधार दिया", "ମୋଟ ଉଧାର ଦିଆ"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "₹$totalUdharGiven",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = tr("Total Jama Received", "कुल जमा मिला", "ମୋଟ ଜମା ପାଇଲି"),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                text = "₹$totalJamaReceived",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Firestore sync badge
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = tr("Synced to Firestore Cloud Database", "Firestore क्लाउड में सिंक व सुरक्षित", "Firestore କ୍ଲାଉଡରେ ସିଙ୍କ୍ ହୋଇ ସୁରକ୍ଷିତ"),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // 3. Primary Khata Actions: + Udhar Diya & + Jama Mila
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Action 1: Add Udhar (Credit)
                Button(
                    onClick = onAddCredit,
                    colors = ButtonDefaults.buttonColors(containerColor = UdharRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("action_saman_diya_button")
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tr("+ Udhar Diya", "+ सामान दिया (उधार)", "+ ଉଧାର ଦେଲି"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Action 2: Record Jama (Payment)
                Button(
                    onClick = onAddJama,
                    colors = ButtonDefaults.buttonColors(containerColor = JamaGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("action_jama_pay_button")
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tr("+ Jama Mila", "+ जमा / भुगतान मिला", "+ ଜମା ପାଇଲି"),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 4. Secondary Actions: Adjust Balance, WhatsApp Reminder & PDF Statement
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Adjust Balance Button
                OutlinedButton(
                    onClick = onAdjustBalance,
                    modifier = Modifier.weight(1f).testTag("action_adjust_balance_button"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Set Balance", "बाकी बदलें", "ବାକି ବଦଳାନ୍ତୁ"), fontSize = 11.sp, maxLines = 1)
                }

                // WhatsApp Reminder
                OutlinedButton(
                    onClick = {
                        val text = when (lang) {
                            "or" -> "ନମସ୍କାର ${customer.name} ଆଜ୍ଞା, ଆପଣଙ୍କର $shopName ରେ ମୋଟ ₹${customer.outstandingBalance} ଟଙ୍କା ଉଧାର ବାକି ରହିଛି। ଦୟାକରି ଶୀଘ୍ର ଜମା କରନ୍ତୁ। ଧନ୍ୟବାଦ!"
                            "hi" -> "नमस्ते ${customer.name} जी, आपका $shopName में कुल ₹${customer.outstandingBalance} का उधारी खाता बाकी है। कृपया समय पर जमा करें। धन्यवाद!"
                            else -> "Dear ${customer.name}, your outstanding balance at $shopName is ₹${customer.outstandingBalance}. Please clear it at your earliest convenience. Thank you!"
                        }
                        val uri = Uri.parse("https://api.whatsapp.com/send?phone=+91${customer.phone}&text=${Uri.encode(text)}")
                        val intent = Intent(Intent.ACTION_VIEW, uri)
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Send Payment Reminder"))
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("action_reminder_button"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("Reminder", "याद दिलाएं", "ମନେ ପକାନ୍ତୁ"), fontSize = 11.sp, maxLines = 1)
                }

                // PDF Statement
                OutlinedButton(
                    onClick = {
                        customerWithTx?.let { cwt ->
                            try {
                                val shopSettings = viewModel.shopSettings.value
                                val pdf = ReceiptPdfGenerator.generateKhataStatementPdf(context, cwt, shopSettings)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdf)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Statement PDF"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("action_pdf_statement_button"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(tr("PDF Bill", "खाता PDF", "ଖାତା PDF"), fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        // 5. Transaction Ledger Header & Filter Chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("Transaction History", "खाता विवरण (लेन-देन इतिहास)", "ହିସାବ ତାଲିକା (ଇତିହାସ)"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${filteredTransactions.size} " + tr("entries", "प्रविष्टियां", "ରେକର୍ଡ"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = txFilter == TransactionFilter.ALL,
                    onClick = { txFilter = TransactionFilter.ALL },
                    label = { Text(tr("All (${transactions.size})", "सभी (${transactions.size})", "ସମସ୍ତ (${transactions.size})")) }
                )
                FilterChip(
                    selected = txFilter == TransactionFilter.UDHAR_ONLY,
                    onClick = { txFilter = TransactionFilter.UDHAR_ONLY },
                    label = { Text(tr("Udhar Only", "उधार (+)", "ଉଧାର (+)")) },
                    leadingIcon = { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(UdharRed)) }
                )
                FilterChip(
                    selected = txFilter == TransactionFilter.JAMA_ONLY,
                    onClick = { txFilter = TransactionFilter.JAMA_ONLY },
                    label = { Text(tr("Jama Only", "जमा (-)", "ଜମା (-)")) },
                    leadingIcon = { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(JamaGreen)) }
                )
            }
        }

        // 6. Chronological Transaction History Cards
        if (filteredTransactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = tr("No transactions recorded yet.", "कोई लेन-देन दर्ज नहीं है।", "ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି ହିସାବ ରେକର୍ଡ ହୋଇନାହିଁ।"),
                            fontWeight = FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr("Use '+ Udhar Diya' or '+ Jama Mila' above to record transactions.", "ऊपर दिए गए बटनों से उधार या जमा दर्ज करें।", "ଉପର ବଟନରୁ ଉଧାର କିମ୍ବା ଜମା ରେକର୍ଡ କରନ୍ତୁ।"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredTransactions, key = { it.id }) { tx ->
                val isDebit = tx.type.contains("UDHAR")
                val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_item_${tx.id}"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDebit) UdharRed.copy(alpha = 0.05f) else JamaGreen.copy(alpha = 0.05f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(
                            if (isDebit) UdharRed.copy(alpha = 0.25f) else JamaGreen.copy(alpha = 0.25f)
                        )
                    )
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            // Left: Type Badge & Description
                            Row(verticalAlignment = Alignment.Top, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(if (isDebit) UdharRed.copy(alpha = 0.15f) else JamaGreen.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isDebit) Icons.Default.ArrowOutward else Icons.Default.ArrowDownward,
                                        contentDescription = null,
                                        tint = if (isDebit) UdharRed else JamaGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column {
                                    Text(
                                        text = if (isDebit) tr("UDHAR (Goods Given)", "उधार (सामान दिया)", "ଉଧାର (ସାମଗ୍ରୀ ଦେଲି)")
                                               else tr("JAMA (Payment Received)", "जमा (भुगतान मिला)", "ଜମା (ଟଙ୍କା ପାଇଲି)"),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDebit) UdharRed else JamaGreen,
                                        fontSize = 13.sp
                                    )

                                    Text(
                                        text = dateFormat.format(Date(tx.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    if (tx.notes.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = tx.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Normal
                                        )
                                    }

                                    if (!isDebit && tx.paymentMode.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = tx.paymentMode,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Right: Amount & Running Balance
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = (if (isDebit) "+₹" else "-₹") + tx.amount,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDebit) UdharRed else JamaGreen,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Bal: ₹${tx.balanceAfter}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Bottom row: Cloud sync indicator
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = GroceryGreenPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Firestore Synced",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
