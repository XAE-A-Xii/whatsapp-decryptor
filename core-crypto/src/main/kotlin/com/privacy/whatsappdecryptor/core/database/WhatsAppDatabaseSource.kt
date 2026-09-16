package com.privacy.whatsappdecryptor.core.database

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.io.Closeable

interface WhatsAppDatabaseSource : Closeable {
    fun listChats(
        query: String? = null,
        onlyGroups: Boolean = false,
        limit: Int = 100,
        offset: Int = 0
    ): List<ChatSummary>

    fun getChatMessages(
        chatId: Long,
        limit: Int = 100,
        offset: Int = 0
    ): List<ChatMessage>

    fun getAllChatMessages(chatId: Long): Sequence<ChatMessage>

    fun searchMessages(
        searchQuery: String,
        chatId: Long? = null,
        limit: Int = 50
    ): List<ChatMessage>

    fun countMessages(chatId: Long): Long

    fun getRecentGroupTextMessages(sinceTimestampMs: Long): Sequence<com.privacy.whatsappdecryptor.core.inventory.RawMessage>
}
