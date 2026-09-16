package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ChatDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    chats: List<ChatSummary>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onlyGroups: Boolean,
    onFilterGroupsChanged: (Boolean) -> Unit,
    selectedChatIds: Set<Long>,
    onToggleChatSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onChatClicked: (ChatSummary) -> Unit,
    onNavigateToSettings: () -> Unit,
    onExportSelected: () -> Unit,
    onExportPropertyInventory: (Long) -> Unit,
    inventoryExportState: com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState = com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Idle,
    onDismissInventoryDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var inventoryMonths by remember { mutableStateOf(1L) }
    var isSearchActive by remember { mutableStateOf(false) }
    val isMultiSelectMode = selectedChatIds.isNotEmpty()

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = { Text("${selectedChatIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = onSelectAll) {
                            Text("Select All", color = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onExportSelected) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export Selected")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        if (isSearchActive) {
                            TextField(
                                value = searchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = { Text("Search chats or numbers...") },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text("Decrypted Chats", fontWeight = FontWeight.SemiBold)
                        }
                    },
                    actions = {
                        if (isSearchActive) {
                            IconButton(onClick = {
                                isSearchActive = false
                                onSearchQueryChanged("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Close search")
                            }
                        } else {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings & Privacy")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(visible = isMultiSelectMode) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedChatIds.size} chats marked for export",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onExportSelected,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export")
                        }
                    }
                }
            }
        }
        // Inventory Export Status Dialogs
        when (val state = inventoryExportState) {
            is com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Processing -> {
                AlertDialog(
                    onDismissRequest = {},
                    icon = { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) },
                    title = { Text("Extracting Property Inventory", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("${state.detail}\n\nProcessed: ${state.processedMessages} messages")
                    },
                    confirmButton = {}
                )
            }
            is com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Complete -> {
                AlertDialog(
                    onDismissRequest = onDismissInventoryDialog,
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
                    title = { Text("Inventory Export Ready!", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Successfully extracted ${state.totalRows} property listings (${state.targetInCount} in target projects).\n\nOpening share sheet for WhatsApp...")
                    },
                    confirmButton = {
                        Button(onClick = onDismissInventoryDialog) {
                            Text("OK")
                        }
                    }
                )
            }
            is com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Error -> {
                AlertDialog(
                    onDismissRequest = onDismissInventoryDialog,
                    icon = { Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    title = { Text("Export Failed", fontWeight = FontWeight.Bold) },
                    text = { Text(state.message) },
                    confirmButton = {
                        Button(onClick = onDismissInventoryDialog) {
                            Text("Dismiss")
                        }
                    }
                )
            }
            com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Idle -> {}
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Master Property Inventory Hero Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Master Property Inventory",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "All group chats • Text only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Extract deduplicated dealer inventory into the 12-column Excel CSV and share directly to WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1L, 3L).forEach { months ->
                            FilterChip(
                                selected = inventoryMonths == months,
                                onClick = { inventoryMonths = months },
                                label = { Text(if (months == 1L) "1 month" else "3 months") }
                            )
                        }
                    }
                    Text(if (inventoryMonths == 1L) "From the first day of the previous month in this backup." else "From the first day three months before this backup’s latest message.",
                        style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { onExportPropertyInventory(inventoryMonths) },
                        enabled = inventoryExportState !is com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Processing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("⚡ Export & Share via WhatsApp", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !onlyGroups,
                    onClick = { onFilterGroupsChanged(false) },
                    label = { Text("All Chats") },
                    shape = RoundedCornerShape(20.dp)
                )
                FilterChip(
                    selected = onlyGroups,
                    onClick = { onFilterGroupsChanged(true) },
                    label = { Text("Groups Only") },
                    leadingIcon = if (onlyGroups) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(20.dp)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "No chats match \"$searchQuery\"" else "No chats available",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(chats, key = { it.id }) { chat ->
                        val isSelected = selectedChatIds.contains(chat.id)

                        ChatListItem(
                            chat = chat,
                            isSelected = isSelected,
                            isMultiSelectMode = isMultiSelectMode,
                            onClick = {
                                if (isMultiSelectMode) {
                                    onToggleChatSelection(chat.id)
                                } else {
                                    onChatClicked(chat)
                                }
                            },
                            onLongClick = {
                                onToggleChatSelection(chat.id)
                            }
                        )
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            modifier = Modifier.padding(start = 76.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: ChatSummary,
    isSelected: Boolean,
    isMultiSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = 12.dp)
            )
        }

        // Avatar
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (chat.isGroup) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (chat.isGroup) Icons.Default.Groups else Icons.Default.Person,
                contentDescription = null,
                tint = if (chat.isGroup) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (chat.lastMessageTimestampMs != null) {
                    val dateText = ChatDateFormatter.format(Instant.ofEpochMilli(chat.lastMessageTimestampMs))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = chat.rawJid,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
