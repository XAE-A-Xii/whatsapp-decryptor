package com.privacy.whatsappdecryptor.core.database

import com.privacy.whatsappdecryptor.core.database.model.ChatMessage
import com.privacy.whatsappdecryptor.core.database.model.ChatSummary
import java.io.Closeable
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class WhatsAppDatabaseReader private constructor(
    private val connection: Connection,
    val adapter: WhatsAppSchemaAdapter,
    val detectionResult: SchemaDetectionResult
) : WhatsAppDatabaseSource {

    override fun listChats(
        query: String?,
        onlyGroups: Boolean,
        limit: Int,
        offset: Int
    ): List<ChatSummary> {
        return adapter.listChats(connection, query, onlyGroups, limit, offset)
    }

    override fun getChatMessages(
        chatId: Long,
        limit: Int,
        offset: Int
    ): List<ChatMessage> {
        return adapter.getChatMessages(connection, chatId, limit, offset)
    }

    override fun getAllChatMessages(chatId: Long): Sequence<ChatMessage> {
        return adapter.getAllChatMessages(connection, chatId)
    }

    override fun searchMessages(
        searchQuery: String,
        chatId: Long?,
        limit: Int
    ): List<ChatMessage> {
        return adapter.searchMessages(connection, searchQuery, chatId, limit)
    }

    override fun countMessages(chatId: Long): Long {
        return adapter.countMessages(connection, chatId)
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

        val stmt = connection.prepareStatement(sql)
        stmt.setLong(1, sinceTimestampMs)
        stmt.setLong(2, sinceTimestampMs)
        val rs = stmt.executeQuery()

        try {
            while (rs.next()) {
                val ts = rs.getLong("timestamp")
                val sender = rs.getString("sender") ?: "Unknown"
                val text = rs.getString("text_data") ?: ""
                yield(com.privacy.whatsappdecryptor.core.inventory.RawMessage(ts, sender, text))
            }
        } finally {
            rs.close()
            stmt.close()
        }
    }

    override fun close() {
        if (!connection.isClosed) {
            connection.close()
        }
    }

    companion object {
        init {
            try {
                Class.forName("org.sqlite.JDBC")
            } catch (_: ClassNotFoundException) {
            }
        }

        /**
         * Opens [databaseFile] in strictly read-only mode, performs a fast integrity check,
         * detects the schema, and returns an initialized [WhatsAppDatabaseReader].
         */
        fun open(databaseFile: File): WhatsAppDatabaseReader {
            require(databaseFile.exists()) { "Database file does not exist: ${databaseFile.absolutePath}" }

            val url = "jdbc:sqlite:${databaseFile.absolutePath}"
            val config = org.sqlite.SQLiteConfig().apply {
                setReadOnly(true)
            }
            val conn = config.createConnection(url)

            // Refinement #5: Fast sanity check (verifies page 1 & schema tree in ~15ms)
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA schema_version").use { rs ->
                    if (!rs.next() || rs.getInt(1) <= 0) {
                        conn.close()
                        throw IllegalStateException("Invalid SQLite database: missing or empty schema_version")
                    }
                }
                stmt.executeQuery("SELECT 1 FROM sqlite_master LIMIT 1").use { rs ->
                    if (!rs.next()) {
                        conn.close()
                        throw IllegalStateException("Invalid SQLite database: sqlite_master is unreadable")
                    }
                }
            }

            val detection = SchemaDetector.detect(conn)
            if (!detection.isSupported || detection.adapter == null) {
                conn.close()
                throw UnsupportedOperationException(
                    "Unsupported WhatsApp database schema:\n${detection.diagnosticSummary}"
                )
            }

            return WhatsAppDatabaseReader(
                connection = conn,
                adapter = detection.adapter,
                detectionResult = detection
            )
        }
    }
}
