package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.backup.BackupManager
import com.example.ui.components.AdminPinDialog
import com.example.ui.theme.GroceryGreenPrimary
import com.example.ui.theme.UdharRed
import com.example.ui.viewmodel.GroceryViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBackupScreen(
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

    val isBackupInProgress by viewModel.isBackupInProgress.collectAsStateWithLifecycle()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()
    val remoteBackups by viewModel.remoteBackupsList.collectAsStateWithLifecycle()
    val isAdminAuthenticated by viewModel.isAdminAuthenticated.collectAsStateWithLifecycle()

    var showPinDialog by remember { mutableStateOf(!isAdminAuthenticated) }
    var showChangePinDialog by remember { mutableStateOf(false) }

    // Google Sign-In Account State
    var connectedGoogleAccount by remember { mutableStateOf<String?>(null) }
    var googleAccessToken by remember { mutableStateOf<String?>(null) }
    var notificationMessage by remember { mutableStateOf<String?>(null) }

    // Check if previously signed in
    LaunchedEffect(Unit) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
        if (account != null) {
            connectedGoogleAccount = account.email
        }
    }

    // Google Sign In Launcher
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.result
                connectedGoogleAccount = account.email
                notificationMessage = "Connected Google Account: ${account.email}"

                // Fetch OAuth token in background
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                            context,
                            account.account ?: return@launch,
                            "oauth2:https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata"
                        )
                        withContext(Dispatchers.Main) {
                            googleAccessToken = token
                            notificationMessage = "Google Drive authorization successful for ${account.email}!"
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            notificationMessage = "Drive Auth token: ${e.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                notificationMessage = "Google Sign-In failed: ${e.message}"
            }
        }
    }

    // Local JSON file picker for Restore
    val restoreFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val json = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    withContext(Dispatchers.Main) {
                        viewModel.restoreFromLocalJson(
                            json,
                            onSuccess = { msg -> notificationMessage = msg },
                            onError = { err -> notificationMessage = "Restore Error: $err" }
                        )
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        notificationMessage = "File read failed: ${e.message}"
                    }
                }
            }
        }
    }

    // Admin PIN gate
    if (showPinDialog) {
        AdminPinDialog(
            onPinVerified = {
                viewModel.authenticateAdmin(true)
                showPinDialog = false
            },
            onDismiss = {
                // Return to dashboard if cancelled
                viewModel.navigateTo(com.example.ui.navigation.Screen.DASHBOARD)
            },
            verifyPinFn = { pin -> viewModel.verifyPin(pin) }
        )
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        var oldPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showChangePinDialog = false },
            title = { Text("Change Admin PIN / पिन बदलें") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { oldPin = it },
                        label = { Text("Current PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("New 4-Digit PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (viewModel.verifyPin(oldPin)) {
                        if (newPin.length >= 4) {
                            viewModel.changePin(newPin)
                            showChangePinDialog = false
                            notificationMessage = "Admin PIN changed successfully!"
                        } else {
                            errorMsg = "New PIN must be at least 4 digits."
                        }
                    } else {
                        errorMsg = "Current PIN is incorrect."
                    }
                }) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePinDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Editable Store Profile State
    var shopName by remember(settings.shopName) { mutableStateOf(settings.shopName) }
    var tagline by remember(settings.tagline) { mutableStateOf(settings.tagline) }
    var ownerName by remember(settings.ownerName) { mutableStateOf(settings.ownerName) }
    var phone by remember(settings.phone) { mutableStateOf(settings.phone) }
    var address by remember(settings.address) { mutableStateOf(settings.address) }
    var receiptFooter by remember(settings.receiptFooter) { mutableStateOf(settings.receiptFooter) }
    var driveFolder by remember(settings.googleDriveFolderName) { mutableStateOf(settings.googleDriveFolderName) }

    Scaffold(
        snackbarHost = {
            notificationMessage?.let { msg ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = { notificationMessage = null }) { Text("OK") } }
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
        ) {
            // Google Drive Cloud Backup Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = GroceryGreenPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr("Google Drive Cloud Backup", "गूगल ड्राइव क्लाउड बैकअप", "ଗୁଗୁଲ୍ ଡ୍ରାଇଭ୍ କ୍ଲାଉଡ୍ ବ୍ୟାକଅପ୍"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Target Owner Account: ${settings.googleAccountEmail}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Cloud Folder: '${settings.googleDriveFolderName}'",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Status of Google connection
                        if (connectedGoogleAccount != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFC8E6C9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GroceryGreenPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Connected: $connectedGoogleAccount",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GroceryGreenPrimary
                                    )
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                        .requestEmail()
                                        .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"), Scope("https://www.googleapis.com/auth/drive.appdata"))
                                        .setAccountName(settings.googleAccountEmail)
                                        .build()
                                    val client = GoogleSignIn.getClient(context, gso)
                                    googleSignInLauncher.launch(client.signInIntent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_google_drive_button")
                            ) {
                                Icon(Icons.Default.AccountCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Google Drive Account")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Upload button & progress
                        if (isBackupInProgress) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(backupStatusMessage ?: "Processing backup...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (googleAccessToken != null) {
                                            viewModel.uploadToGoogleDrive(
                                                googleAccessToken!!,
                                                onSuccess = { msg -> notificationMessage = msg },
                                                onError = { err -> notificationMessage = err }
                                            )
                                        } else {
                                            // Fallback local backup if Drive token not yet acquired
                                            viewModel.createLocalBackup(
                                                onSuccess = { file ->
                                                    notificationMessage = "Local backup saved: ${file.name}. Connect Google Drive for cloud upload."
                                                },
                                                onError = { err -> notificationMessage = err }
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("backup_now_button")
                                ) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Backup Now", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (googleAccessToken != null) {
                                            viewModel.fetchRemoteDriveBackups(googleAccessToken!!) { err ->
                                                notificationMessage = err
                                            }
                                        } else {
                                            notificationMessage = "Please connect Google Drive first."
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("List Backups", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Last Backup: " + (if (settings.lastBackupTimestamp > 0)
                                SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(settings.lastBackupTimestamp))
                            else "No backup yet"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Text(
                            text = "Status: ${settings.lastBackupStatus}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Remote Drive Backups List if fetched
            if (remoteBackups.isNotEmpty()) {
                item {
                    Text("Remote Google Drive Backups (${remoteBackups.size}):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                }
                items(remoteBackups) { remoteFile ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(remoteFile.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Size: ${remoteFile.sizeBytes / 1024} KB | ${remoteFile.modifiedTime}", style = MaterialTheme.typography.labelSmall)
                            }
                            Button(
                                onClick = {
                                    if (googleAccessToken != null) {
                                        viewModel.restoreFromDrive(
                                            googleAccessToken!!,
                                            remoteFile.id,
                                            onSuccess = { msg -> notificationMessage = msg },
                                            onError = { err -> notificationMessage = err }
                                        )
                                    }
                                }
                            ) {
                                Text("Restore", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Local Offline Backup & Restore Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr("Local Offline Backup & Restore", "ऑफलाइन स्थानीय बैकअप (Local Storage)", "ଅଫଲାଇନ୍ ଲୋକାଲ୍ ବ୍ୟାକଅପ୍ ଓ ପୁନରୁଦ୍ଧାର"),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Create an encrypted local backup file on phone storage, or restore from a previous backup file.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.createLocalBackup(
                                        onSuccess = { file ->
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Save / Share Local Backup"))
                                        },
                                        onError = { notificationMessage = it }
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("export_local_backup_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Export Local", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { restoreFilePickerLauncher.launch("application/json") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("import_local_backup_button")
                            ) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore File", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Store Profile Settings Form
            item {
                Text(
                    text = tr("Shop Profile & Store Settings", "दुकान विवरण व बिलिंग सेटिंग्स", "ଦୋକାନ ପ୍ରୋଫାଇଲ୍ ଓ ସେଟିଙ୍ଗ୍ସ"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text("Shop Name / दुकान का नाम *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("settings_shop_name_input")
                    )

                    OutlinedTextField(
                        value = tagline,
                        onValueChange = { tagline = it },
                        label = { Text("Tagline / स्लोगन") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ownerName,
                            onValueChange = { ownerName = it },
                            label = { Text("Owner Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone Number") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Shop Address / पता") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = receiptFooter,
                        onValueChange = { receiptFooter = it },
                        label = { Text("Receipt Footer Message") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = driveFolder,
                        onValueChange = { driveFolder = it },
                        label = { Text("Google Drive Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Negative stock toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Allow Negative Stock Billing", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Permit billing items even when recorded stock is 0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.allowNegativeStock,
                            onCheckedChange = { checked ->
                                viewModel.updateSettings(settings.copy(allowNegativeStock = checked))
                            }
                        )
                    }

                    // Language selection
                    Column {
                        Text(tr("App Language", "ऐप की भाषा", "ଆପ୍ ଭାଷା"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "en" to "English",
                                "hi" to "हिंदी (Hindi)",
                                "or" to "ଓଡ଼ିଆ (Odia)"
                            ).forEach { (code, label) ->
                                FilterChip(
                                    selected = settings.language == code,
                                    onClick = { viewModel.updateSettings(settings.copy(language = code)) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Save Store Profile Button
                    Button(
                        onClick = {
                            viewModel.updateSettings(
                                settings.copy(
                                    shopName = shopName.trim().ifBlank { settings.shopName },
                                    tagline = tagline.trim(),
                                    ownerName = ownerName.trim(),
                                    phone = phone.trim(),
                                    address = address.trim(),
                                    receiptFooter = receiptFooter.trim(),
                                    googleDriveFolderName = driveFolder.trim().ifBlank { "Smart Grocery Shop Manager Backup" }
                                )
                            )
                            notificationMessage = "Shop settings saved successfully!"
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_shop_settings_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Shop Profile")
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Change PIN button
                    OutlinedButton(
                        onClick = { showChangePinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Change Admin Security PIN")
                    }
                }
            }
        }
    }
}
