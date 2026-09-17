package com.privacy.whatsappdecryptor.core.inventory

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import java.io.ByteArrayOutputStream
import kotlin.test.*

class StreamingInventoryStoreTest {
    @TempDir lateinit var directory: Path
    private class Sql(path: Path) : InventorySql {
        private val connection = DriverManager.getConnection("jdbc:sqlite:$path")
        override fun execute(sql: String, args: List<Any?>) {
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
                statement.execute()
            }
        }
        override fun <T> query(sql: String, args: List<Any?>, read: (InventoryCursor) -> T): T =
            connection.prepareStatement(sql).use { statement ->
                args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
                statement.executeQuery().use { result ->
                    read(object : InventoryCursor {
                        override fun next() = result.next()
                        override fun text(column: Int) = result.getString(column + 1)
                        override fun long(column: Int) = result.getLong(column + 1)
                    })
                }
            }
        override fun close() = connection.close()
    }
    private fun <T> runStore(name: String, block: (StreamingInventoryStore) -> T): T =
        Sql(directory.resolve("$name.db")).use { work ->
            Sql(directory.resolve("cache.db")).use { cache ->
                StreamingInventoryStore(work, cache).use(block)
            }
        }
    private val text = "M3M Capital\nAvailable for sale\n3 BHK\n1800 sqft\n2.5 cr"

    @Test fun `cache persists without leaking dealer identity`() {
        runStore("first") { store ->
            store.add(RawMessage(100, "919876543210", text))
            store.add(RawMessage(200, "919876543211", text))
            store.add(RawMessage(300, "someone", "hello"))
            assertEquals(2, store.parsed)
            assertEquals(1, store.reused)
            assertEquals(2, store.prepare().rows)
            val rows = mutableListOf<ImportantDealerRow>()
            store.forEachRow { rows.add(it) }
            assertEquals(listOf("9876543210", "9876543211"), rows.map { it.phoneNo })
            assertEquals(listOf("919876543210", "919876543211"), rows.map { it.dealerName })
        }
        runStore("second") { store ->
            store.add(RawMessage(400, "another", text))
            store.add(RawMessage(500, "another", "hello"))
            assertEquals(0, store.parsed)
            assertEquals(2, store.reused)
            assertEquals(1, store.prepare().rows)
            store.forEachRow { assertEquals("another", it.dealerName); assertEquals("", it.phoneNo) }
        }
    }

    @Test fun `earliest repost retained and timestamp ties marked consistently`() {
        runStore("ties") { store ->
            store.add(RawMessage(300, "dealer", text))
            store.add(RawMessage(100, "dealer", "$text!"))
            store.add(RawMessage(200, "dealer", text.replace("1800", "1900")))
            store.add(RawMessage(200, "dealer", text.replace("1800", "2000")))
            assertEquals(3, store.prepare().rows)
            val rows = mutableListOf<ImportantDealerRow>()
            store.forEachRow { rows.add(it) }
            assertTrue(rows.first().fullMessage.endsWith("!"))
            assertEquals(listOf(true, true, false), rows.map { it.isDuplicate })
            val streamed = ByteArrayOutputStream()
            InventoryCsvWriter.writeCsv(streamed) { emit -> store.forEachRow(emit) }
            val previousWriter = ByteArrayOutputStream()
            InventoryCsvWriter.writeCsv(rows, previousWriter)
            assertContentEquals(previousWriter.toByteArray(), streamed.toByteArray())
        }
    }

    @Test fun `empty export writes header and cache survives interrupted processing`() {
        assertFailsWith<IllegalStateException> {
            runStore("interrupted") { store ->
                store.add(RawMessage(1, "dealer", text))
                error("cancelled")
            }
        }
        runStore("resumed") { store ->
            store.add(RawMessage(1, "dealer", text))
            assertEquals(0, store.parsed)
            assertEquals(1, store.reused)
        }
        runStore("empty") { store ->
            assertEquals(InventoryTotals(0, 0), store.prepare())
            val output = ByteArrayOutputStream()
            InventoryCsvWriter.writeCsv(output) { emit -> store.forEachRow(emit) }
            assertEquals(1, output.toString("UTF-8").trim().lines().size)
        }
    }

    @Test fun `corrupted cache entry is gracefully recovered without failing export`() {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> val n = byte.toInt() and 255; "${"0123456789abcdef"[n ushr 4]}${"0123456789abcdef"[n and 15]}" }
        runStore("corrupt") { store ->
            Sql(directory.resolve("cache.db")).use { cache ->
                cache.execute("INSERT OR REPLACE INTO parsed_text VALUES (?, ?, ?)",
                    listOf(StreamingInventoryStore.CACHE_VERSION, digest, "{invalid json [10].society"))
            }
            store.add(RawMessage(100, "919876543210", text))
            assertEquals(1, store.prepare().rows)
            val rows = mutableListOf<ImportantDealerRow>()
            store.forEachRow { rows.add(it) }
            assertEquals(1, rows.size)
        }
    }

    @Test fun `project summaries and single project streaming export properly`() {
        val text1 = "M3M Capital\nAvailable for sale\n3 BHK\n1800 sqft\n2.5 cr"
        val text2 = "Smart World DXP\nFresh unit\n4 BHK\n2800 sqft\n4.2 cr"
        val text3 = "Random Unregistered Society\n2 BHK\n900 sqft\n1.1 cr"

        runStore("projects") { store ->
            store.add(RawMessage(100, "DealerA", text1))
            store.add(RawMessage(200, "DealerB", text1)) // Same project, different dealer
            store.add(RawMessage(300, "DealerC", text2)) // Target project
            store.add(RawMessage(400, "DealerD", text3)) // Non-target (OUT)

            val totals = store.prepare()
            assertEquals(4, totals.rows)

            val summaries = store.getProjectSummaries()
            assertTrue(summaries.isNotEmpty())

            // M3M Capital should have 2 listings and 2 unique dealers
            val m3mSummary = summaries.find { it.society.contains("M3M CAPITAL", ignoreCase = true) }
            assertNotNull(m3mSummary)
            assertEquals("IN", m3mSummary.status)
            assertEquals(2, m3mSummary.totalListings)
            assertEquals(2, m3mSummary.uniqueDealers)

            // Test single-project streaming
            val m3mRows = mutableListOf<ImportantDealerRow>()
            store.forEachRowForProject(m3mSummary.society) { m3mRows.add(it) }
            assertEquals(2, m3mRows.size)
            assertTrue(m3mRows.all { it.society == m3mSummary.society })

            // Test filename utility
            val fileName = InventoryCsvWriter.subExcelFileName("SMART WORLD DXP", 3)
            assertEquals("Inventory_SMART_WORLD_DXP_3m.csv", fileName)
        }
    }
}
