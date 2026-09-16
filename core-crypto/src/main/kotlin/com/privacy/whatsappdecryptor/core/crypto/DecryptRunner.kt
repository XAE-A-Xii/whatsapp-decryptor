package com.privacy.whatsappdecryptor.core.crypto

import com.privacy.whatsappdecryptor.core.database.WhatsAppDatabaseReader
import java.io.File
import java.io.FileInputStream
import java.sql.DriverManager
import kotlin.system.measureTimeMillis

object DecryptRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val backupFilePath = args.getOrNull(0) ?: "msgstore.db.crypt15"
        val keyFilePath = args.getOrNull(1) ?: "backup_key.txt"
        val outputFilePath = args.getOrNull(2) ?: "msgstore_decrypted.db"

        val backupFile = File(backupFilePath)
        val keyFile = File(keyFilePath)
        val outputFile = File(outputFilePath)

        println("=== WhatsApp Crypt15 Verification Runner ===")
        println("Backup file: ${backupFile.absolutePath} (${backupFile.length() / (1024 * 1024)} MB)")
        println("Key file:    ${keyFile.absolutePath}")
        println("Output file: ${outputFile.absolutePath}")

        if (!backupFile.exists()) {
            System.err.println("ERROR: Backup file does not exist: ${backupFile.absolutePath}")
            return
        }

        if (!keyFile.exists()) {
            System.err.println("ERROR: Key file does not exist: ${keyFile.absolutePath}")
            println("Please create ${keyFile.name} and paste your 64-digit hex key inside it.")
            return
        }

        val rawKey = keyFile.readText().trim()
        if (!KeyValidator.isValidKeyFormat(rawKey)) {
            System.err.println("ERROR: Key in ${keyFile.name} is invalid. Expected 64 hexadecimal characters.")
            return
        }

        if (outputFile.exists() && outputFile.length() > 0) {
            println("Found existing decrypted database: ${outputFile.name} (${outputFile.length() / (1024 * 1024)} MB).")
            println("Skipping re-decryption and proceeding to schema inspection directly.")
            inspectDatabase(outputFile)
            return
        }

        val totalSize = backupFile.length()
        var lastPercent = -1

        val elapsedMs = measureTimeMillis {
            FileInputStream(backupFile).use { fis ->
                Crypt15StreamingDecryptor.decrypt(
                    inputStream = fis,
                    outputFile = outputFile,
                    hexKey = rawKey,
                    totalBytes = totalSize,
                    onProgress = { processed, total ->
                        val percent = if (total > 0) ((processed * 100) / total).toInt() else 0
                        if (percent != lastPercent && percent % 5 == 0) {
                            print("\rProgress: $percent% (${processed / (1024 * 1024)} MB / ${total / (1024 * 1024)} MB)")
                            System.out.flush()
                            lastPercent = percent
                        }
                    }
                )
            }
        }

        println("\n\nSUCCESS! Decryption and decompression completed in ${elapsedMs / 1000}s.")
        println("Decrypted file size: ${outputFile.length() / (1024 * 1024)} MB")

        // Inspect SQLite Schema
        inspectDatabase(outputFile)
    }

    private fun inspectDatabase(dbFile: File) {
        println("\n=== Inspecting SQLite Schema via WhatsAppDatabaseReader ===")
        try {
            WhatsAppDatabaseReader.open(dbFile).use { reader ->
                println("Adapter bound: ${reader.adapter.versionTag}")
                println("Diagnostic summary:\n${reader.detectionResult.diagnosticSummary}")

                val chats = reader.listChats(limit = 10)
                println("Top ${chats.size} Chats:")
                chats.forEachIndexed { i, chat ->
                    println("  [${i + 1}] ${chat.title} (${if (chat.isGroup) "Group" else "DM"}) | Messages: ${chat.messageCount} | JID: ${chat.rawJid}")
                }

                if (chats.isNotEmpty()) {
                    val firstChat = chats.first()
                    val messages = reader.getChatMessages(firstChat.id, limit = 5)
                    println("\nSample ${messages.size} Messages from '${firstChat.title}':")
                    messages.forEach { msg ->
                        val sender = if (msg.isFromMe) "You" else msg.sender.displayLabel
                        val content = msg.metadataSummary ?: msg.text?.take(40) ?: "[Empty]"
                        println("  [${java.time.Instant.ofEpochMilli(msg.timestampMs)}] $sender: $content")
                    }
                }
            }
        } catch (e: Exception) {
            System.err.println("Database inspection failed: ${e.message}")
            e.printStackTrace()
        }
    }
}
