package com.privacy.whatsappdecryptor.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.core.crypto.KeyValidator
import com.privacy.whatsappdecryptor.core.keystore.KeyStorageManager
import com.privacy.whatsappdecryptor.core.service.StoragePreflight
import com.privacy.whatsappdecryptor.ui.theme.Spacing

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
                title = {
                    Column {
                        Text(
                            text = "WhatsApp Decryptor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Secure local backup reader",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Thumb-zone Primary CTA
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.md)
                ) {
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
                            .height(56.dp),
                        shape = RoundedCornerShape(Spacing.md)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "Decrypt & Explore Inventory",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Hero Trust Badge Card (60/30/10 & Emotional Design)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(Spacing.lg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Surface(
                        shape = RoundedCornerShape(Spacing.md),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "100% On-Device & Private",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = "Zero network transmission. Decryption keys and chat messages never leave this phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Step 1: File Selection Card
            StepHeader(stepNumber = "1", title = "Select Backup File (.crypt14 / .crypt15)")

            OutlinedCard(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Spacing.md),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (selectedUri != null) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Surface(
                        shape = RoundedCornerShape(Spacing.sm),
                        color = if (selectedUri != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (selectedUri != null) Icons.Default.CheckCircle else Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = if (selectedUri != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fileName ?: "Choose msgstore.db.crypt15",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (fileName != null) FontWeight.Bold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (fileSize > 0) {
                            Text(
                                text = "Size: ${StoragePreflight.formatBytes(fileSize)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Typically found in WhatsApp/Databases",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(Spacing.sm),
                        contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Text("Browse", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Step 2: Key Entry Card
            StepHeader(stepNumber = "2", title = "64-Digit Encryption Key")

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("64-digit hexadecimal key") },
                placeholder = { Text("Paste hex key…") },
                leadingIcon = {
                    Icon(
                        imageVector = if (isKeyValid) Icons.Default.CheckCircle else Icons.Default.Key,
                        contentDescription = null,
                        tint = if (isKeyValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            keyInput = clip.trim()
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste Key")
                    }
                },
                isError = keyInput.isNotEmpty() && !isKeyValid,
                supportingText = {
                    if (keyInput.isNotEmpty() && !isKeyValid) {
                        Text(
                            text = "Must be 64 hexadecimal characters (entered: ${KeyValidator.normalize(keyInput).length})",
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isKeyValid) {
                        Text(
                            text = "Key format valid • Ready for decryption",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "WhatsApp > Settings > Chats > Chat backup > End-to-end encrypted backup",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Spacing.md)
            )

            // Step 3: Remember Key in Keystore
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                shape = RoundedCornerShape(Spacing.md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Remember Key",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(Spacing.xxs))
                        Text(
                            text = "Stored securely in hardware-backed Android Keystore",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rememberKey,
                        onCheckedChange = { rememberKey = it }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun StepHeader(stepNumber: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
