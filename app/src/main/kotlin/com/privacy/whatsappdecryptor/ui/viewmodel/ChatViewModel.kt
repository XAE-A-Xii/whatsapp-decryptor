package com.privacy.whatsappdecryptor.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.privacy.whatsappdecryptor.core.database.AndroidWhatsAppDatabaseReader
import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.export.JsonChatExporter
import com.privacy.whatsappdecryptor.core.export.TxtChatExporter
import android.content.Context
import com.privacy.whatsappdecryptor.core.inventory.InventoryCsvWriter
import com.privacy.whatsappdecryptor.core.inventory.AndroidInventorySql
import com.privacy.whatsappdecryptor.core.inventory.StreamingInventoryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    TXT("Plain Text (.txt)", "txt", "text/plain"),
    JSON("Structured JSON (.json)", "json", "application/json")
}

sealed class InventoryExportState {
    object Idle : InventoryExportState()
    data class Processing(val processedMessages: Int, val detail: String = "Preparing export…") : InventoryExportState()
    data class Complete(val file: File, val totalRows: Int, val targetInCount: Int) : InventoryExportState()
    data class Error(val message: String) : InventoryExportState()
}

sealed class DatabaseState {
    object Closed : DatabaseState()
    object Loading : DatabaseState()
    data class Ready(val file: File) : DatabaseState()
    data class Error(val message: String) : DatabaseState()
}

class ChatViewModel : ViewModel() {

    private var databaseSource: AndroidWhatsAppDatabaseReader? = null
    private var inventoryJob: Job? = null

    private val _dbState = MutableStateFlow<DatabaseState>(DatabaseState.Closed)
    val dbState: StateFlow<DatabaseState> = _dbState.asStateFlow()

    private val _chats = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chats: StateFlow<List<ChatSummary>> = _chats.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyGroups = MutableStateFlow(false)
    val onlyGroups: StateFlow<Boolean> = _onlyGroups.asStateFlow()

    private val _selectedChat = MutableStateFlow<ChatSummary?>(null)
    val selectedChat: StateFlow<ChatSummary?> = _selectedChat.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoadingMessages = MutableStateFlow(false)
    val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()

    private val _selectedChatIdsForExport = MutableStateFlow<Set<Long>>(emptySet())
    val selectedChatIdsForExport: StateFlow<Set<Long>> = _selectedChatIdsForExport.asStateFlow()

    private val _inventoryExportState = MutableStateFlow<InventoryExportState>(InventoryExportState.Idle)
    val inventoryExportState: StateFlow<InventoryExportState> = _inventoryExportState.asStateFlow()

    fun loadDatabase(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _dbState.value = DatabaseState.Loading
            try {
                databaseSource?.close()
                val reader = AndroidWhatsAppDatabaseReader.open(file)
                databaseSource = reader
                _dbState.value = DatabaseState.Ready(file)
                refreshChats()
            } catch (e: Exception) {
                _dbState.value = DatabaseState.Error(e.localizedMessage ?: "Failed to open SQLite database")
            }
        }
    }

    fun refreshChats() {
        val reader = databaseSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val list = reader.listChats(
                query = _searchQuery.value.takeIf { it.isNotBlank() },
                onlyGroups = _onlyGroups.value,
                limit = 500
            )
            _chats.value = list
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        refreshChats()
    }

    fun onFilterGroupsChanged(onlyGroups: Boolean) {
        _onlyGroups.value = onlyGroups
        refreshChats()
    }

    fun selectChat(chat: ChatSummary) {
        _selectedChat.value = chat
        val reader = databaseSource ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMessages.value = true
            try {
                // Fetch up to 1,000 messages for fast rendering
                val msgs = reader.getChatMessages(chat.id, limit = 1000, offset = 0)
                _messages.value = msgs
            } finally {
                _isLoadingMessages.value = false
            }
        }
    }

    fun clearSelectedChat() {
        _selectedChat.value = null
        _messages.value = emptyList()
    }

    fun toggleChatSelection(chatId: Long) {
        val current = _selectedChatIdsForExport.value.toMutableSet()
        if (current.contains(chatId)) {
            current.remove(chatId)
        } else {
            current.add(chatId)
        }
        _selectedChatIdsForExport.value = current
    }

    fun selectAllChats() {
        _selectedChatIdsForExport.value = _chats.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedChatIdsForExport.value = emptySet()
    }

    suspend fun exportChat(
        chat: ChatSummary,
        format: ExportFormat,
        outputStream: OutputStream
    ) = withContext(Dispatchers.IO) {
        val reader = databaseSource ?: throw IllegalStateException("Database not open")
        val messagesSeq = reader.getAllChatMessages(chat.id)

        when (format) {
            ExportFormat.TXT -> {
                TxtChatExporter().exportChat(chat, messagesSeq, outputStream)
            }
            ExportFormat.JSON -> {
                JsonChatExporter().exportChat(chat, messagesSeq, outputStream)
            }
        }
    }

    fun exportMasterPropertyInventory(context: Context, months: Long = 1, onShareReady: (File) -> Unit) {
        val sourceFile = (_dbState.value as? DatabaseState.Ready)?.file ?: return
        if (inventoryJob?.isActive == true) return
        require(months > 0)
        val appContext = context.applicationContext
        inventoryJob = viewModelScope.launch(Dispatchers.IO) {
            _inventoryExportState.value = InventoryExportState.Processing(0)
            val scratch = File(appContext.cacheDir, "inventory-work-${java.util.UUID.randomUUID()}")
            try {
                check(scratch.mkdirs()) { "Cannot create export working directory" }
                // A dedicated reader cannot be closed by chat navigation or refresh.
                AndroidWhatsAppDatabaseReader.open(sourceFile).use { reader ->
                    val latest = reader.latestBackupTimestamp() ?: error("No messages found in this backup")
                    val cutoff = Instant.ofEpochMilli(latest).atZone(ZoneId.systemDefault())
                        .toLocalDate().minusMonths(months).withDayOfMonth(1)
                    val cutoffMs = cutoff.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val cacheDir = File(appContext.noBackupFilesDir, "inventory_cache").apply { mkdirs() }
                    val exportDir = File(appContext.cacheDir, "shared_exports").apply { mkdirs() }
                    val exportFile = File(exportDir, "Master Important Dealer Inventory ${LocalDate.now()} ${months}m.csv")
                    val staged = File(scratch, "inventory.csv")
                    AndroidInventorySql(File(scratch, "listings.db")).use { work ->
                        AndroidInventorySql(File(cacheDir, "parsed-text.db")).use { cache ->
                            StreamingInventoryStore(work, cache).use { store ->
                                var groupDetail = ""
                                reader.scanInventoryMessages(cutoffMs, { done, total, name ->
                                    coroutineContext.ensureActive()
                                    groupDetail = "Groups: $done / $total • Since $cutoff\n$name"
                                    _inventoryExportState.value = InventoryExportState.Processing(store.processed, groupDetail)
                                }) { message ->
                                    coroutineContext.ensureActive()
                                    store.add(message)
                                    if (store.processed % 500 == 0) {
                                        _inventoryExportState.value = InventoryExportState.Processing(store.processed,
                                            "$groupDetail\nListings: ${store.extracted} • Reused texts: ${store.reused}")
                                    }
                                }
                                _inventoryExportState.value = InventoryExportState.Processing(store.processed, "Preparing deduplicated inventory…")
                                val totals = store.prepare()
                                var written = 0
                                FileOutputStream(staged).use { output ->
                                    InventoryCsvWriter.writeCsv(output) { emit ->
                                        store.forEachRow { row ->
                                            coroutineContext.ensureActive()
                                            emit(row)
                                            written++
                                            if (written % 500 == 0) _inventoryExportState.value = InventoryExportState.Processing(
                                                store.processed, "Writing CSV: $written / ${totals.rows} rows")
                                        }
                                    }
                                }
                                coroutineContext.ensureActive()
                                java.nio.file.Files.move(staged.toPath(), exportFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                _inventoryExportState.value = InventoryExportState.Complete(exportFile, totals.rows, totals.matched)
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { onShareReady(exportFile) }
                }
            } catch (e: CancellationException) {
                _inventoryExportState.value = InventoryExportState.Idle
                throw e
            } catch (e: Exception) {
                _inventoryExportState.value = InventoryExportState.Error(e.localizedMessage ?: "Failed to export property inventory")
            } finally {
                scratch.deleteRecursively()
            }
        }
    }

    fun dismissInventoryExportDialog() {
        _inventoryExportState.value = InventoryExportState.Idle
    }

    fun purgeDecryptedData(databaseFile: File): Boolean {
        if (inventoryJob?.isActive == true) return false
        File(databaseFile.parentFile, "inventory_cache").deleteRecursively()
        databaseSource?.close()
        databaseSource = null
        _dbState.value = DatabaseState.Closed
        _chats.value = emptyList()
        _messages.value = emptyList()
        _selectedChat.value = null
        _selectedChatIdsForExport.value = emptySet()

        return try {
            if (databaseFile.exists()) {
                // Overwrite with zeroes before deleting if possible, or standard delete
                databaseFile.delete()
            } else true
        } catch (_: Exception) {
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        databaseSource?.close()
        databaseSource = null
    }
}
