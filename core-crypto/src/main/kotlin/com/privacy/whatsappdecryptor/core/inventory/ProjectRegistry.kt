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

    data class ProjectPattern(val regex: Regex, val canonical: String)

    private val FULL_TEXT_CANONICAL_PATTERNS: List<ProjectPattern> = listOf(
        ProjectPattern(Regex("\\bM3M\\s+CAPITAL\\s+WALK\\b", RegexOption.IGNORE_CASE), "M3M CAPITAL WALK"),
        ProjectPattern(Regex("\\bSMART\\s*WORLD\\s+(?:ONE\\s+)?DXP\\b", RegexOption.IGNORE_CASE), "SMART WORLD DXP"),
        ProjectPattern(Regex("\\bONE\\s+DXP\\b", RegexOption.IGNORE_CASE), "SMART WORLD DXP"),
        ProjectPattern(Regex("\\bM3M\\s+MANSION\\b", RegexOption.IGNORE_CASE), "M3M MANSION"),
        ProjectPattern(Regex("\\bM3M\\s+CAPITAL\\b", RegexOption.IGNORE_CASE), "M3M CAPITAL"),
        ProjectPattern(Regex("\\bM3M\\s+CROWN\\b", RegexOption.IGNORE_CASE), "M3M CROWN"),
        ProjectPattern(Regex("\\bPURI\\s+DIPLOMAT?IC(?:\\s+RESIDENCES?)?\\b", RegexOption.IGNORE_CASE), "PURI DIPLOMACTIC"),
        ProjectPattern(Regex("\\bDIPLOMAT?IC\\s+GREENS?\\b", RegexOption.IGNORE_CASE), "DIPLOMACTIC GREEN NEW"),
        ProjectPattern(Regex("\\bTASHEE\\s+CAPITAL\\s+GATE?WAY\\b", RegexOption.IGNORE_CASE), "TASHEE CAPITAL GATWAY"),
        ProjectPattern(Regex("\\bENIGMA\\b|\\bENGIMA\\b", RegexOption.IGNORE_CASE), "ENGIMA"),
        ProjectPattern(Regex("\\bELAN\\s+(?:THE\\s+)?PRESIDENTIAL\\b", RegexOption.IGNORE_CASE), "ELAN THE PRESIDENTIAL"),
        ProjectPattern(Regex("\\bSOBHA\\s+CITY\\b", RegexOption.IGNORE_CASE), "SOBHA CITY"),
        ProjectPattern(Regex("\\bGODREJ\\s+MERIDI?EN\\b", RegexOption.IGNORE_CASE), "GODREJ MERIDIAN"),
        ProjectPattern(Regex("\\bHERO\\s+HOMES?\\b", RegexOption.IGNORE_CASE), "HERO HOMES"),
        ProjectPattern(Regex("\\bBPTP\\s+AM[S]?TORIA\\b", RegexOption.IGNORE_CASE), "BPTP AMTORIA"),
        ProjectPattern(Regex("\\bM3M\\s+WOODSHIRE\\b", RegexOption.IGNORE_CASE), "M3M WOODSHIRE"),
        ProjectPattern(Regex("\\bATS\\s+K[O|U]COON\\b", RegexOption.IGNORE_CASE), "ATS KOCOON"),
        ProjectPattern(Regex("\\bWINDCH[A|I]NTS?\\b", RegexOption.IGNORE_CASE), "WINDCHINTS"),
        ProjectPattern(Regex("\\bIMPERIAL\\s+GARDENS?\\b", RegexOption.IGNORE_CASE), "IMPERIAL GARDEN"),
        ProjectPattern(Regex("\\bTATA\\s+GURGAON\\s+GATEWAY\\b", RegexOption.IGNORE_CASE), "TATA GURGAON GATEWAY"),
        ProjectPattern(Regex("\\bINDIABULLS\\s+OFFICE\\b", RegexOption.IGNORE_CASE), "INDIABULLS OFFICE SPACE"),
        ProjectPattern(Regex("\\bANSAL\\s+HIGHLAND\\s+PARK\\b", RegexOption.IGNORE_CASE), "ANSAL HIGHLAND PARK"),
        ProjectPattern(Regex("\\b(SHAPOORJI|SHAHPURJEE|SHAPURJI)\\b", RegexOption.IGNORE_CASE), "SHAHPURJEE"),
        ProjectPattern(Regex("\\bSATYA\\s+HIVE\\b", RegexOption.IGNORE_CASE), "SATYA HIVE SEC102"),
        ProjectPattern(Regex("\\bHEART\\s*SONG\\b", RegexOption.IGNORE_CASE), "HEART SONG"),
        ProjectPattern(Regex("\\b(KRISUMI|KRUSIMI)\\b", RegexOption.IGNORE_CASE), "KRUSIMI"),
        ProjectPattern(Regex("\\bHERITAGE\\s+MAX\\b", RegexOption.IGNORE_CASE), "HERITAGE MAX"),
        ProjectPattern(Regex("\\bPARAS\\b", RegexOption.IGNORE_CASE), "PARAS"),
        ProjectPattern(Regex("\\bG\\s*99\\b", RegexOption.IGNORE_CASE), "G 99 PLOT"),
        ProjectPattern(Regex("\\bEXPERION\\s+WESTERLIES\\b|\\bEXPERION\\s+WESTILIZE\\b", RegexOption.IGNORE_CASE), "EXPERION WESTILIZE"),
        ProjectPattern(Regex("\\bPURI\\s+EMERALD\\s+BAY\\b", RegexOption.IGNORE_CASE), "PURI EMERALD BAY"),
        ProjectPattern(Regex("\\bTATA\\s+LA\\s+VIDA\\b", RegexOption.IGNORE_CASE), "TATA LA VIDA"),
        ProjectPattern(Regex("\\bKASHISH\\s+MANOR\\s+ONE\\b|\\bKASHISH\\s+MANON\\s+ONE\\b", RegexOption.IGNORE_CASE), "KASHISH MANON ONE"),
        ProjectPattern(Regex("\\bRA[H|J]EJA\\s+VANYA\\b", RegexOption.IGNORE_CASE), "RAJEJA VANYA PLOT"),
        ProjectPattern(Regex("\\bPLAZA\\s*(?:SEC(?:TOR)?)?\\s*106\\b", RegexOption.IGNORE_CASE), "PLAZA SEC 106"),
        ProjectPattern(Regex("\\bASSOTECH\\s+BLITH\\b", RegexOption.IGNORE_CASE), "ASSOTECH BLITH"),
        ProjectPattern(Regex("\\bGURGAON\\s+GREENS?\\b", RegexOption.IGNORE_CASE), "GURGAON GREENS"),
        ProjectPattern(Regex("\\bGODREJ\\s+ZENITH\\b", RegexOption.IGNORE_CASE), "GODREJ ZENITH"),
        ProjectPattern(Regex("\\bMAX\\s*360\\b", RegexOption.IGNORE_CASE), "MAX 360"),
        ProjectPattern(Regex("\\bSOBHA\\s+VILLA\\b", RegexOption.IGNORE_CASE), "SOBHA VILLA"),
        ProjectPattern(Regex("\\bM3M\\s+SOLITUDE\\b|\\bM3M\\s+SOULITUDE\\b", RegexOption.IGNORE_CASE), "M3M SOLITUDE"),
        ProjectPattern(Regex("\\bCOSMOS\\s+EXPRESS\\b", RegexOption.IGNORE_CASE), "COSMOS EXPRESS"),
        ProjectPattern(Regex("\\bGODREJ\\s+ICON\\b", RegexOption.IGNORE_CASE), "GODREJ ICON"),
        ProjectPattern(Regex("\\bEMAAR\\s+EBD\\b|\\bEBD\\s*(?:SCO\\s*)?114\\b", RegexOption.IGNORE_CASE), "EMAAR EBD SCO 114"),
        ProjectPattern(Regex("\\bATS\\s+TRI?UMPH\\b", RegexOption.IGNORE_CASE), "ATS TRIMPH"),
        ProjectPattern(Regex("\\bATS\\s+TOURMALINE\\b|\\bATS\\s+TOURMOLINE\\b", RegexOption.IGNORE_CASE), "ATS TOURMOLINE"),
        ProjectPattern(Regex("\\bHCBS\\b", RegexOption.IGNORE_CASE), "HCBS"),
        ProjectPattern(Regex("\\bSATYA\\s+M[E|A]RINO\\b", RegexOption.IGNORE_CASE), "SATYA MERINO"),
        ProjectPattern(Regex("\\bVIENNA\\s+GREENS?\\b", RegexOption.IGNORE_CASE), "VIENNA GREENS"),
        ProjectPattern(Regex("\\bPAREENA\\s+COBAN\\b", RegexOption.IGNORE_CASE), "PAREENA COBAN"),
        ProjectPattern(Regex("\\bINDIABULLS\\s+CLUB\\s*(?:&|AND)?\\s*ESTATE\\b", RegexOption.IGNORE_CASE), "INDIABULLS CLUB & ESTATE"),
        ProjectPattern(Regex("\\bINDIA\\s*BULLS?\\s+H[E|I]IGHTS?\\b", RegexOption.IGNORE_CASE), "INDIA BULLS HIGHTS"),
        ProjectPattern(Regex("\\bSIGNATURE\\s+(?:GLOBAL\\s+)?(?:DE\\s*LUXE\\s+)?DXP\\b", RegexOption.IGNORE_CASE), "SIGNATURE DXP"),
        ProjectPattern(Regex("\\bKIMBERLY\\s+SUITES?\\b", RegexOption.IGNORE_CASE), "KIMBERLY SUITES"),
        ProjectPattern(Regex("\\bWESTIN\\b", RegexOption.IGNORE_CASE), "WESTIN")
    )

    fun findCanonicalProjectInText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        for (item in FULL_TEXT_CANONICAL_PATTERNS) {
            if (item.regex.containsMatchIn(text)) {
                return item.canonical
            }
        }
        return ""
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
