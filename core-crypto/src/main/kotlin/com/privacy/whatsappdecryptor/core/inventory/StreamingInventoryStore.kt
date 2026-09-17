package com.privacy.whatsappdecryptor.core.inventory

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.security.MessageDigest
import java.text.Normalizer

/** Small SQLite bridge shared by Android's native driver and JVM tests. */
interface InventorySql : Closeable {
    fun execute(sql: String, args: List<Any?> = emptyList())
    fun <T> query(sql: String, args: List<Any?> = emptyList(), read: (InventoryCursor) -> T): T
}

interface InventoryCursor {
    fun next(): Boolean
    fun text(column: Int): String
    fun long(column: Int): Long
}

@kotlinx.serialization.Serializable
data class ProjectInventorySummary(
    val society: String,
    val status: String,
    val totalListings: Int,
    val uniqueDealers: Int,
    val latestTimestamp: Long
)

data class InventoryTotals(val rows: Int, val matched: Int)

/** Only a bounded set of parsed texts lives in memory. All ads and sorting live on disk. */
class StreamingInventoryStore(private val work: InventorySql, private val cache: InventorySql) : Closeable {
    companion object {
        // Bump when property extraction, project rules, or CSV field conversion change.
        const val CACHE_VERSION = "android-inventory-v4"
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val NON_DIGIT = Regex("\\D")
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }
    private val recent = object : LinkedHashMap<String, List<ImportantDealerRow>>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ImportantDealerRow>>?) = size > 256
    }
    var processed = 0; private set
    var extracted = 0; private set
    var parsed = 0; private set
    var reused = 0; private set

    init {
        work.execute("PRAGMA temp_store=FILE")
        work.execute("PRAGMA cache_size=-8192")
        work.execute("""CREATE TABLE listings (
            dedup_key TEXT PRIMARY KEY, ts INTEGER NOT NULL, seq INTEGER NOT NULL,
            society TEXT NOT NULL, dealer TEXT NOT NULL, status TEXT NOT NULL, fields TEXT NOT NULL
        )""")
        cache.execute("""CREATE TABLE IF NOT EXISTS parsed_text (
            version TEXT NOT NULL, digest TEXT NOT NULL, fields TEXT NOT NULL,
            PRIMARY KEY(version, digest)
        )""")
        work.execute("BEGIN")
        cache.execute("BEGIN")
    }

    private fun normalize(value: String) = NON_WORD.replace(Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT), "")

    private fun parseAndCache(text: String, digest: String): List<ImportantDealerRow> {
        val result = PropertyListingExtractor.extractListings(sequenceOf(RawMessage(0, "", text)))
            .map { InventoryDeduplicator.toImportantDealerRow(it) }
        runCatching {
            cache.execute("INSERT OR REPLACE INTO parsed_text VALUES (?, ?, ?)",
                listOf(CACHE_VERSION, digest, json.encodeToString(result)))
        }
        parsed++
        return result
    }

    private fun templates(text: String): List<ImportantDealerRow> {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> val n = byte.toInt() and 255; "${"0123456789abcdef"[n ushr 4]}${"0123456789abcdef"[n and 15]}" }
        recent[digest]?.let { reused++; return it }
        val saved = cache.query("SELECT fields FROM parsed_text WHERE version=? AND digest=?", listOf(CACHE_VERSION, digest)) {
            if (it.next()) it.text(0) else null
        }
        val rows = if (saved != null) {
            val decoded = runCatching { json.decodeFromString<List<ImportantDealerRow>>(saved) }.getOrNull()
            if (decoded != null) {
                reused++
                decoded
            } else {
                parseAndCache(text, digest)
            }
        } else {
            parseAndCache(text, digest)
        }
        recent[digest] = rows
        return rows
    }

    private fun isLid(str: String?): Boolean {
        if (str.isNullOrBlank()) return false
        if (str.contains("@lid", ignoreCase = true)) return true
        val digits = str.filter { it.isDigit() }
        return digits.length >= 13 && digits.length == str.trim().length
    }

    private fun cleanSender(sender: String): String {
        return if (sender.contains("@")) sender.substringBefore("@").trim() else sender.trim()
    }

    fun add(message: RawMessage) {
        processed++
        for (template in templates(message.text)) {
            extracted++
            val inMsgDealer = PropertyListingExtractor.extractDealerName(template.fullMessage)
            val isSenderLid = isLid(message.senderName)
            val cleanSender = cleanSender(message.senderName)
            val senderPhone = NON_DIGIT.replace(cleanSender, "").takeLast(10).takeIf { it.length == 10 } ?: ""

            val resolvedDealer = when {
                inMsgDealer.isNotEmpty() -> inMsgDealer
                template.dealerName.isNotBlank() && !isLid(template.dealerName) && !template.dealerName.equals("Dealer", ignoreCase = true) -> template.dealerName
                !isSenderLid && cleanSender.isNotBlank() -> cleanSender
                template.phoneNo.isNotEmpty() -> template.phoneNo
                senderPhone.isNotEmpty() -> senderPhone
                else -> "Dealer"
            }
            val resolvedPhone = template.phoneNo.ifEmpty { senderPhone }
            val row = template.copy(dealerName = resolvedDealer, phoneNo = resolvedPhone)
            val normalized = normalize(row.fullMessage)
            if (normalized.isEmpty()) continue
            val key = "${normalize(row.dealerName)}|$normalized"
            // Keep the earliest post, breaking timestamp ties by first encountered.
            // Exact timestamps replace the previous date-only sort.
            work.execute("""INSERT INTO listings VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(dedup_key) DO UPDATE SET ts=excluded.ts, seq=excluded.seq,
                society=excluded.society, dealer=excluded.dealer, status=excluded.status, fields=excluded.fields
                WHERE excluded.ts < listings.ts""",
                listOf(key, message.timestampMs, extracted, row.society, row.dealerName,
                    row.projectListStatus, json.encodeToString(row)))
        }
        if (processed % 500 == 0) {
            work.execute("COMMIT"); work.execute("BEGIN")
            cache.execute("COMMIT"); cache.execute("BEGIN")
        }
    }

    fun prepare(): InventoryTotals {
        work.execute("COMMIT")
        work.execute("CREATE INDEX listing_order ON listings(ts, seq)")
        work.execute("CREATE INDEX listing_latest ON listings(society, dealer, ts DESC, seq DESC)")
        return work.query("SELECT COUNT(*), COALESCE(SUM(status='IN'), 0) FROM listings") {
            it.next(); InventoryTotals(it.long(0).toInt(), it.long(1).toInt())
        }
    }

    /** Callback keeps the cursor scoped even if writing or cancellation throws. */
    fun forEachRow(consume: (ImportantDealerRow) -> Unit) {
        work.query("""SELECT l.fields, l.seq != (
            SELECT newest.seq FROM listings newest
            WHERE newest.society=l.society AND newest.dealer=l.dealer
            ORDER BY newest.ts DESC, newest.seq DESC LIMIT 1
        ) FROM listings l ORDER BY l.ts, l.seq""") { cursor ->
            while (cursor.next()) {
                val raw = cursor.text(0)
                val row = runCatching { json.decodeFromString<ImportantDealerRow>(raw) }.getOrNull()
                if (row != null) {
                    consume(row.copy(isDuplicate = cursor.long(1) != 0L))
                }
            }
        }
    }

    fun getProjectSummaries(): List<ProjectInventorySummary> {
        return work.query("""
            SELECT society, status, COUNT(*), COUNT(DISTINCT dealer), MAX(ts)
            FROM listings
            GROUP BY society, status
            ORDER BY (status = 'IN') DESC, COUNT(*) DESC
        """) { cursor ->
            val list = mutableListOf<ProjectInventorySummary>()
            while (cursor.next()) {
                list.add(
                    ProjectInventorySummary(
                        society = cursor.text(0),
                        status = cursor.text(1),
                        totalListings = cursor.long(2).toInt(),
                        uniqueDealers = cursor.long(3).toInt(),
                        latestTimestamp = cursor.long(4)
                    )
                )
            }
            list
        }
    }

    fun forEachRowForProject(society: String, consume: (ImportantDealerRow) -> Unit) {
        work.query("""SELECT l.fields, l.seq != (
            SELECT newest.seq FROM listings newest
            WHERE newest.society=l.society AND newest.dealer=l.dealer
            ORDER BY newest.ts DESC, newest.seq DESC LIMIT 1
        ) FROM listings l WHERE l.society = ? ORDER BY l.ts, l.seq""", listOf(society)) { cursor ->
            while (cursor.next()) {
                val raw = cursor.text(0)
                val row = runCatching { json.decodeFromString<ImportantDealerRow>(raw) }.getOrNull()
                if (row != null) {
                    consume(row.copy(isDuplicate = cursor.long(1) != 0L))
                }
            }
        }
    }

    override fun close() {
        try { cache.execute("COMMIT") } catch (_: Exception) {}
        try { work.execute("COMMIT") } catch (_: Exception) {}
        recent.clear()
    }
}
