package com.privacy.whatsappdecryptor.core.export

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

@Serializable
data class ChatExportMetadata(
    val version: String = "1.0",
    val exportTimestamp: Long,
    val chatName: String,
    val chatJid: String,
    val isGroup: Boolean,
    val messageCount: Long
)

class JsonChatExporter : ChatExporter {

    override val fileExtension: String = "json"
    override val mimeType: String = "application/json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override fun exportChat(
        chat: ChatSummary,
        messages: Sequence<ChatMessage>,
        outputStream: OutputStream
    ) {
        val writer = BufferedWriter(OutputStreamWriter(outputStream, StandardCharsets.UTF_8))
        val metadata = ChatExportMetadata(
            version = "1.0",
            exportTimestamp = System.currentTimeMillis(),
            chatName = chat.title,
            chatJid = chat.rawJid,
            isGroup = chat.isGroup,
            messageCount = chat.messageCount
        )

        val metadataJson = json.encodeToString(ChatExportMetadata.serializer(), metadata)

        writer.write("{\n")
        writer.write("  \"exportMetadata\": $metadataJson,\n")
        writer.write("  \"messages\": [\n")

        var isFirst = true
        for (msg in messages) {
            if (!isFirst) {
                writer.write(",\n")
            } else {
                isFirst = false
            }

            val msgJson = json.encodeToString(ChatMessage.serializer(), msg)
            // Indent message json
            val indented = msgJson.lines().joinToString("\n") { "    $it" }
            writer.write(indented)
        }

        writer.write("\n  ]\n}\n")
        writer.flush()
    }
}
