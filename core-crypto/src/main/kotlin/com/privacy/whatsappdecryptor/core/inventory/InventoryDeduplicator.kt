package com.privacy.whatsappdecryptor.core.inventory

import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@kotlinx.serialization.Serializable
data class ImportantDealerRow(
    val society: String,
    val projectListStatus: String,
    val sec: String,
    val area: String,
    val acco: String,
    val floor: String,
    val flatNo: String,
    val dealerName: String,
    val phoneNo: String,
    val price: String,
    val fullMessage: String,
    val isDuplicate: Boolean
)

object InventoryDeduplicator {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr, dateFormatter)
        } catch (_: Exception) {
            null
        }
    }

    fun filterRecentListings(
        rows: List<ExtractedListing>,
        referenceDate: LocalDate = LocalDate.now(),
        months: Long = 3
    ): List<ExtractedListing> {
        val cutoff = referenceDate.minusMonths(months).withDayOfMonth(1)
        return rows.filter { row ->
            val date = parseDate(row.date)
            date != null && !date.isBefore(cutoff) && !date.isAfter(referenceDate)
        }
    }

    private fun normalizeListingMessage(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase()
        return normalized.replace(Regex("[^\\p{L}\\p{N}]+"), "")
    }

    fun consolidateListings(rows: List<ExtractedListing>): List<ExtractedListing> {
        val ordered = rows.sortedBy { parseDate(it.date) ?: LocalDate.MIN }
        val merged = mutableMapOf<String, ExtractedListing>()

        for (r in ordered) {
            val dealer = normalizeListingMessage(r.postedBy)
            val msg = normalizeListingMessage(r.fullMessage)
            if (msg.isEmpty()) continue
            val key = "$dealer|$msg"
            val existing = merged[key]
            if (existing == null) {
                merged[key] = r.copy(timesPosted = 1)
            } else {
                merged[key] = r.copy(
                    date = existing.date,
                    timesPosted = existing.timesPosted + 1
                )
            }
        }
        return merged.values.toList()
    }

    private fun sectorForInventory(row: ExtractedListing): String {
        val text = "${row.location}\n${row.fullMessage}"
        val match = Regex("\\b(?:sector|sec)\\s*[-–:. ]?\\s*(\\d{1,3}\\s*[A-D]?)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.get(1)?.replace(Regex("\\s+"), "")?.uppercase() ?: ""
    }

    private fun areaForInventory(row: ExtractedListing): String {
        val match = Regex("\\d+(?:\\.\\d+)?").find(row.size)
        return match?.value ?: ""
    }

    private fun accommodationForInventory(row: ExtractedListing): String {
        val text = row.fullMessage
        if (row.bhk.isNotEmpty()) {
            val servant = Regex("\\b\\d\\s*(?:BHK|BED(?:ROOM)?|BR)?\\s*\\+\\s*(?:S|SQ|SR|SERVANT)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
                || Regex("\\b(?:servant|maid|\\bSR\\b)", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val study = Regex("\\bSTUDY\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val suffix = when {
                servant && study -> " + SR + STUDY"
                servant -> " + SR"
                study -> " + STUDY"
                else -> ""
            }
            return "${row.bhk}BHK$suffix"
        }
        val type = row.propertyType
        return when {
            Regex("office|commercial", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "OFFICE"
            Regex("sco", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "SCO"
            Regex("plot|land", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "PLOT"
            Regex("shop|retail", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "SHOP"
            Regex("villa|bungalow", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "VILLA"
            Regex("floor|builder", RegexOption.IGNORE_CASE).containsMatchIn(type) -> "BUILDER FLOOR"
            else -> ""
        }
    }

    private fun floorForInventory(row: ExtractedListing): String {
        val text = row.fullMessage
        val valuePattern = "(lower ground|upper ground|ground|basement|middle|lower|higher|ugf|lgf|\\d{1,2}(?:st|nd|rd|th)?)"
        val match1 = Regex("\\b(?:floor|flr)[ \\t]*[-–:.#]?[ \\t]*$valuePattern\\b", RegexOption.IGNORE_CASE).find(text)
        val match2 = Regex("\\b$valuePattern[ \\t]*(?:floor|flr)\\b", RegexOption.IGNORE_CASE).find(text)
        val match = match1 ?: match2
        if (match != null) {
            val value = match.groupValues[1]
            val number = Regex("^\\d+").find(value)?.value
            return number ?: value.uppercase()
        }
        if (Regex("\\bUGF\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "UGF"
        if (Regex("\\bLGF\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)) return "LGF"
        if (Regex("sco", RegexOption.IGNORE_CASE).containsMatchIn(row.propertyType)) return "SCO"
        if (Regex("plot|land", RegexOption.IGNORE_CASE).containsMatchIn(row.propertyType)) return "PLOT"
        return ""
    }

    private fun flatNumberForInventory(row: ExtractedListing): String {
        val text = row.fullMessage
        val unit = Regex("\\b(?:flat|unit)\\s*(?:no\\.?|number)?\\s*[-–:.#]?\\s*([A-Z0-9][A-Z0-9/-]{0,14})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        val tower = Regex("\\b(?:tower|twr)\\s*[-–:.#]?\\s*([A-Z0-9][A-Z0-9/-]{0,8})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        if (tower != null && unit != null) return "T ${tower.uppercase()} ${unit.uppercase()}"
        if (tower != null) return "T ${tower.uppercase()}"
        if (unit != null) return unit.uppercase()
        val block = Regex("\\bblock\\s*[-–:.#]?\\s*([A-Z0-9][A-Z0-9/-]{0,10})", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)
        return if (block != null) "BLOCK ${block.uppercase()}" else ""
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

    private fun dealerForInventory(row: ExtractedListing): String {
        val inMsg = PropertyListingExtractor.extractDealerName(row.fullMessage)
        if (inMsg.isNotEmpty()) return inMsg

        if (row.postedBy.isNotBlank() && !isLid(row.postedBy)) {
            val cleaned = cleanSender(row.postedBy)
            if (cleaned.isNotEmpty()) return cleaned
        }

        val phone = phoneForInventory(row)
        if (phone.isNotEmpty()) return phone

        val postedClean = cleanSender(row.postedBy)
        return if (isLid(row.postedBy) || isLid(postedClean)) "Dealer" else postedClean.ifEmpty { "Dealer" }
    }

    private fun phoneForInventory(row: ExtractedListing): String {
        val contact = row.contactNo.split(",").firstOrNull()?.replace(Regex("\\D"), "")?.takeLast(10) ?: ""
        if (contact.length == 10) return contact
        if (!isLid(row.postedBy)) {
            val dealer = row.postedBy.replace(Regex("\\D"), "").takeLast(10)
            if (dealer.length == 10) return dealer
        }
        return ""
    }

    private fun priceForInventory(row: ExtractedListing): String {
        val sale = row.salePriceCr.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        if (sale.isNotEmpty()) return sale.joinToString(" | ") { "$it CR" }
        if (row.rentPerMonth.isNotEmpty()) return row.rentPerMonth
        if (row.ratePerSqFt.isNotEmpty()) return row.ratePerSqFt
        val rateMatch = Regex("@\\s*(\\d{4,6})(?:\\s*/-)?", RegexOption.IGNORE_CASE).find(row.fullMessage)
        if (rateMatch != null) {
            return "@ ${rateMatch.groupValues[1]}"
        }
        return ""
    }

    /** Convert a single parsed listing without date filtering or accumulating rows. */
    fun toImportantDealerRow(listing: ExtractedListing): ImportantDealerRow {
        val canonical = ProjectRegistry.canonicalSelectedProject(listing.projectOrSociety)
            .ifEmpty { ProjectRegistry.findCanonicalProjectInText(listing.fullMessage) }
        val resolvedSociety = canonical.ifEmpty { listing.projectOrSociety }
        val dealer = dealerForInventory(listing)
        return ImportantDealerRow(
            society = resolvedSociety,
            projectListStatus = if (canonical.isEmpty()) "OUT" else "IN",
            sec = sectorForInventory(listing), area = areaForInventory(listing),
            acco = accommodationForInventory(listing), floor = floorForInventory(listing),
            flatNo = flatNumberForInventory(listing), dealerName = dealer,
            phoneNo = phoneForInventory(listing), price = priceForInventory(listing),
            fullMessage = listing.fullMessage, isDuplicate = false
        )
    }

    fun toImportantDealerRows(listings: List<ExtractedListing>): List<ImportantDealerRow> {
        val recent = filterRecentListings(listings)
        val consolidated = consolidateListings(recent)

        // Project Canonicalization
        val processedListings = consolidated.map { row ->
            val canonical = ProjectRegistry.canonicalSelectedProject(row.projectOrSociety)
                .ifEmpty { ProjectRegistry.findCanonicalProjectInText(row.fullMessage) }
            val status = if (canonical.isNotEmpty()) "IN" else "OUT"
            val society = if (canonical.isNotEmpty()) canonical else row.projectOrSociety
            val dealer = dealerForInventory(row)
            row.copy(projectOrSociety = society, postedBy = dealer) to status
        }

        // Duplicate Marking:
        // Group by (Project, PostedBy). For multiple occurrences, mark older ones is_duplicate = true
        data class IndexedListing(val index: Int, val listing: ExtractedListing, val status: String)
        val groups = mutableMapOf<String, MutableList<IndexedListing>>()

        processedListings.forEachIndexed { index, (listing, status) ->
            val key = "${listing.projectOrSociety}|${listing.postedBy}"
            groups.getOrPut(key) { mutableListOf() }.add(IndexedListing(index, listing, status))
        }

        val olderIndexes = mutableSetOf<Int>()
        for (group in groups.values) {
            if (group.size < 2) continue
            // Sort by date/time to find the latest
            val sorted = group.sortedBy { parseDate(it.listing.date) ?: LocalDate.MIN }
            val latest = sorted.last()
            for (item in sorted) {
                if (item.index != latest.index) {
                    olderIndexes.add(item.index)
                }
            }
        }

        return processedListings.mapIndexed { index, (listing, status) ->
            val isDuplicate = olderIndexes.contains(index)
            ImportantDealerRow(
                society = listing.projectOrSociety,
                projectListStatus = status,
                sec = sectorForInventory(listing),
                area = areaForInventory(listing),
                acco = accommodationForInventory(listing),
                floor = floorForInventory(listing),
                flatNo = flatNumberForInventory(listing),
                dealerName = listing.postedBy,
                phoneNo = phoneForInventory(listing),
                price = priceForInventory(listing),
                fullMessage = listing.fullMessage,
                isDuplicate = isDuplicate
            )
        }
    }
}
