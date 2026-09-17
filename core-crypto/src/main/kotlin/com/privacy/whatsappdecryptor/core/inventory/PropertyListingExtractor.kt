package com.privacy.whatsappdecryptor.core.inventory

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

data class RawMessage(
    val timestampMs: Long,
    val senderName: String,
    val text: String
)

data class ExtractedListing(
    val date: String,
    val time: String,
    val postedBy: String,
    val dealType: String,
    val propertyType: String,
    val projectOrSociety: String,
    val location: String,
    val bhk: String,
    val size: String,
    val salePriceCr: String,
    val rentPerMonth: String,
    val ratePerSqFt: String,
    val contactNo: String,
    val listingInMsg: String,
    val timesPosted: Int,
    val fullMessage: String
)

object PropertyListingExtractor {

    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}←-⇿⌀-➿⬀-⯿️‍•■-◿]")

    private const val PROJECT_HINTS_SRC =
        "dlf|m3m|emaar|sobha|godrej|signature\\s*global|tulip|vatika|ireo|central\\s*park|elan|whiteland|" +
                "uppal|suncity|adani|\\bats\\b|bestech|conscient|krisumi|smartworld|trump|puri|paras|pioneer|silverglades|tata|" +
                "mahindra|experion|bptp|unitech|ansal|raheja|vipul|aipl|anant\\s*raj|birla|max\\s*estate|trevoc|navraj|orris|" +
                "\\bsare\\b|nirvana|palm|county|greenopolis|indiabulls|international\\s*city|\\brof\\b|mapsko|spaze|ninex|ocus|" +
                "eldeco|assotech|\\bss\\b|microtek|silver\\s*oak|pyramid|shapoorji|risland|hero\\s*homes|lodha|prestige|oberoi|" +
                "tarc|satya|breez|imperia|supertech|\\bamb\\b|\\bjmd\\b|good\\s*earth|world\\s*trade|\\bwtc\\b|two\\s*horizon|sushant|" +
                "south\\s*city|malibu|magnolia|aralias|camellias|crest|park\\s*place|princeton|belaire|icon|pinnacle|summit|" +
                "wellington|regency|hamilton|richmond|windsor|carlton|beverly|oakwood|rosewood|greenwood|heritage|laburnum|" +
                "ridgewood|essentia|la\\s*lagune|grove|garden\\s*city|new\\s*town|valley|heights?|residency|greens|enclave|" +
                "tower|plaza|mall|arcade|estate|homes?|city|court|park|vihar|kunj|nagar|bhawan|square|avenue|boulevard"
    private val PROJECT_HINTS = Regex(PROJECT_HINTS_SRC, RegexOption.IGNORE_CASE)

    private val PTYPE_RULES: List<Pair<String, Regex>> = listOf(
        "SCO / Plot (Commercial)" to Regex("\\bsco\\b", RegexOption.IGNORE_CASE),
        "Shop / Retail" to Regex("\\bshop\\b|\\bretail\\b|\\bshowroom\\b|\\bfood\\s*court\\b|\\banchor\\s*store\\b", RegexOption.IGNORE_CASE),
        "Office / Commercial" to Regex("\\boffice\\b|\\bcommercial\\b|\\bco[- ]?working\\b|\\bit\\s*space\\b|\\bwarehouse\\b|\\bindustrial\\b|\\bpre[- ]?rented\\b", RegexOption.IGNORE_CASE),
        "Villa / Bungalow" to Regex("\\bvilla\\b|\\bbungalow\\b|\\bkothi\\b|\\bduplex\\b|\\bpent\\s*house\\b|\\bpenthouse\\b", RegexOption.IGNORE_CASE),
        "Independent / Builder Floor" to Regex("builder\\s*floor|independent\\s*floor|\\bbuilder\\s*flr\\b|\\bfloor\\b", RegexOption.IGNORE_CASE),
        "Plot / Land" to Regex("\\bplot\\b|\\bland\\b|\\bacres?\\b|\\bkanal\\b", RegexOption.IGNORE_CASE),
        "Apartment / Flat" to Regex("\\bapartment\\b|\\bflat\\b|\\bbhk\\b|\\bbedroom\\b|\\btower\\b|\\bcondo|\\bstudio\\b", RegexOption.IGNORE_CASE)
    )

    private const val SIZE_SRC = "(\\d{2,6}(?:[.,]\\d{1,3})?)\\s*(sq\\.?\\s*(?:yd|yds|yard|yards|gaj)|sq\\s*yrd|sqyd|sqyrd|yds?\\b|gaj\\b|sq\\.?\\s*(?:ft|feet)|sqft|sqf\\b|sqt\\b|\\bsf\\b|s\\.?f\\.?t)"
    private const val BHK_SRC = "(\\d(?:\\s*(?:&|,|/|\\+|and|to)\\s*\\d)*)\\s*(?:\\+\\s*\\d\\s*)?\\s*(?:bhk|b\\.h\\.k|bed\\s*rooms?|bedrooms?|\\bbr\\b)"
    private const val CR_SRC = "(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(?:cr\\b|crore?s?\\b)"
    private const val LAC_SRC = "(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(?:lacs?\\b|lakhs?\\b|l\\b)"
    private const val SECTOR_SRC = "\\b(?:sector|sec)\\s*[-–:. ]?\\s*(\\d{1,3}\\s*[A-Da-d]?)\\b"
    private const val PHONE_SRC = "(?:\\+?91[\\-\\s]?)?\\b([6-9]\\d{4}[\\s\\-]?\\d{5})\\b"

    private val SIZE_RE = Regex(SIZE_SRC, RegexOption.IGNORE_CASE)
    private val BHK_RE = Regex(BHK_SRC, RegexOption.IGNORE_CASE)
    private val CR_RE = Regex(CR_SRC, RegexOption.IGNORE_CASE)
    private val LAC_RE = Regex(LAC_SRC, RegexOption.IGNORE_CASE)
    private val SECTOR_RE = Regex(SECTOR_SRC, RegexOption.IGNORE_CASE)
    private val PHONE_RE = Regex(PHONE_SRC)

    private val PSF_RE = Regex("(\\d{3,6})\\s*(?:/-)?\\s*(?:per|/|p)\\s*sq\\.?\\s*(?:ft|feet|yd|yard)", RegexOption.IGNORE_CASE)
    private const val RENT_K_SRC = "(\\d{1,3}(?:\\.\\d{1,2})?)\\s*k\\b"
    private val RENT_K_RE = Regex(RENT_K_SRC, RegexOption.IGNORE_CASE)
    private const val BIGNUM_SRC = "(?<![\\d.])(\\d[\\d,]{3,8})(?:/-)?(?![\\d.])"
    private val BIGNUM_RE = Regex(BIGNUM_SRC)

    private val LOCALITY = Regex(
        "sohna(?:\\s*road)?|golf\\s*course\\s*ext(?:n|ension)?\\s*road|golf\\s*course\\s*road|gcr\\b|dwarka\\s*expressway|" +
                "\\bspr\\b|southern\\s*peripheral\\s*road|nh\\s*-?\\s*(?:8|48)|mg\\s*road|cyber\\s*(?:city|hub|park)|udyog\\s*vihar|" +
                "manesar|new\\s*gurgaon|old\\s*gurgaon|palam\\s*vihar|sushant\\s*lok|south\\s*city|dlf\\s*phase\\s*[1-5ivx]+|" +
                "northern\\s*peripheral|\\bnoida\\b|\\bdelhi\\b|faridabad|\\bpataudi\\b|\\bdharuhera\\b|\\bbhiwadi\\b|\\bneemrana\\b",
        RegexOption.IGNORE_CASE
    )

    private val REQ_RE = Regex("\\brequire?ments?\\b|\\brequired\\b|\\blooking\\s*for\\b|\\bneed(?:ed)?\\b|\\bwanted\\b|\\bclient\\s*(?:looking|need)|\\bif\\s*(?:you|any)\\b.{0,30}(?:option|inventory|have)", RegexOption.IGNORE_CASE)
    private val AVAIL_RE = Regex("\\bavailable\\b|\\bfor\\s*sale\\b|\\bfor\\s*rent\\b|\\bon\\s*sale\\b|\\bon\\s*rent\\b|\\bdeal\\b|\\binventory\\b|\\boffer\\b", RegexOption.IGNORE_CASE)
    private val RENT_RE = Regex("\\bfor\\s*rent\\b|\\bon\\s*rent\\b|\\brents?\\b|\\brental\\b|\\brented\\b|\\blease\\b|\\btenant\\b|\\bp\\.?\\s*m\\b|/month|per\\s*month", RegexOption.IGNORE_CASE)
    private val SALE_RE = Regex("\\bfor\\s*sale\\b|\\bon\\s*sale\\b|\\bsale\\b|\\bselling\\b|\\bresale\\b|\\basking\\b|\\bdemand\\b|\\bprice\\b|\\bcost\\b|\\bregistry\\b", RegexOption.IGNORE_CASE)
    private val NOISE_RE = Regex("^\\s*(<media omitted>|this message was deleted|null|ok(ay)?|thanks?|done|yes|no|noted|\\W*)\\s*$", RegexOption.IGNORE_CASE)
    private val GENERIC = Regex(
        "^(?:in\\s+|at\\s+)?(?:plot|flat|floor|tower|block|apartment|villa|shop|office|unit|units|" +
                "property|size|area|deal|deals|option|options|multiple|inventory|sqft|sqyd|bhk|type|status|note|" +
                "ground|first|second|third|fourth|basement|stilt|corner|park\\s*facing|semi\\s*furnished|fully\\s*furnished|" +
                "ready\\s*to\\s*move|under\\s*construction|new\\s*booking|resale|rent|rents|sale|rented|location|" +
                "price|asking|demand|details?|contact|available)\\b[\\s\\-–:.#/]*\\w{0,4}$", RegexOption.IGNORE_CASE
    )
    private val GENERIC2 = Regex("^\\d\\s*(?:\\+\\s*\\d\\s*)?(?:bhk|bed)|^(?:unit|flat|shop|plot)\\s*(?:no\\.?|number)?\\s*[-–:.#]?\\s*\\w{1,5}$", RegexOption.IGNORE_CASE)
    private val HEADLINE = Regex("^\\s*(available|urgent|distress|desperate|premium|prime|hot|new|exclusive|fresh|resale|for)\\b.{0,60}$", RegexOption.IGNORE_CASE)
    private val SPLIT_RE = Regex("\\n\\s*\\n|\\n\\s*[-–—_=⸻*]{3,}\\s*\\n")
    private val NEG_LINE = Regex("\\b(price|asking|demand|rent|brokerage|commission|contact|call|size|area|facing|furnished|possession|parking|loaded|renovated|negotiable|available)\\b", RegexOption.IGNORE_CASE)
    private val SIGNAL_RE = Regex("\\bprice|\\basking|\\brent|\\bdemand|\\bplot|\\bfloor|\\bshop|\\boffice", RegexOption.IGNORE_CASE)
    private val BODY_RE = Regex("\\bplot|\\bshop|\\boffice|\\bsco\\b|\\bfloor|\\bapartment|\\bvilla", RegexOption.IGNORE_CASE)
    private val RATE_TAIL = Regex("^\\s*(?:/|per\\s*)?\\s*(?:sq\\.?\\s*(?:ft|feet|yd|yard|yrd)|sqft|sqyd|yd\\b|gaj)", RegexOption.IGNORE_CASE)
    private val RATE_HEAD = Regex("(?:sq\\.?\\s*(?:yds?|yards?|ft|feet)|sqyds?|sqft|yds?|gaj)\\s*[@:]\\s*[\\d.]", RegexOption.IGNORE_CASE)

    private val SALE_KW_G = Regex("\\bsale\\s*price|\\bfor\\s*sale\\b|\\basking\\b|\\bdemand\\b|\\bbooks?\\b|\\bcost\\b|\\bprice\\b", RegexOption.IGNORE_CASE)
    private val RENT_ANY_G = Regex("\\brents?\\b|\\brental\\b|\\brented\\b|\\blease\\b|\\btenant\\b|\\bp\\.?\\s*m\\b|/month|per\\s*month|\\bmonthly\\b|\\+\\s*m\\b|incl?u?d?i?n?g?\\.?\\s*maint|excl?u?d?i?n?g?\\.?\\s*maint", RegexOption.IGNORE_CASE)

    private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yy").withZone(ZoneId.systemDefault())
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

    private fun pyStrip(s: String, chars: String): String {
        var a = 0
        var b = s.length
        while (a < b && chars.contains(s[a])) a++
        while (b > a && chars.contains(s[b - 1])) b--
        return s.substring(a, b)
    }

    private fun fmtG(n: Double): String {
        return if (n % 1.0 == 0.0) n.toLong().toString() else "%.2f".format(n).trimEnd('0').trimEnd('.')
    }

    private fun pyTitle(s: String): String {
        return s.split(Regex("\\s+")).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    fun stripFmt(s: String): String {
        return s.replace(Regex("[*_~`]"), "").replace(EMOJI_REGEX, " ").replace("\u0000", "")
    }

    fun safeTake(s: String, maxChars: Int): String {
        if (s.length <= maxChars) return s
        var take = maxChars
        if (Character.isHighSurrogate(s[take - 1])) {
            take--
        }
        return s.substring(0, take)
    }

    fun dealType(t: String): String {
        val rent = RENT_RE.containsMatchIn(t)
        val sale = SALE_RE.containsMatchIn(t)
        val req = REQ_RE.containsMatchIn(t)
        if (req && !AVAIL_RE.containsMatchIn(t)) return "Requirement"
        if (rent && sale) return "Sale / Rent"
        if (rent) return "Rent"
        if (sale) return "Sale"
        return if (req) "Requirement" else "Other"
    }

    fun propertyType(t: String): String {
        for ((label, pattern) in PTYPE_RULES) {
            if (pattern.containsMatchIn(t)) return label
        }
        return ""
    }

    fun project(t: String): String {
        var bestScore = -Double.MAX_VALUE
        var bestNegI = Int.MIN_VALUE
        var bestLine = ""

        val lines = t.split("\n")
        for (i in lines.indices) {
            var l = pyStrip(stripFmt(lines[i]), " :•*-–—>·|.").replace(Regex("\\s{2,}"), " ")
            if (l.length <= 3 || l.length >= 80) continue
            if (PHONE_RE.containsMatchIn(l) || HEADLINE.containsMatchIn(l) || GENERIC.containsMatchIn(l) || GENERIC2.containsMatchIn(l)) continue
            val letters = l.replace(Regex("[^A-Za-z]"), "")
            if (letters.length < 4) continue

            var s = 0.0
            if (PROJECT_HINTS.containsMatchIn(l)) s += 3.0
            if (SECTOR_RE.containsMatchIn(l)) s += 2.0
            if (NEG_LINE.containsMatchIn(l)) s -= 2.5
            if (l.firstOrNull()?.isDigit() == true) s -= 1.5
            if (letters.length.toDouble() / l.length < 0.5) s -= 1.0
            s += maxOf(0.0, 2.5 - i * 0.6)

            val negI = -i
            if (s > bestScore || (s == bestScore && (negI > bestNegI || (negI == bestNegI && l > bestLine)))) {
                bestScore = s
                bestNegI = negI
                bestLine = l
            }
        }
        if (bestLine.isEmpty()) return ""
        var out = bestLine
        out = out.replace(Regex("^(?:in|at)\\s+", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("^(?:location|project|society|property|address)\\s*[:\\-–]\\s*", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("\\s*[,\\-–|]\\s*(?:sector|sec)\\s*[-–: ]?\\s*\\d+\\s*[A-Da-d]?\\s*$", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("^(?:available\\s*(?:for\\s*\\w+)?|for)\\s+(?:sale|rent)?\\s*(?:in|at)?\\s*", RegexOption.IGNORE_CASE), "")
        out = out.replace(Regex("\\s+(?:bringing|presenting|offers?)\\b.*$", RegexOption.IGNORE_CASE), "")
        out = out.split(Regex("\\s+for\\s+(?:sale|rent)\\b", RegexOption.IGNORE_CASE))[0]
        return safeTake(pyStrip(out, " :,-|").replace("\u0000", ""), 60)
    }

    fun location(t: String): String {
        val parts = mutableListOf<String>()
        val secs = SECTOR_RE.findAll(t).take(2)
        for (m in secs) {
            parts.add("Sector " + m.groupValues[1].replace(Regex("\\s+"), "").uppercase())
        }
        val locMatch = LOCALITY.find(t)
        if (locMatch != null) {
            parts.add(pyTitle(locMatch.value.replace(Regex("\\s+"), " ")))
        }
        return parts.distinct().joinToString(", ")
    }

    fun sizes(t: String): List<String> {
        val out = mutableListOf<String>()
        for (m in SIZE_RE.findAll(t)) {
            val numStr = m.groupValues[1].replace(",", "")
            val n = numStr.toDoubleOrNull() ?: continue
            val unitStr = m.groupValues[2]
            val u = if (Regex("yd|yard|yrd|gaj", RegexOption.IGNORE_CASE).containsMatchIn(unitStr)) "sq.yd" else "sq.ft"
            if (n in 50.0..500000.0) {
                out.add("${fmtG(n)} $u")
            }
        }
        return out.distinct()
    }

    fun bhk(t: String): String {
        val vals = sortedSetOf<Int>()
        for (m in BHK_RE.findAll(t)) {
            val digits = Regex("\\d").findAll(m.groupValues[1]).map { it.value.toInt() }
            for (d in digits) {
                if (d in 1..9) vals.add(d)
            }
        }
        return vals.joinToString("/")
    }

    data class MoneyResult(val sale: List<Double>, val rent: List<Double>, val rates: List<String>)

    fun money(t: String, ctx: String = ""): MoneyResult {
        val sale = mutableListOf<Double>()
        val rent = mutableListOf<Double>()
        val rates = mutableListOf<String>()
        val ctxRent = ctx == "Rent"

        for (line in t.split("\n")) {
            val rentPos = RENT_ANY_G.findAll(line).map { it.range.first }.toList()
            val salePos = SALE_KW_G.findAll(line).map { it.range.first }.toList()

            fun isRentAt(idx: Int): Boolean {
                val dr = if (rentPos.isEmpty()) Int.MAX_VALUE else rentPos.minOf { kotlin.math.abs(it - idx) }
                val ds = if (salePos.isEmpty()) Int.MAX_VALUE else salePos.minOf { kotlin.math.abs(it - idx) }
                return if (dr == Int.MAX_VALUE && ds == Int.MAX_VALUE) ctxRent else dr <= ds
            }

            for (m in CR_RE.findAll(line)) {
                val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                if (v !in 0.05..999.0) continue
                val tail = line.substring(m.range.last + 1)
                if (RATE_TAIL.containsMatchIn(tail)) {
                    rates.add("${fmtG(v)} Cr/unit")
                    continue
                }
                sale.add(v)
            }

            for (m in LAC_RE.findAll(line)) {
                val v = m.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                val tail = line.substring(m.range.last + 1)
                if (RATE_TAIL.containsMatchIn(tail)) {
                    if (v in 0.01..500.0) rates.add("${fmtG(v)} L/unit")
                    continue
                }
                if (isRentAt(m.range.first)) {
                    if (v in 0.1..100.0) rent.add(v * 100000.0)
                } else if (v in 1.0..999.0) {
                    sale.add(v / 100.0)
                }
            }

            val kIsRate = rentPos.isEmpty() && RATE_HEAD.containsMatchIn(line)
            for (m in RENT_K_RE.findAll(line)) {
                val v = m.groupValues[1].toDoubleOrNull() ?: continue
                val tail = line.substring(m.range.last + 1)
                if (RATE_TAIL.containsMatchIn(tail)) continue
                if (kIsRate) {
                    rates.add("${fmtG(v)} K/unit")
                    continue
                }
                if (!isRentAt(m.range.first)) continue
                if (v in 5.0..999.0) rent.add(v * 1000.0)
            }

            for (m in BIGNUM_RE.findAll(line)) {
                if (!isRentAt(m.range.first)) continue
                val v = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: continue
                val tail = line.substring(m.range.last + 1)
                if (RATE_TAIL.containsMatchIn(tail)) continue
                if (v in 5000.0..5000000.0 && !Regex("^\\s*(?:sq|sf|s\\.f)", RegexOption.IGNORE_CASE).containsMatchIn(tail)) {
                    rent.add(v)
                }
            }
        }
        return MoneyResult(sale.distinct(), rent.distinct(), rates.distinct())
    }

    fun psf(t: String): String {
        for (m in PSF_RE.findAll(t)) {
            val n = m.groupValues[1].toIntOrNull() ?: continue
            if (n in 1000..500000) return n.toString()
        }
        return ""
    }

    private fun hasSignal(b: String): Boolean {
        return BHK_RE.containsMatchIn(b) || SIZE_RE.containsMatchIn(b) || CR_RE.containsMatchIn(b) || SIGNAL_RE.containsMatchIn(b)
    }

    fun chunks(body: String): List<String> {
        val blocks = body.split(SPLIT_RE).filter { it.isNotBlank() }
        if (blocks.size < 2) return listOf(body)
        val out = mutableListOf<String>()
        val buf = mutableListOf<String>()

        for (b in blocks) {
            val first = b.split("\n").firstOrNull() ?: ""
            val startsNew = PROJECT_HINTS.containsMatchIn(first) || SECTOR_RE.containsMatchIn(first) || !hasSignal(b)
            val joined = buf.joinToString("\n")
            val ready = CR_RE.containsMatchIn(joined) || LAC_RE.containsMatchIn(joined) || SIZE_RE.containsMatchIn(joined) || BHK_RE.containsMatchIn(joined)
            if (buf.isNotEmpty() && startsNew && ready) {
                out.add(joined)
                buf.clear()
            }
            buf.add(b)
        }
        if (buf.isNotEmpty()) out.add(buf.joinToString("\n"))
        return if (out.size > 1) out else listOf(body)
    }

    private fun fmtRent(v: Double): String {
        return if (v >= 100000.0) "${fmtG(v / 100000.0)} L" else "${fmtG(v / 1000.0)} k"
    }

    fun extractListings(
        messages: Sequence<RawMessage>,
        onProgress: ((Int) -> Unit)? = null
    ): List<ExtractedListing> {
        val seen = mutableMapOf<String, Int>()
        val rows = mutableListOf<ExtractedListing>()
        var count = 0

        for (msg in messages) {
            if (onProgress != null && ++count % 1000 == 0) onProgress(count)
            var rawBody = msg.text.trim()
            if (rawBody.isBlank()) continue

            // Normalize fancy fonts and unicode
            rawBody = Normalizer.normalize(rawBody.replace("\r", "").replace("‎", ""), Normalizer.Form.NFKC)

            if (NOISE_RE.containsMatchIn(rawBody) || (rawBody.contains("<Media omitted>", ignoreCase = true) && rawBody.length < 60)) {
                continue
            }
            val fullClean = stripFmt(rawBody)
            if (!(BHK_RE.containsMatchIn(fullClean) || SIZE_RE.containsMatchIn(fullClean) || CR_RE.containsMatchIn(fullClean) || BODY_RE.containsMatchIn(fullClean))) {
                continue
            }

            val parts = chunks(rawBody)
            val phonesMsg = PHONE_RE.findAll(fullClean).map { it.groupValues[1].replace(Regex("\\D"), "").takeLast(10) }.toList()
            val dtMsg = dealType(fullClean)
            val msgSectors = SECTOR_RE.findAll(fullClean).map { it.groupValues[1].replace(Regex("\\s+"), "").uppercase() }.toSet()
            val msgLoc = if (msgSectors.size <= 1) location(fullClean) else ""
            val key0 = fullClean.lowercase().replace(Regex("[^0-9a-z_À-ÿ]+"), "").take(200)
            val times = (seen[key0] ?: 0) + 1
            seen[key0] = times

            val instant = Instant.ofEpochMilli(msg.timestampMs)
            val dateStr = dateFormatter.format(instant)
            val timeStr = timeFormatter.format(instant)

            for (i in parts.indices) {
                val part = parts[i]
                val c = stripFmt(part)
                var dt = dealType(c)
                if (dt == "Other") dt = dtMsg
                val moneyRes = money(c, dt)
                val sz = sizes(c)
                val b = bhk(c)
                val pj = project(c)
                val rate = psf(c)

                if (moneyRes.sale.isEmpty() && moneyRes.rent.isEmpty() && sz.isEmpty() && b.isEmpty() && rate.isEmpty()) {
                    continue
                }

                val phonesPart = PHONE_RE.findAll(c).map { it.groupValues[1].replace(Regex("\\D"), "").takeLast(10) }.toList()
                val phones = (phonesPart + phonesMsg).distinct()

                rows.add(
                    ExtractedListing(
                        date = dateStr,
                        time = timeStr,
                        postedBy = msg.senderName,
                        dealType = dt,
                        propertyType = propertyType(c).ifEmpty { propertyType(fullClean) },
                        projectOrSociety = pj,
                        location = location(c).ifEmpty { msgLoc },
                        bhk = b,
                        size = sz.take(3).joinToString(" | "),
                        salePriceCr = moneyRes.sale.take(3).joinToString(" | ") { fmtG(it) },
                        rentPerMonth = moneyRes.rent.take(3).joinToString(" | ") { fmtRent(it) },
                        ratePerSqFt = rate.ifEmpty { moneyRes.rates.take(2).joinToString(" | ") },
                        contactNo = phones.take(2).joinToString(", "),
                        listingInMsg = if (parts.size > 1) "${i + 1} of ${parts.size}" else "",
                        timesPosted = times,
                        fullMessage = safeTake(part.trim().replace("\u0000", ""), 2000)
                    )
                )
            }
        }
        return rows
    }
}
