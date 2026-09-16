package com.privacy.whatsappdecryptor.core.database

import java.sql.Connection

data class SchemaDetectionResult(
    val adapter: WhatsAppSchemaAdapter?,
    val isSupported: Boolean,
    val tablesFound: Set<String>,
    val messageColumnsFound: Set<String>,
    val diagnosticSummary: String
)

object SchemaDetector {

    private val REGISTERED_ADAPTERS: List<WhatsAppSchemaAdapter> = listOf(
        ModernCrypt15Adapter()
    )

    fun detect(conn: Connection): SchemaDetectionResult {
        val tables = mutableSetOf<String>()
        val views = mutableSetOf<String>()

        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT name, type FROM sqlite_master WHERE type IN ('table', 'view')").use { rs ->
                while (rs.next()) {
                    val name = rs.getString("name").lowercase()
                    val type = rs.getString("type")
                    if (type == "view") views.add(name) else tables.add(name)
                }
            }
        }

        val messageColumns = mutableSetOf<String>()
        if (tables.contains("message")) {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(message)").use { rs ->
                    while (rs.next()) {
                        messageColumns.add(rs.getString("name").lowercase())
                    }
                }
            }
        } else if (tables.contains("messages")) {
            conn.createStatement().use { stmt ->
                stmt.executeQuery("PRAGMA table_info(messages)").use { rs ->
                    while (rs.next()) {
                        messageColumns.add(rs.getString("name").lowercase())
                    }
                }
            }
        }

        val allStructures = tables + views
        val matchedAdapter = REGISTERED_ADAPTERS.firstOrNull { it.canHandle(allStructures, messageColumns) }

        val summary = buildString {
            appendLine("WhatsApp Database Schema Inspection:")
            appendLine("  Tables: ${tables.size} found (${tables.take(8).joinToString(", ")}...)")
            appendLine("  Views: ${views.size} found")
            appendLine("  Message columns: ${messageColumns.joinToString(", ")}")
            if (matchedAdapter != null) {
                appendLine("  Status: Fully supported via ${matchedAdapter.versionTag}")
            } else {
                appendLine("  Status: Unrecognized schema variant.")
            }
        }

        return SchemaDetectionResult(
            adapter = matchedAdapter,
            isSupported = matchedAdapter != null,
            tablesFound = allStructures,
            messageColumnsFound = messageColumns,
            diagnosticSummary = summary
        )
    }
}
