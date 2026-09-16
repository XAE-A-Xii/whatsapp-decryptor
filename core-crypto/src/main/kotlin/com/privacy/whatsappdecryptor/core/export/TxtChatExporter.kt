package com.privacy.whatsappdecryptor.core.export

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TxtChatExporter : ChatExporter {

    override val fileExtension: String = "txt"
    override val mimeType: String = "text/plain"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override fun exportChat(
        chat: ChatSummary,
        messages: Sequence<ChatMessage>,
        outputStream: OutputStream
    ) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        val nowStr = dateFormatter.format(Instant.now())

        writer.write("=== WhatsApp Chat Export: ${chat.title} ===\n")
        writer.write("Chat JID: ${chat.rawJid} | Type: ${if (chat.isGroup) "Group" else "Direct Chat"}\n")
        writer.write("Exported: $nowStr\n")
        writer.write("-".repeat(80) + "\n\n")

        for (msg in messages) {
            val tsStr = if (msg.timestampMs > 0) {
                dateFormatter.format(Instant.ofEpochMilli(msg.timestampMs))
            } else {
                "Unknown Time"
            }

            val senderLabel = if (msg.isFromMe) "You" else msg.sender.displayLabel
            val content = when {
                !msg.text.isNullOrBlank() && msg.metadataSummary != null -> "${msg.metadataSummary} ${msg.text}"
                !msg.text.isNullOrBlank() -> msg.text
                msg.metadataSummary != null -> msg.metadataSummary
                else -> "[Empty / System Message]"
            }

            writer.write("[$tsStr] $senderLabel: $content\n")
        }

        writer.flush()
    }
}
