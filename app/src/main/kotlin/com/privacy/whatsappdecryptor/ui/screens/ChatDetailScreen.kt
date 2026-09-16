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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault())

private val DateDividerFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
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
                            fontWeight = FontWeight.SemiBold,
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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

    // WhatsApp bubble styling
    val bubbleColor = if (isMe) {
        // WhatsApp outgoing green
        Color(0xFF005C4B)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val shape = if (isMe) {
        RoundedCornerShape(topStart = 14.dp, topEnd = 2.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    } else {
        RoundedCornerShape(topStart = 2.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
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
            modifier = Modifier.widthIn(min = 60.dp, max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                // In groups, show sender name for incoming messages
                if (isGroup && !isMe) {
                    Text(
                        text = message.sender.displayLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                // Non-text message type indicator badge
                if (message.messageType != WhatsAppMessageType.TEXT) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "[${message.messageType.label}]",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor.copy(alpha = 0.9f),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Message text content
                if (!message.text.isNullOrBlank()) {
                    Text(
                        text = message.text,
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
                            .padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
