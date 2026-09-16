package com.privacy.whatsappdecryptor.core.database

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.database.model.ContactIdentifier
import com.privacy.whatsappdecryptor.core.database.model.WhatsAppMessageType
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class ModernCrypt15Adapter : WhatsAppSchemaAdapter {

    override val versionTag: String = "ModernCrypt15 (chat + message + jid)"

    override fun canHandle(tables: Set<String>, messageColumns: Set<String>): Boolean {
        val hasCoreTables = tables.contains("chat") && tables.contains("message") && tables.contains("jid")
        val hasCoreColumns = messageColumns.contains("text_data") &&
                messageColumns.contains("chat_row_id") &&
                messageColumns.contains("timestamp")
        return hasCoreTables && hasCoreColumns
    }

    override fun listChats(
        conn: Connection,
        query: String?,
        onlyGroups: Boolean,
        limit: Int,
        offset: Int
    ): List<ChatSummary> {
        val hasFilter = !query.isNullOrBlank()
        val filterPattern = if (hasFilter) "%${query?.trim()}%" else "%"

        val sql = """
            SELECT 
                c._id, 
                j.raw_string, 
                c.subject, 
                j.server,
                c.sort_timestamp,
                COALESCE(c.archived, 0) as is_archived
            FROM chat c
            JOIN jid j ON c.jid_row_id = j._id
            WHERE (? = 0 OR (c.subject LIKE ? OR j.raw_string LIKE ?))
              AND (? = 0 OR j.server = 'g.us')
            ORDER BY c.sort_timestamp DESC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val results = mutableListOf<ChatSummary>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setInt(1, if (hasFilter) 1 else 0)
            stmt.setString(2, filterPattern)
            stmt.setString(3, filterPattern)
            stmt.setInt(4, if (onlyGroups) 1 else 0)
            stmt.setInt(5, limit)
            stmt.setInt(6, offset)

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    val id = rs.getLong("_id")
                    val rawJid = rs.getString("raw_string") ?: ""
                    val subject = rs.getString("subject")
                    val server = rs.getString("server") ?: ""
                    val sortTs = rs.getLong("sort_timestamp")
                    val isArchived = rs.getInt("is_archived") == 1
                    val isGroup = server == "g.us"

                    val title = deriveChatTitle(subject, rawJid, isGroup)

                    results.add(
                        ChatSummary(
                            id = id,
                            rawJid = rawJid,
                            title = title,
                            isGroup = isGroup,
                            messageCount = 0L, // Loaded on-demand via countMessages(id)
                            lastMessageTimestampMs = if (sortTs > 0) sortTs else null,
                            isArchived = isArchived
                        )
                    )
                }
            }
        }
        return results
    }

    override fun getChatMessages(
        conn: Connection,
        chatId: Long,
        limit: Int,
        offset: Int
    ): List<ChatMessage> {
        val sql = """
            SELECT 
                m._id, 
                m.key_id, 
                m.timestamp, 
                m.from_me, 
                m.message_type, 
                m.text_data,
                sender_j.raw_string AS sender_raw_jid,
                chat_j.raw_string AS chat_raw_jid
            FROM message m
            LEFT JOIN jid sender_j ON m.sender_jid_row_id = sender_j._id
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_j ON c.jid_row_id = chat_j._id
            WHERE m.chat_row_id = ?
            ORDER BY m.timestamp ASC, m._id ASC
            LIMIT ? OFFSET ?
        """.trimIndent()

        val list = mutableListOf<ChatMessage>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.setInt(2, limit)
            stmt.setInt(3, offset)

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(mapMessageRow(rs))
                }
            }
        }
        return list
    }

    override fun getAllChatMessages(conn: Connection, chatId: Long): Sequence<ChatMessage> = sequence {
        val sql = """
            SELECT 
                m._id, 
                m.key_id, 
                m.timestamp, 
                m.from_me, 
                m.message_type, 
                m.text_data,
                sender_j.raw_string AS sender_raw_jid,
                chat_j.raw_string AS chat_raw_jid
            FROM message m
            LEFT JOIN jid sender_j ON m.sender_jid_row_id = sender_j._id
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_j ON c.jid_row_id = chat_j._id
            WHERE m.chat_row_id = ?
            ORDER BY m.timestamp ASC, m._id ASC
        """.trimIndent()

        val stmt = conn.prepareStatement(sql)
        val rs = stmt.executeQuery()

        try {
            while (rs.next()) {
                yield(mapMessageRow(rs))
            }
        } finally {
            rs.close()
            stmt.close()
        }
    }

    override fun searchMessages(
        conn: Connection,
        searchQuery: String,
        chatId: Long?,
        limit: Int
    ): List<ChatMessage> {
        val sql = """
            SELECT 
                m._id, 
                m.key_id, 
                m.timestamp, 
                m.from_me, 
                m.message_type, 
                m.text_data,
                sender_j.raw_string AS sender_raw_jid,
                chat_j.raw_string AS chat_raw_jid
            FROM message m
            LEFT JOIN jid sender_j ON m.sender_jid_row_id = sender_j._id
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_j ON c.jid_row_id = chat_j._id
            WHERE m.text_data LIKE ?
              AND (? IS NULL OR m.chat_row_id = ?)
            ORDER BY m.timestamp DESC
            LIMIT ?
        """.trimIndent()

        val results = mutableListOf<ChatMessage>()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, "%${searchQuery.trim()}%")
            if (chatId != null) {
                stmt.setLong(2, chatId)
                stmt.setLong(3, chatId)
            } else {
                stmt.setNull(2, java.sql.Types.BIGINT)
                stmt.setNull(3, java.sql.Types.BIGINT)
            }
            stmt.setInt(4, limit)

            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    results.add(mapMessageRow(rs))
                }
            }
        }
        return results
    }

    override fun countMessages(conn: Connection, chatId: Long): Long {
        val sql = "SELECT COUNT(*) FROM message WHERE chat_row_id = ?"
        conn.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, chatId)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    private fun mapMessageRow(rs: ResultSet): ChatMessage {
        val id = rs.getLong("_id")
        val keyId = rs.getString("key_id") ?: id.toString()
        val timestamp = rs.getLong("timestamp")
        val fromMe = rs.getInt("from_me") == 1
        val messageTypeCode = rs.getInt("message_type")
        val textData = rs.getString("text_data")
        val senderRawJid = rs.getString("sender_raw_jid")
        val chatRawJid = rs.getString("chat_raw_jid")

        val effectiveSenderJid = when {
            fromMe -> "me"
            !senderRawJid.isNullOrBlank() -> senderRawJid
            !chatRawJid.isNullOrBlank() -> chatRawJid
            else -> "unknown"
        }

        val sender = if (fromMe) {
            ContactIdentifier(rawJid = "me", resolvedDisplayName = "You")
        } else {
            ContactIdentifier.fromJid(effectiveSenderJid)
        }

        val type = WhatsAppMessageType.fromCode(messageTypeCode)
        val metadataSummary = if (type != WhatsAppMessageType.TEXT) "[${type.label}]" else null

        return ChatMessage(
            id = id,
            messageKeyId = keyId,
            timestampMs = timestamp,
            sender = sender,
            isFromMe = fromMe,
            text = textData,
            messageType = type,
            metadataSummary = metadataSummary
        )
    }

    private fun deriveChatTitle(subject: String?, rawJid: String, isGroup: Boolean): String {
        if (!subject.isNullOrBlank()) return subject
        val user = rawJid.substringBefore("@")
        if (isGroup) return "Group $user"
        return if (user.all { it.isDigit() }) "+$user" else user
    }
}
