package com.privacy.whatsappdecryptor.ui

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.service.DecryptionForegroundService
import com.privacy.whatsappdecryptor.core.service.DecryptionProgressState
import com.privacy.whatsappdecryptor.ui.screens.*
import com.privacy.whatsappdecryptor.ui.theme.WhatsAppDecryptorTheme
import com.privacy.whatsappdecryptor.ui.viewmodel.ChatViewModel
import com.privacy.whatsappdecryptor.ui.viewmodel.DatabaseState
import com.privacy.whatsappdecryptor.ui.viewmodel.ExportFormat
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    SETUP,
    PROGRESS,
    CHAT_LIST,
    CHAT_DETAIL,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Privacy Mode (FLAG_SECURE) default enabled
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val privacyMode = prefs.getBoolean("privacy_mode_enabled", true)
        updatePrivacyFlags(privacyMode)

        // Check if a decrypted database already exists from a previous session
        val decryptedFile = File(noBackupFilesDir, "msgstore_decrypted.db")
        val initialScreen = if (decryptedFile.exists()) {
            viewModel.loadDatabase(decryptedFile)
            Screen.CHAT_LIST
        } else {
            Screen.SETUP
        }

        setContent {
            WhatsAppDecryptorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    var privacyModeEnabled by remember { mutableStateOf(privacyMode) }

                    // Export State
                    var exportPendingChat by remember { mutableStateOf<ChatSummary?>(null) }
                    var activeExportFormat by remember { mutableStateOf(ExportFormat.TXT) }
                    var showExportDialog by remember { mutableStateOf(false) }

                    val progressState by DecryptionForegroundService.progressState.collectAsStateWithLifecycle()
                    val chats by viewModel.chats.collectAsStateWithLifecycle()
                    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                    val onlyGroups by viewModel.onlyGroups.collectAsStateWithLifecycle()
                    val selectedChat by viewModel.selectedChat.collectAsStateWithLifecycle()
                    val messages by viewModel.messages.collectAsStateWithLifecycle()
                    val isLoadingMessages by viewModel.isLoadingMessages.collectAsStateWithLifecycle()
                    val selectedChatIds by viewModel.selectedChatIdsForExport.collectAsStateWithLifecycle()
                    val inventoryExportState by viewModel.inventoryExportState.collectAsStateWithLifecycle()

                    // SAF Document Creator for Exports
                    val createDocumentLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.CreateDocument(activeExportFormat.mimeType)
                    ) { uri: Uri? ->
                        val chatToExport = exportPendingChat
                        if (uri != null && chatToExport != null) {
                            lifecycleScope.launch {
                                try {
                                    contentResolver.openOutputStream(uri)?.use { os ->
                                        viewModel.exportChat(chatToExport, activeExportFormat, os)
                                    }
                                    Toast.makeText(this@MainActivity, "Chat exported successfully!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(this@MainActivity, "Export failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    exportPendingChat = null
                                }
                            }
                        }
                    }

                    // Back Handler Navigation
                    BackHandler {
                        when (currentScreen) {
                            Screen.SETTINGS -> currentScreen = Screen.CHAT_LIST
                            Screen.CHAT_DETAIL -> {
                                viewModel.clearSelectedChat()
                                currentScreen = Screen.CHAT_LIST
                            }
                            Screen.CHAT_LIST -> finish()
                            Screen.PROGRESS -> {
                                DecryptionForegroundService.cancel(this@MainActivity)
                                currentScreen = Screen.SETUP
                            }
                            Screen.SETUP -> finish()
                        }
                    }

                    // Export Dialog
                    if (showExportDialog && exportPendingChat != null) {
                        ExportDialog(
                            exportTargetDescription = "Exporting chat: \"${exportPendingChat?.title}\"",
                            onDismiss = {
                                showExportDialog = false
                                exportPendingChat = null
                            },
                            onConfirmExport = { format ->
                                activeExportFormat = format
                                showExportDialog = false
                                val sanitizedTitle = exportPendingChat?.title?.replace("[^a-zA-Z0-9_]".toRegex(), "_") ?: "chat"
                                val fileName = "chat_${sanitizedTitle}_export.${format.extension}"
                                createDocumentLauncher.launch(fileName)
                            }
                        )
                    }

                    when (currentScreen) {
                        Screen.SETUP -> {
                            SetupScreen(
                                onStartDecryption = { uri, key, _ ->
                                    DecryptionForegroundService.start(this@MainActivity, uri, key)
                                    currentScreen = Screen.PROGRESS
                                }
                            )
                        }

                        Screen.PROGRESS -> {
                            ProgressScreen(
                                progressState = progressState,
                                onDecryptionComplete = {
                                    val targetFile = File(noBackupFilesDir, "msgstore_decrypted.db")
                                    viewModel.loadDatabase(targetFile)
                                    currentScreen = Screen.CHAT_LIST
                                },
                                onCancel = {
                                    currentScreen = Screen.SETUP
                                }
                            )
                        }

                        Screen.CHAT_LIST -> {
                            ChatListScreen(
                                chats = chats,
                                searchQuery = searchQuery,
                                onSearchQueryChanged = viewModel::onSearchQueryChanged,
                                onlyGroups = onlyGroups,
                                onFilterGroupsChanged = viewModel::onFilterGroupsChanged,
                                selectedChatIds = selectedChatIds,
                                onToggleChatSelection = viewModel::toggleChatSelection,
                                onSelectAll = viewModel::selectAllChats,
                                onClearSelection = viewModel::clearSelection,
                                onChatClicked = { chat ->
                                    viewModel.selectChat(chat)
                                    currentScreen = Screen.CHAT_DETAIL
                                },
                                onNavigateToSettings = {
                                    currentScreen = Screen.SETTINGS
                                },
                                onExportSelected = {
                                    // For single selected chat in list
                                    val firstSelectedId = selectedChatIds.firstOrNull()
                                    val chat = chats.firstOrNull { it.id == firstSelectedId }
                                    if (chat != null) {
                                        exportPendingChat = chat
                                        showExportDialog = true
                                    }
                                },
                                onExportPropertyInventory = { months ->
                                    viewModel.exportMasterPropertyInventory(this@MainActivity, months) { shareFile ->
                                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "${packageName}.fileprovider",
                                            shareFile
                                        )
                                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/csv"
                                            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                                            putExtra(android.content.Intent.EXTRA_SUBJECT, "Master Important Dealer Inventory")
                                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        startActivity(android.content.Intent.createChooser(shareIntent, "Share Inventory to WhatsApp / Apps"))
                                    }
                                },
                                inventoryExportState = inventoryExportState,
                                onDismissInventoryDialog = viewModel::dismissInventoryExportDialog
                            )
                        }

                        Screen.CHAT_DETAIL -> {
                            val activeChat = selectedChat
                            if (activeChat != null) {
                                ChatDetailScreen(
                                    chat = activeChat,
                                    messages = messages,
                                    isLoading = isLoadingMessages,
                                    onBack = {
                                        viewModel.clearSelectedChat()
                                        currentScreen = Screen.CHAT_LIST
                                    },
                                    onExportChat = {
                                        exportPendingChat = activeChat
                                        showExportDialog = true
                                    }
                                )
                            } else {
                                currentScreen = Screen.CHAT_LIST
                            }
                        }

                        Screen.SETTINGS -> {
                            SettingsScreen(
                                decryptedFile = File(noBackupFilesDir, "msgstore_decrypted.db"),
                                privacyModeEnabled = privacyModeEnabled,
                                onTogglePrivacyMode = { enabled ->
                                    privacyModeEnabled = enabled
                                    prefs.edit().putBoolean("privacy_mode_enabled", enabled).apply()
                                    updatePrivacyFlags(enabled)
                                },
                                onPurgeDecryptedData = {
                                    viewModel.purgeDecryptedData(File(noBackupFilesDir, "msgstore_decrypted.db"))
                                    currentScreen = Screen.SETUP
                                },
                                onBack = {
                                    currentScreen = Screen.CHAT_LIST
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updatePrivacyFlags(enabled: Boolean) {
        if (enabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
