package com.privacy.whatsappdecryptor.core.database

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.sql.Connection

/**
 * Interface contract for schema-specific WhatsApp database adapters.
 * Allows graceful adaptation to different historical or vendor-specific WhatsApp SQLite schemas.
 */
interface WhatsAppSchemaAdapter {

    val versionTag: String

    /**
     * Inspects discovered table names and column names to determine if this adapter is compatible.
     */
    fun canHandle(tables: Set<String>, messageColumns: Set<String>): Boolean

    /**
     * Lists direct chats and groups ordered by latest activity timestamp.
     */
    fun listChats(
        conn: Connection,
        query: String? = null,
        onlyGroups: Boolean = false,
        limit: Int = 100,
        offset: Int = 0
    ): List<ChatSummary>

    /**
     * Retrieves paginated messages for a given chat in chronological order.
     */
    fun getChatMessages(
        conn: Connection,
        chatId: Long,
        limit: Int = 100,
        offset: Int = 0
    ): List<ChatMessage>

    /**
     * Streams all messages for a given chat chronologically for memory-efficient exports.
     */
    fun getAllChatMessages(
        conn: Connection,
        chatId: Long
    ): Sequence<ChatMessage>

    /**
     * Searches message text across all chats or within a specific chat.
     */
    fun searchMessages(
        conn: Connection,
        searchQuery: String,
        chatId: Long? = null,
        limit: Int = 50
    ): List<ChatMessage>

    /**
     * Returns total number of messages in a given chat.
     */
    fun countMessages(conn: Connection, chatId: Long): Long
}
