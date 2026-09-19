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
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState
import kotlinx.coroutines.launch
import java.io.File

enum class Screen {
    SETUP,
    PROGRESS,
    PROJECTS,
    IN_LISTINGS,
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
            Screen.PROJECTS
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

                    // Project Inventory States
                    val projectSummaries by viewModel.projectSummaries.collectAsStateWithLifecycle()
                    val isProjectScanning by viewModel.isProjectScanning.collectAsStateWithLifecycle()
                    val projectScanProgress by viewModel.projectScanProgress.collectAsStateWithLifecycle()
                    val projectSearchQuery by viewModel.projectSearchQuery.collectAsStateWithLifecycle()
                    val projectStatusFilter by viewModel.projectStatusFilter.collectAsStateWithLifecycle()
                    val projectMonths by viewModel.projectMonths.collectAsStateWithLifecycle()

                    // IN Listings States
                    val inProjectSummaries by viewModel.inProjectSummaries.collectAsStateWithLifecycle()
                    val inListingsSearchQuery by viewModel.inListingsSearchQuery.collectAsStateWithLifecycle()
                    val inListingsSocietyFilter by viewModel.inListingsSocietyFilter.collectAsStateWithLifecycle()
                    val isLoadingInListings by viewModel.isLoadingInListings.collectAsStateWithLifecycle()

                    // Helper to share files
                    fun shareFile(file: File, mimeType: String, subject: String, title: String) {
                        val fileUri = androidx.core.content.FileProvider.getUriForFile(
                            this@MainActivity,
                            "${packageName}.fileprovider",
                            file
                        )
                        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = mimeType
                            putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(android.content.Intent.createChooser(shareIntent, title))
                    }

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
                            Screen.SETTINGS -> currentScreen = Screen.PROJECTS
                            Screen.CHAT_DETAIL -> {
                                viewModel.clearSelectedChat()
                                currentScreen = Screen.CHAT_LIST
                            }
                            Screen.CHAT_LIST -> currentScreen = Screen.PROJECTS
                            Screen.IN_LISTINGS -> currentScreen = Screen.PROJECTS
                            Screen.PROJECTS -> finish()
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

                    // Centralized Inventory Export Dialogs (Processing, Complete, Error)
                    when (val state = inventoryExportState) {
                        is InventoryExportState.Processing -> {
                            AlertDialog(
                                onDismissRequest = {},
                                icon = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
                                title = { Text("Processing Inventory", fontWeight = FontWeight.Bold) },
                                text = { Text("${state.detail}\n\nProcessed: ${state.processedMessages}") },
                                confirmButton = {}
                            )
                        }
                        is InventoryExportState.Complete -> {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissInventoryExportDialog,
                                icon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                },
                                title = { Text("Export Ready!", fontWeight = FontWeight.Bold) },
                                text = {
                                    Text("Successfully generated:\n${state.file.name}\n\nListings included: ${state.totalRows}\nOpening share menu...")
                                },
                                confirmButton = {
                                    Button(onClick = viewModel::dismissInventoryExportDialog) {
                                        Text("OK")
                                    }
                                }
                            )
                        }
                        is InventoryExportState.Error -> {
                            AlertDialog(
                                onDismissRequest = viewModel::dismissInventoryExportDialog,
                                icon = {
                                    Icon(
                                        Icons.Default.Error,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                title = { Text("Export Failed", fontWeight = FontWeight.Bold) },
                                text = { Text(state.message) },
                                confirmButton = {
                                    Button(onClick = viewModel::dismissInventoryExportDialog) {
                                        Text("Dismiss")
                                    }
                                }
                            )
                        }
                        InventoryExportState.Idle -> {}
                    }

                    val isDashboard = currentScreen == Screen.PROJECTS || currentScreen == Screen.IN_LISTINGS || currentScreen == Screen.CHAT_LIST

                    Scaffold(
                        bottomBar = {
                            if (isDashboard && selectedChatIds.isEmpty()) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    NavigationBarItem(
                                        selected = currentScreen == Screen.PROJECTS,
                                        onClick = { currentScreen = Screen.PROJECTS },
                                        icon = {
                                            Icon(
                                                Icons.Default.Apartment,
                                                contentDescription = "Projects"
                                            )
                                        },
                                        label = { Text("Projects") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == Screen.IN_LISTINGS,
                                        onClick = {
                                            currentScreen = Screen.IN_LISTINGS
                                            viewModel.loadInListings(this@MainActivity)
                                        },
                                        icon = {
                                            Icon(
                                                Icons.Default.FormatListBulleted,
                                                contentDescription = "IN Listings"
                                            )
                                        },
                                        label = { Text("IN Listings") }
                                    )
                                    NavigationBarItem(
                                        selected = currentScreen == Screen.CHAT_LIST,
                                        onClick = { currentScreen = Screen.CHAT_LIST },
                                        icon = {
                                            Icon(
                                                Icons.Default.Chat,
                                                contentDescription = "Chats"
                                            )
                                        },
                                        label = { Text("Chats") }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
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
                                            currentScreen = Screen.PROJECTS
                                        },
                                        onCancel = {
                                            currentScreen = Screen.SETUP
                                        }
                                    )
                                }

                                Screen.PROJECTS -> {
                                    ProjectInventoryScreen(
                                        projectSummaries = projectSummaries,
                                        isScanning = isProjectScanning,
                                        scanProgress = projectScanProgress,
                                        searchQuery = projectSearchQuery,
                                        onSearchQueryChanged = viewModel::onProjectSearchQueryChanged,
                                        statusFilter = projectStatusFilter,
                                        onStatusFilterChanged = viewModel::onProjectStatusFilterChanged,
                                        selectedMonths = projectMonths,
                                        onMonthsChanged = { viewModel.onProjectMonthsChanged(it, this@MainActivity) },
                                        onScanProjects = { months ->
                                            viewModel.scanProjectInventory(this@MainActivity, months, forceRefresh = true)
                                        },
                                        onExportSingleProject = { proj ->
                                            viewModel.exportSingleProjectSubExcel(this@MainActivity, proj, projectMonths) { shareFile ->
                                                shareFile(shareFile, "text/csv", "Sub-Excel: ${proj.society}", "Share ${proj.society} Sub-Excel")
                                            }
                                        },
                                        onExportAllZip = {
                                            viewModel.exportAllProjectsZip(this@MainActivity, projectMonths) { zipFile ->
                                                shareFile(zipFile, "application/zip", "All Project Sub-Excels", "Share All Project Sub-Excels (ZIP)")
                                            }
                                        },
                                        onExportMasterCsv = {
                                            viewModel.exportMasterPropertyInventory(this@MainActivity, projectMonths) { csvFile ->
                                                shareFile(csvFile, "text/csv", "Master Property Inventory", "Share Master Inventory CSV")
                                            }
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = Screen.SETTINGS
                                        }
                                    )
                                }

                                Screen.IN_LISTINGS -> {
                                    InListingsScreen(
                                        inProjects = inProjectSummaries,
                                        isLoading = isLoadingInListings,
                                        searchQuery = inListingsSearchQuery,
                                        onSearchQueryChanged = { viewModel.onInListingsSearchQueryChanged(it, this@MainActivity) },
                                        onExportSingleProject = { proj ->
                                            viewModel.exportSingleProjectSubExcel(this@MainActivity, proj, projectMonths) { shareFile ->
                                                shareFile(shareFile, "text/csv", "Sub-Excel: ${proj.society}", "Share ${proj.society} Sub-Excel")
                                            }
                                        },
                                        onExportMasterCsv = {
                                            viewModel.exportMasterPropertyInventory(this@MainActivity, projectMonths) { csvFile ->
                                                shareFile(csvFile, "text/csv", "Master Property Inventory", "Share Master Inventory CSV")
                                            }
                                        },
                                        onExportAllZip = {
                                            viewModel.exportAllProjectsZip(this@MainActivity, projectMonths) { zipFile ->
                                                shareFile(zipFile, "application/zip", "All Project Sub-Excels", "Share All Project Sub-Excels (ZIP)")
                                            }
                                        },
                                        onRefresh = {
                                            viewModel.loadInListings(this@MainActivity)
                                        },
                                        onNavigateToSettings = {
                                            currentScreen = Screen.SETTINGS
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
                                        onNavigateToProjects = {
                                            currentScreen = Screen.PROJECTS
                                        },
                                        onExportSelected = {
                                            val firstSelectedId = selectedChatIds.firstOrNull()
                                            val chat = chats.firstOrNull { it.id == firstSelectedId }
                                            if (chat != null) {
                                                exportPendingChat = chat
                                                showExportDialog = true
                                            }
                                        }
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
