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

data class InventoryTotals(val rows: Int, val matched: Int)

/** Only a bounded set of parsed texts lives in memory. All ads and sorting live on disk. */
class StreamingInventoryStore(private val work: InventorySql, private val cache: InventorySql) : Closeable {
    companion object {
        // Bump when property extraction, project rules, or CSV field conversion change.
        const val CACHE_VERSION = "android-inventory-v1"
        private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
        private val NON_DIGIT = Regex("\\D")
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
        ) WITHOUT ROWID""")
        work.execute("BEGIN")
        cache.execute("BEGIN")
    }

    private fun normalize(value: String) = NON_WORD.replace(Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(java.util.Locale.ROOT), "")

    private fun templates(text: String): List<ImportantDealerRow> {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> val n = byte.toInt() and 255; "${"0123456789abcdef"[n ushr 4]}${"0123456789abcdef"[n and 15]}" }
        recent[digest]?.let { reused++; return it }
        val saved = cache.query("SELECT fields FROM parsed_text WHERE version=? AND digest=?", listOf(CACHE_VERSION, digest)) {
            if (it.next()) it.text(0) else null
        }
        val rows = if (saved != null) {
            reused++
            Json.decodeFromString<List<ImportantDealerRow>>(saved)
        } else {
            // Parse one distinct text at a time. Sender/time are deliberately absent
            // from these reusable templates, including cached non-listings ([]).
            val result = PropertyListingExtractor.extractListings(sequenceOf(RawMessage(0, "", text)))
                .map { InventoryDeduplicator.toImportantDealerRow(it) }
            cache.execute("INSERT OR REPLACE INTO parsed_text VALUES (?, ?, ?)",
                listOf(CACHE_VERSION, digest, Json.encodeToString(result)))
            parsed++
            result
        }
        recent[digest] = rows
        return rows
    }

    fun add(message: RawMessage) {
        processed++
        for (template in templates(message.text)) {
            extracted++
            val fallback = NON_DIGIT.replace(message.senderName, "").takeLast(10).takeIf { it.length == 10 } ?: ""
            val row = template.copy(dealerName = message.senderName,
                phoneNo = template.phoneNo.ifEmpty { fallback })
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
                    row.projectListStatus, Json.encodeToString(row)))
        }
        if (processed % 500 == 0) {
            work.execute("COMMIT"); work.execute("BEGIN")
            cache.execute("COMMIT"); cache.execute("BEGIN")
        }
    }

    fun prepare(): InventoryTotals {
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
                consume(Json.decodeFromString<ImportantDealerRow>(cursor.text(0))
                    .copy(isDuplicate = cursor.long(1) != 0L))
            }
        }
    }

    override fun close() {
        // Preserve the reusable text cache even when an export is cancelled.
        try { cache.execute("COMMIT") } finally { recent.clear() }
        // Drivers are owned by the caller; its use blocks close them on all paths.
    }
}
