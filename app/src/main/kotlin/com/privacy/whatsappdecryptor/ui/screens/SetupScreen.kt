package com.privacy.whatsappdecryptor.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.core.crypto.KeyValidator
import com.privacy.whatsappdecryptor.core.keystore.KeyStorageManager
import com.privacy.whatsappdecryptor.core.service.StoragePreflight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onStartDecryption: (Uri, String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf<Long>(-1L) }
    var keyInput by remember { mutableStateOf("") }
    var rememberKey by remember { mutableStateOf(false) }

    // Check if key was previously remembered in Keystore
    LaunchedEffect(Unit) {
        val remembered = KeyStorageManager.retrieveKey(context)
        if (!remembered.isNullOrBlank()) {
            keyInput = remembered
            rememberKey = true
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex)
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }
        }
    }

    val isKeyValid = KeyValidator.isValidKeyFormat(keyInput)
    val canDecrypt = selectedUri != null && isKeyValid

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WhatsApp Backup Decryptor", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Column {
                        Text(
                            "100% Offline & Private",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Zero internet permission. Decryption and database reading occur entirely on-device.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Step 1: Select Backup File
            Text("1. Select Backup File (.crypt15)", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedCard(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName ?: "Tap to choose msgstore.db.crypt15",
                            fontWeight = if (fileName != null) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (fileSize > 0) {
                            Text(
                                text = "Size: ${StoragePreflight.formatBytes(fileSize)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("Browse", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }

            // Step 2: Enter Key
            Text("2. WhatsApp 64-Digit Backup Key", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("64-digit hex key") },
                placeholder = { Text("e.g. 0123456789abcdef...") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            keyInput = clip
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste Key")
                    }
                },
                isError = keyInput.isNotEmpty() && !isKeyValid,
                supportingText = {
                    if (keyInput.isNotEmpty() && !isKeyValid) {
                        Text("Must be 64 hexadecimal characters (currently ${KeyValidator.normalize(keyInput).length})")
                    } else if (isKeyValid) {
                        Text("Key format valid", color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("Obtain from WhatsApp: Settings > Chats > Chat backup > End-to-end encrypted backup")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Remember key toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Remember Key", fontWeight = FontWeight.Medium)
                    Text(
                        "Stored securely in hardware-backed Android Keystore",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = rememberKey,
                    onCheckedChange = { rememberKey = it }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Decrypt CTA Button
            Button(
                onClick = {
                    val uri = selectedUri
                    if (uri != null && isKeyValid) {
                        if (rememberKey) {
                            KeyStorageManager.saveKey(context, keyInput)
                        } else {
                            KeyStorageManager.clearKey(context)
                        }
                        onStartDecryption(uri, keyInput, rememberKey)
                    }
                },
                enabled = canDecrypt,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Decrypt & Explore Chats", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
