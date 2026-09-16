package com.privacy.whatsappdecryptor.core.inventory

import java.text.Normalizer

object ProjectRegistry {

    val SELECTED_PROJECT_NAMES: List<String> = listOf(
        "SMART WORLD DXP", "M3M MANSION", "M3M CAPITAL", "M3M CROWN", "PURI DIPLOMACTIC",
        "TASHEE CAPITAL GATWAY", "ENGIMA", "ELAN THE PRESIDENTIAL", "SOBHA CITY", "GODREJ MERIDIAN",
        "HERO HOMES", "BPTP AMTORIA", "M3M WOODSHIRE", "ATS KOCOON", "WINDCHINTS",
        "IMPERIAL GARDEN", "TATA GURGAON GATEWAY", "INDIABULLS OFFICE SPACE", "ANSAL HIGHLAND PARK",
        "SHAHPURJEE", "SATYA HIVE SEC102", "HEART SONG", "KRUSIMI", "HERITAGE MAX", "PARAS",
        "G 99 PLOT", "EXPERION WESTILIZE", "PURI EMERALD BAY", "TATA LA VIDA", "KASHISH MANON ONE",
        "RAJEJA VANYA PLOT", "PLAZA SEC 106", "ASSOTECH BLITH", "GURGAON GREENS", "M3M CAPITAL WALK",
        "GODREJ ZENITH", "MAX 360", "SOBHA VILLA", "M3M SOLITUDE", "COSMOS EXPRESS", "GODREJ ICON",
        "EMAAR EBD SCO 114", "DIPLOMACTIC GREEN NEW", "ATS TRIMPH", "ATS TOURMOLINE", "HCBS",
        "SATYA MERINO", "VIENNA GREENS", "PAREENA COBAN", "INDIA BULLS HIGHTS",
        "INDIABULLS CLUB & ESTATE", "SIGNATURE DXP", "KIMBERLY SUITES", "WESTIN"
    )

    private val HTML_HEX_REGEX = Regex("&#x([0-9a-fA-F]+);")
    private val HTML_DEC_REGEX = Regex("&#(\\d+);")
    private val DIACRITICS_REGEX = Regex("\\p{M}")
    private val NON_ALPHANUM_REGEX = Regex("[^A-Z0-9]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    private val ALIASES: List<Pair<Regex, String>> = listOf(
        Regex("\\bSMART WORLD\\b", RegexOption.IGNORE_CASE) to "SMARTWORLD",
        Regex("\\bINDIA BULLS?\\b", RegexOption.IGNORE_CASE) to "INDIABULLS",
        Regex("\\bKRUSIMI\\b", RegexOption.IGNORE_CASE) to "KRISUMI",
        Regex("\\bENGIMA\\b", RegexOption.IGNORE_CASE) to "ENIGMA",
        Regex("\\bDIPLOMACTIC\\b", RegexOption.IGNORE_CASE) to "DIPLOMATIC",
        Regex("\\bGATWAY\\b", RegexOption.IGNORE_CASE) to "GATEWAY",
        Regex("\\bTAASHI\\b|\\bTASHI\\b", RegexOption.IGNORE_CASE) to "TASHEE",
        Regex("\\bAMTORIA\\b", RegexOption.IGNORE_CASE) to "AMSTORIA",
        Regex("\\bMERIDIAN\\b", RegexOption.IGNORE_CASE) to "MERIDIEN",
        Regex("\\bEMARALD\\b|\\bEMRALD\\b", RegexOption.IGNORE_CASE) to "EMERALD",
        Regex("\\bKOCOON\\b|\\bKUCOON\\b", RegexOption.IGNORE_CASE) to "KOCOON",
        Regex("\\bTRIMPH\\b", RegexOption.IGNORE_CASE) to "TRIUMPH",
        Regex("\\bTOURMOLINE\\b", RegexOption.IGNORE_CASE) to "TOURMALINE",
        Regex("\\bWINDCHINTS?\\b|\\bWINDCHANTS?\\b", RegexOption.IGNORE_CASE) to "WINDCHANTS",
        Regex("\\bWESTILIZE\\b", RegexOption.IGNORE_CASE) to "WESTERLIES",
        Regex("\\bSOULITUDE\\b", RegexOption.IGNORE_CASE) to "SOLITUDE",
        Regex("\\bMANON\\b", RegexOption.IGNORE_CASE) to "MANOR",
        Regex("\\bRAJEJA\\b", RegexOption.IGNORE_CASE) to "RAHEJA",
        Regex("\\bMARENO\\b", RegexOption.IGNORE_CASE) to "MERINO",
        Regex("\\bHIGHTS\\b", RegexOption.IGNORE_CASE) to "HEIGHTS",
        Regex("\\bPAREENA\\b", RegexOption.IGNORE_CASE) to "PAREENA"
    )

    fun normalizeProjectName(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var name = value
            .replace(HTML_HEX_REGEX) { String(Character.toChars(it.groupValues[1].toInt(16))) }
            .replace(HTML_DEC_REGEX) { String(Character.toChars(it.groupValues[1].toInt(10))) }

        name = Normalizer.normalize(name, Normalizer.Form.NFKD)
            .replace(DIACRITICS_REGEX, "")
            .uppercase()
            .replace("&", " AND ")
            .replace(NON_ALPHANUM_REGEX, " ")
            .trim()
            .replace(MULTI_SPACE_REGEX, " ")

        for ((pattern, replacement) in ALIASES) {
            name = name.replace(pattern, replacement)
        }
        return name
    }

    fun canonicalSelectedProject(value: String?): String {
        val p = normalizeProjectName(value)
        if (p.isEmpty()) return ""

        fun has(vararg parts: String): Boolean = parts.all { p.contains(it) }

        return when {
            has("M3M", "CAPITAL", "WALK") -> "M3M CAPITAL WALK"
            has("SMARTWORLD", "DXP") -> "SMART WORLD DXP"
            has("M3M", "MANSION") -> "M3M MANSION"
            has("M3M", "CAPITAL") -> "M3M CAPITAL"
            has("M3M", "CROWN") -> "M3M CROWN"
            has("PURI", "DIPLOMATIC") -> "PURI DIPLOMACTIC"
            has("DIPLOMATIC", "GREEN") -> "DIPLOMACTIC GREEN NEW"
            has("TASHEE", "CAPITAL", "GATEWAY") -> "TASHEE CAPITAL GATWAY"
            has("ENIGMA") -> "ENGIMA"
            has("ELAN", "PRESIDENTIAL") -> "ELAN THE PRESIDENTIAL"
            has("SOBHA CITY") -> "SOBHA CITY"
            has("GODREJ", "MERIDIEN") -> "GODREJ MERIDIAN"
            has("HERO", "HOME") -> "HERO HOMES"
            has("BPTP", "AMSTORIA") -> "BPTP AMTORIA"
            has("M3M", "WOODSHIRE") -> "M3M WOODSHIRE"
            has("ATS", "KOCOON") -> "ATS KOCOON"
            has("WINDCHANTS") -> "WINDCHINTS"
            has("IMPERIAL", "GARDEN") -> "IMPERIAL GARDEN"
            has("TATA", "GURGAON", "GATEWAY") -> "TATA GURGAON GATEWAY"
            has("INDIABULLS", "OFFICE") -> "INDIABULLS OFFICE SPACE"
            has("ANSAL", "HIGHLAND", "PARK") -> "ANSAL HIGHLAND PARK"
            Regex("\\b(SHAPOORJI|SHAHPURJEE|SHAPURJI)\\b").containsMatchIn(p) -> "SHAHPURJEE"
            has("SATYA", "HIVE") -> "SATYA HIVE SEC102"
            has("HEARTSONG") || (has("HEART") && has("SONG")) -> "HEART SONG"
            has("KRISUMI") -> "KRUSIMI"
            has("HERITAGE", "MAX") -> "HERITAGE MAX"
            Regex("\\bPARAS\\b").containsMatchIn(p) -> "PARAS"
            Regex("\\bG ?99\\b").containsMatchIn(p) -> "G 99 PLOT"
            has("EXPERION", "WESTERLIES") -> "EXPERION WESTILIZE"
            has("PURI", "EMERALD", "BAY") -> "PURI EMERALD BAY"
            has("TATA", "LA", "VIDA") -> "TATA LA VIDA"
            has("KASHISH", "MANOR", "ONE") -> "KASHISH MANON ONE"
            has("RAHEJA", "VANYA") -> "RAJEJA VANYA PLOT"
            has("PLAZA") && Regex("\\b106\\b").containsMatchIn(p) -> "PLAZA SEC 106"
            has("ASSOTECH", "BLITH") -> "ASSOTECH BLITH"
            has("GURGAON", "GREEN") -> "GURGAON GREENS"
            has("GODREJ", "ZENITH") -> "GODREJ ZENITH"
            has("MAX", "360") -> "MAX 360"
            has("SOBHA", "VILLA") -> "SOBHA VILLA"
            has("M3M", "SOLITUDE") -> "M3M SOLITUDE"
            has("COSMOS", "EXPRESS") -> "COSMOS EXPRESS"
            has("GODREJ", "ICON") -> "GODREJ ICON"
            has("EMAAR", "114") && (has("EBD") || has("SCO") || has("BUSINESS", "DISTRICT")) -> "EMAAR EBD SCO 114"
            has("ATS", "TRIUMPH") -> "ATS TRIMPH"
            has("ATS", "TOURMALINE") -> "ATS TOURMOLINE"
            Regex("\\bHCBS\\b").containsMatchIn(p) -> "HCBS"
            has("SATYA", "MERINO") -> "SATYA MERINO"
            has("VIENNA", "GREEN") -> "VIENNA GREENS"
            has("PAREENA", "COBAN") -> "PAREENA COBAN"
            has("INDIABULLS", "CLUB", "ESTATE") -> "INDIABULLS CLUB & ESTATE"
            has("INDIABULLS", "HEIGHT") -> "INDIA BULLS HIGHTS"
            has("SIGNATURE", "DXP") -> "SIGNATURE DXP"
            has("KIMBERLY", "SUITE") -> "KIMBERLY SUITES"
            Regex("\\bWESTIN\\b").containsMatchIn(p) -> "WESTIN"
            else -> ""
        }
    }

    fun isSelectedProject(value: String?): Boolean = canonicalSelectedProject(value).isNotEmpty()
}
