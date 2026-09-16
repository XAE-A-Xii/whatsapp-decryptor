package com.privacy.whatsappdecryptor.core.export

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.database.model.ContactIdentifier
import com.privacy.whatsappdecryptor.core.database.model.WhatsAppMessageType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class ChatExporterTest {

    private val sampleChat = ChatSummary(
        id = 101L,
        rawJid = "12036302918237@g.us",
        title = "Architecture Discussion",
        isGroup = true,
        messageCount = 2
    )

    private val sampleMessages = sequenceOf(
        ChatMessage(
            id = 1L,
            messageKeyId = "MSG001",
            timestampMs = 1773646800000L,
            sender = ContactIdentifier("15551234567@s.whatsapp.net", phoneNumber = "+15551234567", pushName = "Alice"),
            isFromMe = false,
            text = "Hello team, pipeline is ready.",
            messageType = WhatsAppMessageType.TEXT
        ),
        ChatMessage(
            id = 2L,
            messageKeyId = "MSG002",
            timestampMs = 1773646860000L,
            sender = ContactIdentifier("me", resolvedDisplayName = "You"),
            isFromMe = true,
            text = "Acknowledged! Sending test fixture.",
            messageType = WhatsAppMessageType.TEXT
        )
    )

    @Test
    fun `test TxtChatExporter produces human-readable transcript`() {
        val exporter = TxtChatExporter()
        val out = ByteArrayOutputStream()

        exporter.exportChat(sampleChat, sampleMessages, out)
        val text = out.toString(Charsets.UTF_8)

        assertTrue(text.contains("=== WhatsApp Chat Export: Architecture Discussion ==="))
        assertTrue(text.contains("Alice (+15551234567): Hello team, pipeline is ready."))
        assertTrue(text.contains("You: Acknowledged! Sending test fixture."))
    }

    @Test
    fun `test JsonChatExporter produces valid structured JSON`() {
        val exporter = JsonChatExporter()
        val out = ByteArrayOutputStream()

        exporter.exportChat(sampleChat, sampleMessages, out)
        val jsonString = out.toString(Charsets.UTF_8)

        assertTrue(jsonString.contains("\"exportMetadata\":"))
        assertTrue(jsonString.contains("\"chatName\": \"Architecture Discussion\""))
        assertTrue(jsonString.contains("\"messages\": ["))
        assertTrue(jsonString.contains("\"messageKeyId\": \"MSG001\""))
        assertTrue(jsonString.contains("\"messageKeyId\": \"MSG002\""))
    }
}
