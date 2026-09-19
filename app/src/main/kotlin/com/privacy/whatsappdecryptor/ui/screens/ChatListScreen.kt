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
import com.privacy.whatsappdecryptor.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.absoluteValue

private val ChatDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    .withZone(ZoneId.systemDefault())

// Curated avatar palette for visual distinction
private val AvatarColors = listOf(
    Color(0xFF00796B), // Teal
    Color(0xFF303F9F), // Indigo
    Color(0xFFC2185B), // Pink
    Color(0xFFE64A19), // Deep Orange
    Color(0xFF512DA8), // Deep Purple
    Color(0xFF0097A7), // Cyan
    Color(0xFF388E3C), // Green
    Color(0xFFAFB42B), // Lime/Olive
    Color(0xFF5D4037)  // Brown
)

private fun getAvatarColor(seed: String): Color {
    val index = seed.hashCode().absoluteValue % AvatarColors.size
    return AvatarColors[index]
}

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
    onNavigateToProjects: () -> Unit = {},
    onExportPropertyInventory: (Long) -> Unit = {},
    inventoryExportState: com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState = com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Idle,
    onDismissInventoryDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isSearchActive by remember { mutableStateOf(false) }
    val isMultiSelectMode = selectedChatIds.isNotEmpty()

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedChatIds.size} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        TextButton(onClick = onSelectAll) {
                            Text(
                                "Select All",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
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
                            Text(
                                "Decrypted Chats",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
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
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
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
                            shape = RoundedCornerShape(Spacing.sm),
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Export", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
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
                    icon = {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.xl)
                        )
                    },
                    title = { Text("Inventory Export Ready!", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Successfully extracted ${state.totalRows} property listings (${state.targetInCount} in target projects).\n\nOpening share sheet for WhatsApp...")
                    },
                    confirmButton = {
                        Button(onClick = onDismissInventoryDialog, shape = RoundedCornerShape(Spacing.sm)) {
                            Text("OK")
                        }
                    }
                )
            }
            is com.privacy.whatsappdecryptor.ui.viewmodel.InventoryExportState.Error -> {
                AlertDialog(
                    onDismissRequest = onDismissInventoryDialog,
                    icon = {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(Spacing.xl)
                        )
                    },
                    title = { Text("Export Failed", fontWeight = FontWeight.Bold) },
                    text = { Text(state.message) },
                    confirmButton = {
                        Button(onClick = onDismissInventoryDialog, shape = RoundedCornerShape(Spacing.sm)) {
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
            // Quick shortcut to Project Sub-Excels Hero Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ),
                shape = RoundedCornerShape(Spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                onClick = onNavigateToProjects
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(Spacing.xl)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Domain,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "Project Sub-Excels",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Export spreadsheets for each individual project",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Open Projects",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Filter chips with 8-pt spacing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                FilterChip(
                    selected = !onlyGroups,
                    onClick = { onFilterGroupsChanged(false) },
                    label = { Text("All Chats (${chats.size})") },
                    shape = RoundedCornerShape(Spacing.xs)
                )
                FilterChip(
                    selected = onlyGroups,
                    onClick = { onFilterGroupsChanged(true) },
                    label = { Text("Groups Only") },
                    leadingIcon = if (onlyGroups) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    shape = RoundedCornerShape(Spacing.xs),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(top = Spacing.xxs)
            )

            if (chats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Spacing.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(Spacing.xxl)
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
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                            modifier = Modifier.padding(start = 72.dp)
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
    val avatarBg = remember(chat.title, chat.rawJid) {
        getAvatarColor(chat.title.ifEmpty { chat.rawJid })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                modifier = Modifier.padding(end = Spacing.sm)
            )
        }

        // Distinct Avatar with Material/Pastel tone
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(avatarBg),
            contentAlignment = Alignment.Center
        ) {
            val initial = chat.title.firstOrNull()?.uppercaseChar()
            if (chat.isGroup) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else if (initial != null && initial.isLetterOrDigit()) {
                Text(
                    text = initial.toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

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

                val lastTs = chat.lastMessageTimestampMs
                if (lastTs != null) {
                    val dateText = ChatDateFormatter.format(Instant.ofEpochMilli(lastTs))
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxs))

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

