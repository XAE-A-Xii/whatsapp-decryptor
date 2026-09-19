package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.database.model.WhatsAppMessageType
import com.privacy.whatsappdecryptor.ui.theme.Spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatSummary,
    messages: List<ChatMessage>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onExportChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = chat.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = chat.rawJid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onExportChat) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Chat")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages found in this chat",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            MessageBubble(message = msg, isGroup = chat.isGroup)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isGroup: Boolean
) {
    val isMe = message.isFromMe

    // WhatsApp bubble styling adhering to 60/30/10 colors
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // 8-point asymmetric bubble corner radii
    val shape = if (isMe) {
        RoundedCornerShape(
            topStart = Spacing.md,
            topEnd = Spacing.xxs,
            bottomStart = Spacing.md,
            bottomEnd = Spacing.md
        )
    } else {
        RoundedCornerShape(
            topStart = Spacing.xxs,
            topEnd = Spacing.md,
            bottomStart = Spacing.md,
            bottomEnd = Spacing.md
        )
    }

    val timeString = if (message.timestampMs > 0) {
        TimeFormatter.format(Instant.ofEpochMilli(message.timestampMs))
    } else ""

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            tonalElevation = 1.dp,
            modifier = Modifier.widthIn(min = 64.dp, max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)) {
                // In groups, show sender name for incoming messages
                if (isGroup && !isMe) {
                    Text(
                        text = message.sender.displayLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = Spacing.xxs)
                    )
                }

                // Non-text message type indicator badge
                if (message.messageType != WhatsAppMessageType.TEXT) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(Spacing.xxs),
                        modifier = Modifier.padding(bottom = Spacing.xxs)
                    ) {
                        Text(
                            text = "[${message.messageType.label}]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs)
                        )
                    }
                }

                // Message text content
                val bodyText = message.text
                if (!bodyText.isNullOrBlank()) {
                    Text(
                        text = bodyText,
                        fontSize = 15.sp,
                        color = textColor,
                        lineHeight = 20.sp
                    )
                }

                // Timestamp at bottom right
                if (timeString.isNotEmpty()) {
                    Text(
                        text = timeString,
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f),
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = Spacing.xxs)
                    )
                }
            }
        }
    }
}

