package com.privacy.whatsappdecryptor.core.inventory

import com.privacy.whatsappdecryptor.core.database.WhatsAppDatabaseReader
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import kotlin.system.measureTimeMillis

object InventoryRunner {

    @JvmStatic
    fun main(args: Array<String>) {
        val dbPath = if (args.isNotEmpty()) args[0] else "msgstore_decrypted.db"
        val outCsvPath = if (args.size > 1) args[1] else "Master_Important_Dealer_Inventory_test.csv"

        val dbFile = File(dbPath)
        println("Opening SQLite database: ${dbFile.absolutePath} (${dbFile.length() / (1024 * 1024)} MB)")

        val reader = WhatsAppDatabaseReader.open(dbFile)

        // Reference date: last 3 calendar months from latest message in backup
        // Or from today
        val threeMonthsAgo = LocalDate.now().minusMonths(3).withDayOfMonth(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        println("Fetching text messages from all group chats since: $threeMonthsAgo")

        var extractedCount = 0
        var totalRows = 0

        val elapsed = measureTimeMillis {
            val messagesSeq = reader.getRecentGroupTextMessages(threeMonthsAgo)
            val listings = PropertyListingExtractor.extractListings(messagesSeq) { progress ->
                print("\rProcessed $progress messages...")
            }
            println("\nExtracted ${listings.size} raw property listings.")

            val inventoryRows = InventoryDeduplicator.toImportantDealerRows(listings)
            println("Consolidated into ${inventoryRows.size} deduplicated inventory rows.")

            val targetInCount = inventoryRows.count { it.projectListStatus == "IN" }
            println("Matched target projects (IN): $targetInCount / ${inventoryRows.size}")

            val outFile = File(outCsvPath)
            FileOutputStream(outFile).use { fos ->
                InventoryCsvWriter.writeCsv(inventoryRows, fos)
            }
            println("Written 12-column CSV to: ${outFile.absolutePath} (${outFile.length()} bytes)")
            extractedCount = listings.size
            totalRows = inventoryRows.size
        }

        println("Completed inventory extraction and CSV generation in ${elapsed}ms!")
        reader.close()
    }
}
