package com.privacy.whatsappdecryptor.core.export

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.io.OutputStream

interface ChatExporter {
    val fileExtension: String
    val mimeType: String

    /**
     * Streams the export of a single conversation to [outputStream].
     */
    fun exportChat(
        chat: ChatSummary,
        messages: Sequence<ChatMessage>,
        outputStream: OutputStream
    )
}
