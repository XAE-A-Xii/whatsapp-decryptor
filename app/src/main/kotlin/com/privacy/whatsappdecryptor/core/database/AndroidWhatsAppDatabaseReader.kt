package com.privacy.whatsappdecryptor.core.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import com.privacy.whatsappdecryptor.core.database.model.ContactIdentifier
import com.privacy.whatsappdecryptor.core.database.model.WhatsAppMessageType
import java.io.File

/**
 * High-performance, zero-dependency Android-native SQLite database reader for WhatsApp msgstore databases.
 * Uses Android's built-in [SQLiteDatabase] (C++ native engine) in strictly read-only mode.
 */
class AndroidWhatsAppDatabaseReader private constructor(
    private val db: SQLiteDatabase
) : WhatsAppDatabaseSource {

    override fun listChats(
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

        val selectionArgs = arrayOf(
            if (hasFilter) "1" else "0",
            filterPattern,
            filterPattern,
            if (onlyGroups) "1" else "0",
            limit.toString(),
            offset.toString()
        )

        val results = mutableListOf<ChatSummary>()
        db.rawQuery(sql, selectionArgs).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val rawIdx = cursor.getColumnIndexOrThrow("raw_string")
            val subjectIdx = cursor.getColumnIndexOrThrow("subject")
            val serverIdx = cursor.getColumnIndexOrThrow("server")
            val sortTsIdx = cursor.getColumnIndexOrThrow("sort_timestamp")
            val archivedIdx = cursor.getColumnIndexOrThrow("is_archived")

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val rawJid = cursor.getString(rawIdx) ?: ""
                val subject = if (cursor.isNull(subjectIdx)) null else cursor.getString(subjectIdx)
                val server = cursor.getString(serverIdx) ?: ""
                val sortTs = cursor.getLong(sortTsIdx)
                val isArchived = cursor.getInt(archivedIdx) == 1
                val isGroup = server == "g.us"

                val title = deriveChatTitle(subject, rawJid, isGroup)

                results.add(
                    ChatSummary(
                        id = id,
                        rawJid = rawJid,
                        title = title,
                        isGroup = isGroup,
                        messageCount = 0L,
                        lastMessageTimestampMs = if (sortTs > 0) sortTs else null,
                        isArchived = isArchived
                    )
                )
            }
        }
        return results
    }

    override fun getChatMessages(
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

        val selectionArgs = arrayOf(chatId.toString(), limit.toString(), offset.toString())
        val list = mutableListOf<ChatMessage>()

        db.rawQuery(sql, selectionArgs).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val keyIdIdx = cursor.getColumnIndexOrThrow("key_id")
            val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
            val fromMeIdx = cursor.getColumnIndexOrThrow("from_me")
            val typeIdx = cursor.getColumnIndexOrThrow("message_type")
            val textIdx = cursor.getColumnIndexOrThrow("text_data")
            val senderJidIdx = cursor.getColumnIndexOrThrow("sender_raw_jid")
            val chatJidIdx = cursor.getColumnIndexOrThrow("chat_raw_jid")

            while (cursor.moveToNext()) {
                list.add(mapMessageRow(cursor, idIdx, keyIdIdx, tsIdx, fromMeIdx, typeIdx, textIdx, senderJidIdx, chatJidIdx))
            }
        }
        return list
    }

    override fun getAllChatMessages(chatId: Long): Sequence<ChatMessage> = sequence {
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

        val cursor = db.rawQuery(sql, arrayOf(chatId.toString()))
        try {
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val keyIdIdx = cursor.getColumnIndexOrThrow("key_id")
            val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
            val fromMeIdx = cursor.getColumnIndexOrThrow("from_me")
            val typeIdx = cursor.getColumnIndexOrThrow("message_type")
            val textIdx = cursor.getColumnIndexOrThrow("text_data")
            val senderJidIdx = cursor.getColumnIndexOrThrow("sender_raw_jid")
            val chatJidIdx = cursor.getColumnIndexOrThrow("chat_raw_jid")

            while (cursor.moveToNext()) {
                yield(mapMessageRow(cursor, idIdx, keyIdIdx, tsIdx, fromMeIdx, typeIdx, textIdx, senderJidIdx, chatJidIdx))
            }
        } finally {
            cursor.close()
        }
    }

    override fun searchMessages(
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
              AND (? = -1 OR m.chat_row_id = ?)
            ORDER BY m.timestamp DESC
            LIMIT ?
        """.trimIndent()

        val selectionArgs = arrayOf(
            "%${searchQuery.trim()}%",
            (chatId ?: -1L).toString(),
            (chatId ?: -1L).toString(),
            limit.toString()
        )

        val results = mutableListOf<ChatMessage>()
        db.rawQuery(sql, selectionArgs).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val keyIdIdx = cursor.getColumnIndexOrThrow("key_id")
            val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
            val fromMeIdx = cursor.getColumnIndexOrThrow("from_me")
            val typeIdx = cursor.getColumnIndexOrThrow("message_type")
            val textIdx = cursor.getColumnIndexOrThrow("text_data")
            val senderJidIdx = cursor.getColumnIndexOrThrow("sender_raw_jid")
            val chatJidIdx = cursor.getColumnIndexOrThrow("chat_raw_jid")

            while (cursor.moveToNext()) {
                results.add(mapMessageRow(cursor, idIdx, keyIdIdx, tsIdx, fromMeIdx, typeIdx, textIdx, senderJidIdx, chatJidIdx))
            }
        }
        return results
    }

    override fun countMessages(chatId: Long): Long {
        val sql = "SELECT COUNT(*) FROM message WHERE chat_row_id = ?"
        db.rawQuery(sql, arrayOf(chatId.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    override fun getRecentGroupTextMessages(sinceTimestampMs: Long): Sequence<com.privacy.whatsappdecryptor.core.inventory.RawMessage> = sequence {
        val sql = """
            SELECT 
                m.timestamp, 
                COALESCE(sender_j.raw_string, chat_j.raw_string, 'Unknown') AS sender, 
                m.text_data
            FROM message m
            JOIN chat c ON m.chat_row_id = c._id
            JOIN jid chat_j ON c.jid_row_id = chat_j._id
            LEFT JOIN jid sender_j ON m.sender_jid_row_id = sender_j._id
            WHERE chat_j.server = 'g.us'
              AND c.sort_timestamp >= ?
              AND m.timestamp >= ?
              AND m.message_type = 0
              AND m.text_data IS NOT NULL
              AND length(m.text_data) > 0
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(sinceTimestampMs.toString(), sinceTimestampMs.toString()))
        try {
            val tsIdx = cursor.getColumnIndexOrThrow("timestamp")
            val senderIdx = cursor.getColumnIndexOrThrow("sender")
            val textIdx = cursor.getColumnIndexOrThrow("text_data")

            while (cursor.moveToNext()) {
                val ts = cursor.getLong(tsIdx)
                val sender = cursor.getString(senderIdx) ?: "Unknown"
                val text = cursor.getString(textIdx) ?: ""
                yield(com.privacy.whatsappdecryptor.core.inventory.RawMessage(ts, sender, text))
            }
        } finally {
            cursor.close()
        }
    }

    fun latestBackupTimestamp(): Long? =
        db.rawQuery("SELECT timestamp FROM message ORDER BY _id DESC LIMIT 1", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

    /** Scoped callbacks close both cursors on cancellation or write failures. */
    fun scanInventoryMessages(
        sinceTimestampMs: Long,
        onGroup: (completed: Int, total: Int, name: String) -> Unit,
        consume: (com.privacy.whatsappdecryptor.core.inventory.RawMessage) -> Unit
    ) {
        val groups = mutableListOf<Pair<Long, String>>()
        db.rawQuery("""SELECT c._id, COALESCE(c.subject, j.raw_string, 'Group')
            FROM chat c JOIN jid j ON c.jid_row_id=j._id
            WHERE j.server='g.us' AND c.sort_timestamp >= ?""",
            arrayOf(sinceTimestampMs.toString())).use { cursor ->
            while (cursor.moveToNext()) groups.add(cursor.getLong(0) to cursor.getString(1))
        }
        for ((index, group) in groups.withIndex()) {
            onGroup(index, groups.size, group.second)
            db.rawQuery("""SELECT m.timestamp, COALESCE(j.raw_string, 'Unknown'), m.text_data
                FROM message m LEFT JOIN jid j ON m.sender_jid_row_id=j._id
                WHERE m.chat_row_id=? AND m.timestamp>=? AND m.message_type=0
                AND m.text_data IS NOT NULL AND length(m.text_data)>0""",
                arrayOf(group.first.toString(), sinceTimestampMs.toString())).use { cursor ->
                while (cursor.moveToNext()) {
                    consume(com.privacy.whatsappdecryptor.core.inventory.RawMessage(
                        cursor.getLong(0), cursor.getString(1), cursor.getString(2)))
                }
            }
        }
        onGroup(groups.size, groups.size, "")
    }

    override fun close() {
        if (db.isOpen) {
            db.close()
        }
    }

    private fun mapMessageRow(
        cursor: Cursor,
        idIdx: Int,
        keyIdIdx: Int,
        tsIdx: Int,
        fromMeIdx: Int,
        typeIdx: Int,
        textIdx: Int,
        senderJidIdx: Int,
        chatJidIdx: Int
    ): ChatMessage {
        val id = cursor.getLong(idIdx)
        val keyId = if (!cursor.isNull(keyIdIdx)) cursor.getString(keyIdIdx) else id.toString()
        val timestamp = cursor.getLong(tsIdx)
        val fromMe = cursor.getInt(fromMeIdx) == 1
        val messageTypeCode = if (!cursor.isNull(typeIdx)) cursor.getInt(typeIdx) else null
        val textData = if (!cursor.isNull(textIdx)) cursor.getString(textIdx) else null
        val senderRawJid = if (!cursor.isNull(senderJidIdx)) cursor.getString(senderJidIdx) else null
        val chatRawJid = if (!cursor.isNull(chatJidIdx)) cursor.getString(chatJidIdx) else null

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

    companion object {
        fun open(databaseFile: File): AndroidWhatsAppDatabaseReader {
            require(databaseFile.exists()) { "Database file does not exist: ${databaseFile.absolutePath}" }

            val db = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            )

            try {
                // Fast integrity check (~15ms)
                db.rawQuery("PRAGMA schema_version", null).use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getInt(0) <= 0) {
                        throw IllegalStateException("Invalid SQLite database: missing or empty schema_version")
                    }
                }
                db.rawQuery("SELECT 1 FROM sqlite_master LIMIT 1", null).use { cursor ->
                    if (!cursor.moveToFirst()) {
                        throw IllegalStateException("Invalid SQLite database: sqlite_master is unreadable")
                    }
                }

                // Verify core tables exist
                val tables = mutableSetOf<String>()
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { cursor ->
                    while (cursor.moveToNext()) {
                        tables.add(cursor.getString(0))
                    }
                }

                if (!tables.contains("chat") || !tables.contains("message") || !tables.contains("jid")) {
                    throw UnsupportedOperationException(
                        "Unsupported schema: Missing essential tables. Found: ${tables.take(10)}"
                    )
                }

                return AndroidWhatsAppDatabaseReader(db)
            } catch (e: Exception) {
                db.close()
                throw e
            }
        }
    }
}
