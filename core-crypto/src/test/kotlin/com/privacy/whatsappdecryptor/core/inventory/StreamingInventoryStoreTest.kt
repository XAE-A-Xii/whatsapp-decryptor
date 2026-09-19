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

        // Reopen same database file to ensure no "table listings already exists" error
        runStore("projects") { store ->
            val totals = store.prepare()
            assertEquals(4, totals.rows)
            val summaries = store.getProjectSummaries()
            assertTrue(summaries.isNotEmpty())
        }
    }

    @Test fun `CRUD operations on IN listings reflect in queries and exports`() {
        val text1 = "M3M Capital\nAvailable for sale\n3 BHK\n1800 sqft\n2.5 cr"
        val text2 = "Smart World DXP\nFresh unit\n4 BHK\n2800 sqft\n4.2 cr"
        val text3 = "Random Unregistered Society\n2 BHK\n900 sqft\n1.1 cr"

        runStore("crud_test") { store ->
            store.add(RawMessage(100, "DealerA", text1))
            store.add(RawMessage(200, "DealerB", text2))
            store.add(RawMessage(300, "DealerC", text3))
            store.prepare()

            // 1. Initial IN listings: only text1 (M3M Capital) and text2 (Smart World DXP)
            val initialIn = store.getInListings()
            assertEquals(2, initialIn.size)

            // 2. Society filtering
            val m3mOnly = store.getInListings(societyFilter = "M3M CAPITAL")
            assertEquals(1, m3mOnly.size)
            assertEquals("DealerA", m3mOnly[0].row.dealerName)

            // 3. Search query
            val searchDealerB = store.getInListings(searchQuery = "DealerB")
            assertEquals(1, searchDealerB.size)
            assertEquals("SMART WORLD DXP", searchDealerB[0].row.society)

            // 4. Update listing: change price and dealer name on M3M Capital
            val m3mListing = m3mOnly[0]
            val updatedM3mRow = m3mListing.row.copy(
                price = "2.85 Cr",
                dealerName = "DealerA Prime"
            )
            store.updateListing(m3mListing.id, updatedM3mRow)

            val updatedIn = store.getInListings(societyFilter = "M3M CAPITAL")
            assertEquals(1, updatedIn.size)
            assertEquals("2.85 Cr", updatedIn[0].row.price)
            assertEquals("DealerA Prime", updatedIn[0].row.dealerName)

            // Verify project summary reflects updated dealer
            val summariesAfterUpdate = store.getProjectSummaries()
            val m3mSummary = summariesAfterUpdate.find { it.society == "M3M CAPITAL" }
            assertNotNull(m3mSummary)
            assertEquals(1, m3mSummary.totalListings)

            // 5. Add a new manual listing
            val newManualListing = ImportantDealerRow(
                society = "M3M CAPITAL",
                projectListStatus = "IN",
                sec = "113",
                area = "2200 sqft",
                acco = "4 BHK",
                floor = "15th",
                flatNo = "1502",
                dealerName = "Agent Direct",
                phoneNo = "9876543210",
                price = "3.2 Cr",
                fullMessage = "Direct owner unit at M3M Capital",
                isDuplicate = false
            )
            val newId = store.addListing(newManualListing)
            assertNotNull(newId)

            val m3mAfterAdd = store.getInListings(societyFilter = "M3M CAPITAL")
            assertEquals(2, m3mAfterAdd.size)

            val summariesAfterAdd = store.getProjectSummaries()
            val m3mSummaryAfterAdd = summariesAfterAdd.find { it.society == "M3M CAPITAL" }
            assertNotNull(m3mSummaryAfterAdd)
            assertEquals(2, m3mSummaryAfterAdd.totalListings)

            // Verify single project export contains the added listing
            val exportedRows = mutableListOf<ImportantDealerRow>()
            store.forEachRowForProject("M3M CAPITAL") { exportedRows.add(it) }
            assertEquals(2, exportedRows.size)
            assertTrue(exportedRows.any { it.dealerName == "Agent Direct" && it.price == "3.2 Cr" })

            // 6. Delete listing: delete the original listing
            store.deleteListing(m3mListing.id)
            val m3mAfterDelete = store.getInListings(societyFilter = "M3M CAPITAL")
            assertEquals(1, m3mAfterDelete.size)
            assertEquals("Agent Direct", m3mAfterDelete[0].row.dealerName)

            val summariesAfterDelete = store.getProjectSummaries()
            val m3mSummaryAfterDelete = summariesAfterDelete.find { it.society == "M3M CAPITAL" }
            assertNotNull(m3mSummaryAfterDelete)
            assertEquals(1, m3mSummaryAfterDelete.totalListings)

            // 7. Verify getCuratedInProjectSummaries() returns all 54 canonical projects
            val curated = store.getCuratedInProjectSummaries()
            assertEquals(ProjectRegistry.SELECTED_PROJECT_NAMES.size, curated.size)
            assertEquals(54, curated.size)
            assertTrue(curated.all { it.status == "IN" })

            val m3mCurated = curated.find { it.society == "M3M CAPITAL" }
            assertNotNull(m3mCurated)
            assertEquals(1, m3mCurated.totalListings)

            val smartWorldCurated = curated.find { it.society == "SMART WORLD DXP" }
            assertNotNull(smartWorldCurated)
            assertEquals(1, smartWorldCurated.totalListings)

            val emptyCurated = curated.find { it.society == "SOBHA VILLA" }
            assertNotNull(emptyCurated)
            assertEquals(0, emptyCurated.totalListings)
            assertEquals(0, emptyCurated.uniqueDealers)
        }
    }
}
