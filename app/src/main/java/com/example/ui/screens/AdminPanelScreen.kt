package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.viewmodel.GroceryViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    viewModel: GroceryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val settings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val totalOutstanding by viewModel.totalOutstandingBalance.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()

    val lang = settings.language

    // Firebase Auth user state
    val auth = remember { FirebaseAuth.getInstance() }
    var currentUser by remember { mutableStateOf(auth.currentUser) }
    var isSigningIn by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var syncStatusMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { fbAuth ->
            currentUser = fbAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val ownerEmailTarget = "subhankarbehera4072@gmail.com"
    val isOwner = currentUser != null && (
        currentUser?.email?.equals(ownerEmailTarget, ignoreCase = true) == true ||
        currentUser?.email?.equals(settings.googleAccountEmail, ignoreCase = true) == true
    )

    // Calculate metrics
    val totalStockUnits = allProducts.sumOf { it.currentStock }
    val totalStockSellingValue = allProducts.sumOf { it.currentStock * it.sellingPricePerUnit }
    val totalStockCostValue = allProducts.sumOf { it.currentStock * it.purchaseCostPerUnit }
    val debtorsCount = allCustomers.count { it.outstandingBalance > 0 }
    val topDebtors = allCustomers.filter { it.outstandingBalance > 0 }
        .sortedByDescending { it.outstandingBalance }
        .take(5)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("admin_panel_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Top Admin Header Card ---
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (lang) {
                                    "or" -> "ମୁଖ୍ୟ ଆଡମିନ୍ ପ୍ୟାନେଲ୍"
                                    "hi" -> "मुख्य एडमिन पैनल"
                                    else -> "Master Admin Panel"
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Ratnamani Variety Store (ରତ୍ନମଣି ଭେରାଇଟି ଷ୍ଟୋର)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Owner status badge
                    if (currentUser != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isOwner) {
                                        when (lang) {
                                            "or" -> "ମାଲିକ ଆକାଉଣ୍ଟ ସଂଯୁକ୍ତ (Verified Store Owner)"
                                            "hi" -> "मालिक अकाउंट कनेक्टेड (Verified Store Owner)"
                                            else -> "Verified Store Owner Connected"
                                        }
                                    } else {
                                        currentUser?.email ?: "Signed In"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B5E20)
                                )
                                Text(
                                    text = currentUser?.email ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            TextButton(
                                onClick = {
                                    auth.signOut()
                                    coroutineScope.launch {
                                        try {
                                            credentialManager.clearCredentialState(ClearCredentialStateRequest())
                                        } catch (e: Exception) {
                                            Log.w("AdminPanel", "Clear cred state: ${e.message}")
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    text = when (lang) {
                                        "or" -> "ଲଗ୍ ଆଉଟ୍"
                                        "hi" -> "लॉग आउट"
                                        else -> "Sign Out"
                                    },
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        // Not signed in
                        Column {
                            Text(
                                text = when (lang) {
                                    "or" -> "ଆପଣଙ୍କ Gmail ID ସହିତ ସଂଯୋଗ କରନ୍ତୁ ଯାହାଦ୍ୱାରା ଅନ୍ୟ ଫୋନରେ ଡାଟା ଦେଖିପାରିବେ ଏବଂ ସବୁବେଳେ ସୁରକ୍ଷିତ ରହିବ:"
                                    "hi" -> "अपने Gmail ID से कनेक्ट करें ताकि किसी भी फ़ोन में डेटा देख सकें और सुरक्षित रहे:"
                                    else -> "Connect your Gmail ID to view store data on other phones and keep it cloud-safe:"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    isSigningIn = true
                                    val clientId = try {
                                        context.getString(R.string.default_web_client_id)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Client ID missing", Toast.LENGTH_SHORT).show()
                                        isSigningIn = false
                                        return@Button
                                    }

                                    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
                                    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

                                    coroutineScope.launch {
                                        try {
                                            val result = credentialManager.getCredential(context as Activity, request)
                                            val credential = result.credential
                                            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                                                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                                                auth.signInWithCredential(authCredential).await()
                                                currentUser = auth.currentUser
                                                Toast.makeText(
                                                    context,
                                                    when (lang) {
                                                        "or" -> "ସଫଳତାର ସହିତ ସଂଯୁକ୍ତ ହୋଇଗଲା!"
                                                        "hi" -> "सफलतापूर्वक कनेक्ट हो गया!"
                                                        else -> "Successfully connected!"
                                                    },
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        } catch (e: GetCredentialCancellationException) {
                                            Log.w("AdminPanel", "Google Sign-In cancelled: ${e.message}")
                                        } catch (e: Exception) {
                                            Log.e("AdminPanel", "Sign-in error", e)
                                            Toast.makeText(context, "Sign-in failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isSigningIn = false
                                        }
                                    }
                                },
                                enabled = !isSigningIn,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_sign_in_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (isSigningIn) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                } else {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = when (lang) {
                                        "or" -> "Google ଦ୍ୱାରା ଲଗଇନ୍ କରନ୍ତୁ ($ownerEmailTarget)"
                                        "hi" -> "Google से लॉगिन करें ($ownerEmailTarget)"
                                        else -> "Sign in with Google ($ownerEmailTarget)"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Cloud Backup & Multi-Phone Sync Actions ---
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = when (lang) {
                                "or" -> "କ୍ଲାଉଡ୍ ବ୍ୟାକଅପ୍ ଓ ଅନ୍ୟ ଫୋନ୍ ସିଙ୍କ୍"
                                "hi" -> "क्लाउड बैकअप व अन्य फ़ोन सिंक"
                                else -> "Cloud Backup & Multi-Phone Sync"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (lang) {
                            "or" -> "ଆପଣଙ୍କ ସମସ୍ତ ଷ୍ଟକ୍ ଏଣ୍ଟ୍ରି ଓ ଉଧାର ଖାତା କ୍ଲାଉଡ୍‌ରେ ସୁରକ୍ଷିତ ରହିବ। ଅନ୍ୟ କୌଣସି ଫୋନ୍‌ରେ ଆପ୍ ଖୋଲି ତୁରନ୍ତ ଡାଟା ଦେଖିପାରିବେ।"
                            "hi" -> "आपकी सारी स्टॉक एंट्री और उधार खाता क्लाउड में सुरक्षित रहेगा। किसी भी दूसरे फ़ोन में ऐप खोलकर तुरंत डेटा देख सकते हैं।"
                            else -> "All stock entries and Udhar Khata records stay backed up to Google Cloud. Open on another phone to see everything."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (syncStatusMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = syncStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Backup / Push to Cloud Button
                        Button(
                            onClick = {
                                val user = auth.currentUser
                                if (user == null) {
                                    Toast.makeText(
                                        context,
                                        when (lang) {
                                            "or" -> "ପ୍ରଥମେ ଉପରେ ଥିବା Google ID ଲଗଇନ୍ କରନ୍ତୁ"
                                            "hi" -> "पहले ऊपर दिए गए Google ID से लॉगिन करें"
                                            else -> "Please sign in with Google first"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@Button
                                }
                                isSyncing = true
                                syncStatusMessage = when (lang) {
                                    "or" -> "କ୍ଲାଉଡ୍‌ରେ ବ୍ୟାକଅପ୍ ହେଉଛି..."
                                    "hi" -> "क्लाउड में बैकअप हो रहा है..."
                                    else -> "Backing up to cloud..."
                                }
                                coroutineScope.launch {
                                    try {
                                        val res = viewModel.syncLocalToCloud(user.uid)
                                        res.onSuccess { count ->
                                            val timeStr = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
                                            syncStatusMessage = when (lang) {
                                                "or" -> "ସଫଳ! $count ଗୋଟି ରେକର୍ଡ କ୍ଲାଉଡ୍‌ରେ ସୁରକ୍ଷିତ ହେଲା ($timeStr)"
                                                "hi" -> "सफल! $count रिकॉर्ड्स क्लाउड पर सुरक्षित हो गए ($timeStr)"
                                                else -> "Success! $count records safely backed up to cloud ($timeStr)"
                                            }
                                        }.onFailure { err ->
                                            syncStatusMessage = "Sync failed: ${err.message}"
                                        }
                                    } finally {
                                        isSyncing = false
                                    }
                                }
                            },
                            enabled = !isSyncing && !isRestoring,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_sync_button")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = when (lang) {
                                    "or" -> "କ୍ଲାଉଡ୍ ସେଭ୍"
                                    "hi" -> "क्लाउड सेव"
                                    else -> "Upload Cloud"
                                },
                                fontSize = 13.sp
                            )
                        }

                        // Restore / Pull from Cloud Button (for other phone)
                        OutlinedButton(
                            onClick = {
                                val user = auth.currentUser
                                if (user == null) {
                                    Toast.makeText(
                                        context,
                                        when (lang) {
                                            "or" -> "ପ୍ରଥମେ ଉପରେ ଥିବା Google ID ଲଗଇନ୍ କରନ୍ତୁ"
                                            "hi" -> "पहले ऊपर दिए गए Google ID से लॉगिन करें"
                                            else -> "Please sign in with Google first"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                    return@OutlinedButton
                                }
                                isRestoring = true
                                syncStatusMessage = when (lang) {
                                    "or" -> "କ୍ଲାଉଡ୍‌ରୁ ଡାଟା ଡାଉନଲୋଡ୍ ହେଉଛି..."
                                    "hi" -> "क्लाउड से डेटा डाउनलोड हो रहा है..."
                                    else -> "Restoring from cloud..."
                                }
                                coroutineScope.launch {
                                    try {
                                        val res = viewModel.restoreFromCloud(user.uid)
                                        res.onSuccess { restoredCount ->
                                            val timeStr = SimpleDateFormat("hh:mm a, dd MMM", Locale.getDefault()).format(Date())
                                            syncStatusMessage = when (lang) {
                                                "or" -> "ସଫଳ! $restoredCount ଗୋଟି ସାମଗ୍ରୀ ଓ ଉଧାର ଏହି ଫୋନ୍‌ରେ ଆସିଗଲା ($timeStr)"
                                                "hi" -> "सफल! $restoredCount सामान और उधार इस फ़ोन में आ गए ($timeStr)"
                                                else -> "Restored $restoredCount items to this phone ($timeStr)"
                                            }
                                        }.onFailure { err ->
                                            syncStatusMessage = "Restore failed: ${err.message}"
                                        }
                                    } finally {
                                        isRestoring = false
                                    }
                                }
                            },
                            enabled = !isSyncing && !isRestoring,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_restore_button")
                        ) {
                            if (isRestoring) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            } else {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = when (lang) {
                                    "or" -> "ଅନ୍ୟ ଫୋନ୍ ସିଙ୍କ୍"
                                    "hi" -> "दूसरे फ़ोन में सिंक"
                                    else -> "Pull on Phone"
                                },
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // --- 3. Live Stock & Udhar Remote Telemetry Cards ---
        item {
            Text(
                text = when (lang) {
                    "or" -> "ଲାଇଭ୍ ଷ୍ଟୋର୍ ମନିଟରିଂ (ଦୋକାନ ସ୍ଥିତି)"
                    "hi" -> "लाइव स्टोर मॉनिटरिंग (दुकान स्थिति)"
                    else -> "Live Store Telemetry & Valuation"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Stock units card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${allProducts.size} items",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (lang) {
                                "or" -> "ମୋଟ ସାମଗ୍ରୀ (ଷ୍ଟକ୍)"
                                "hi" -> "कुल सामान (स्टॉक)"
                                else -> "Total Products"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Units: ${String.format(Locale.getDefault(), "%.1f", totalStockUnits)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Total Udhar outstanding card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%.0f", totalOutstanding)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = when (lang) {
                                "or" -> "ମୋଟ ବକେୟା ଉଧାର"
                                "hi" -> "कुल बकाया उधार"
                                else -> "Total Udhar Pending"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (lang) {
                                "or" -> "$debtorsCount ଜଣ ଗ୍ରାହକ"
                                "hi" -> "$debtorsCount ग्राहक"
                                else -> "$debtorsCount debtors"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        item {
            // Inventory Valuation Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = when (lang) {
                                "or" -> "ମୋଟ ଷ୍ଟକ୍ ବିକ୍ରି ମୂଲ୍ୟ (Selling Value)"
                                "hi" -> "कुल स्टॉक बिक्री मूल्य (Selling Value)"
                                else -> "Total Stock Retail Value"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%.2f", totalStockSellingValue)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = when (lang) {
                                "or" -> "କ୍ରୟ ମୂଲ୍ୟ (Cost Value)"
                                "hi" -> "लागत मूल्य (Cost Value)"
                                else -> "Purchase Cost Value"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format(Locale.getDefault(), "%.2f", totalStockCostValue)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // --- 4. Low Stock Critical Alerts ---
        if (lowStockProducts.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (lang) {
                            "or" -> "କମ୍ ଷ୍ଟକ୍ ଚେତାବନୀ (${lowStockProducts.size} ସାମଗ୍ରୀ ଶେଷ ହେବାକୁ ଯାଉଛି)"
                            "hi" -> "कम स्टॉक चेतावनी (${lowStockProducts.size} सामान ख़त्म होने वाला है)"
                            else -> "Low Stock Alert (${lowStockProducts.size} items running low)"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            items(lowStockProducts.take(4)) { prod ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = prod.name, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Category: ${prod.category}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error
                        ) {
                            Text(
                                text = "Stock: ${prod.currentStock} ${prod.unitType}",
                                color = MaterialTheme.colorScheme.onError,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 5. Top Udhar Debtors (Bakeya Khata) ---
        if (topDebtors.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CurrencyRupee,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (lang) {
                            "or" -> "ଅଧିକ ଉଧାର ଥିବା ଗ୍ରାହକ (Top Debtors)"
                            "hi" -> "अधिक उधार वाले ग्राहक (Top Debtors)"
                            else -> "Top Udhar Customers"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(topDebtors) { cust ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = cust.name, fontWeight = FontWeight.Bold)
                            if (cust.phone.isNotBlank()) {
                                Text(
                                    text = "📞 ${cust.phone}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "₹${String.format(Locale.getDefault(), "%.0f", cust.outstandingBalance)}",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.titleMedium
                            )

                            if (cust.phone.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL).apply {
                                            data = Uri.parse("tel:${cust.phone}")
                                        }
                                        context.startActivity(intent)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Phone,
                                        contentDescription = "Call",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 6. Instructions for Multi-Phone Access ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (lang) {
                                "or" -> "ଅନ୍ୟ ଫୋନ୍‌ରେ କିପରି ଖୋଲିବେ?"
                                "hi" -> "दूसरे फ़ोन में कैसे खोलें?"
                                else -> "How to access on any other phone:"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val stepsText = when (lang) {
                        "or" -> "୧. ଯେକୌଣସି ଆଣ୍ଡ୍ରଏଡ୍ ଫୋନ୍ ବା ଟାବଲେଟ୍‌ରେ ଏହି ଆପ୍ ଇନଷ୍ଟଲ୍ କରନ୍ତୁ।\n୨. 'Admin Panel' କୁ ଯାଇ Google ଲଗଇନ୍ ଦବାନ୍ତୁ ଏବଂ $ownerEmailTarget ବାଛନ୍ତୁ।\n୩. 'ଅନ୍ୟ ଫୋନ୍ ସିଙ୍କ୍' ବଟନ୍ ଦବାନ୍ତୁ। ସମସ୍ତ ଷ୍ଟକ୍ ଓ ଉଧାର ଖାତା ତୁରନ୍ତ ସେହି ଫୋନ୍‌ରେ ଦେଖାଯିବ!"
                        "hi" -> "1. किसी भी एंड्रॉयड फ़ोन या टैबलेट में यह ऐप इनस्टॉल करें।\n2. 'Admin Panel' में जाकर Google लॉगिन दबाएं और $ownerEmailTarget चुनें।\n3. 'दूसरे फ़ोन में सिंक' दबाएं। सारा स्टॉक और उधार खाता तुरंत उस फ़ोन पर आ जाएगा!"
                        else -> "1. Install this app on any other Android phone or tablet.\n2. Open Admin Panel and Sign in with Google ($ownerEmailTarget).\n3. Tap 'Pull on Phone'. All your products, stock, and Udhar Khata will sync instantly!"
                    }

                    Text(
                        text = stepsText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }
}
