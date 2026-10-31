package com.example.visitorcapture

/** What the card reader managed to pull out of the text on a business card. */
data class Parsed(
    val name: String = "", val title: String = "", val company: String = "",
    val email: String = "", val phone: String = "",
    val address: String = "", val state: String = "", val country: String = ""
)

/** State and country lookup used to pre-fill the address fields. Edit the lists to suit your visitors. */
object Places {
    val INDIA_STATES = listOf(
        "Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
        "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
        "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
        "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Delhi", "Jammu and Kashmir", "Ladakh",
        "Puducherry", "Chandigarh", "Andaman and Nicobar Islands", "Lakshadweep",
        "Dadra and Nagar Haveli and Daman and Diu"
    )
    private val STATE_ALIASES = mapOf(
        "Orissa" to "Odisha", "Tamilnadu" to "Tamil Nadu", "Pondicherry" to "Puducherry",
        "Jammu & Kashmir" to "Jammu and Kashmir", "Uttaranchal" to "Uttarakhand",
        "New Delhi" to "Delhi", "NCT of Delhi" to "Delhi"
    )

    /** canonical name -> spellings that may appear on a card */
    private val COUNTRIES: Map<String, List<String>> = buildMap {
        listOf(
            "India", "China", "Bangladesh", "Pakistan", "Sri Lanka", "Nepal", "Bhutan", "Vietnam", "Indonesia",
            "Thailand", "Malaysia", "Singapore", "Myanmar", "Cambodia", "Philippines", "Japan", "South Korea",
            "Taiwan", "Hong Kong", "Egypt", "Uzbekistan", "Ethiopia", "Kenya", "Tanzania", "Nigeria", "South Africa",
            "Germany", "Switzerland", "Italy", "Spain", "Portugal", "France", "Belgium", "Netherlands", "Austria",
            "Poland", "Czech Republic", "Greece", "Russia", "Iran", "Saudi Arabia", "Oman", "Qatar", "Kuwait",
            "Bahrain", "Brazil", "Argentina", "Peru", "Mexico", "Colombia", "Australia", "New Zealand", "Canada"
        ).forEach { put(it, listOf(it)) }
        put("Turkey", listOf("Turkey", "Türkiye", "Turkiye"))
        put("United States", listOf("United States of America", "United States", "U.S.A.", "U.S.A", "USA"))
        put("United Kingdom", listOf("United Kingdom", "Great Britain", "U.K.", "UK"))
        put("United Arab Emirates", listOf("United Arab Emirates", "U.A.E.", "UAE"))
    }

    private fun rx(term: String): Regex {
        // Short spellings (UK, USA, UAE) are matched case-sensitively so they do not fire inside ordinary words.
        val opts = if (term.length <= 4) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return Regex("(?<!\\p{L})" + Regex.escape(term) + "(?!\\p{L})", opts)
    }

    private val stateRx: List<Pair<String, Regex>> =
        (INDIA_STATES.map { it to it } + STATE_ALIASES.map { (alias, canon) -> alias to canon })
            .map { (term, canon) -> canon to rx(term) }
    private val countryRx: List<Pair<String, Regex>> =
        COUNTRIES.flatMap { (canon, names) -> names.map { canon to rx(it) } }

    private fun earliest(text: String, list: List<Pair<String, Regex>>): String? =
        list.mapNotNull { (canon, r) -> r.find(text)?.let { canon to it.range.first } }.minByOrNull { it.second }?.first

    fun findState(text: String): String? = earliest(text, stateRx)
    fun findCountry(text: String): String? = earliest(text, countryRx)
}

private val EMAIL_RX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val PHONE_RX = Regex("(\\+?\\d[\\d\\s().-]{7,}\\d)")
// Legal suffixes are trusted on their own; business words ("Engineering", "Services") also appear in job titles.
private val LEGAL_RX = Regex("(?i)\\b(ltd|limited|inc|gmbh|pvt|private|llp|llc|corp|corporation|co\\.|company)\\b")
private val BUSINESS_RX = Regex("(?i)\\b(industries|group|systems|enterprises?|traders|trading|solutions|technologies|tech|engineering|engineers|works|associates|exports?|international|agencies|mills|textiles|labs|consultancy|services)\\b")
private val TITLE_RX = Regex("(?i)\\b(manager|director|engineer|head|sales|officer|president|owner|executive|procurement|purchase|partner|founder|" +
    "designer|consultant|ceo|cto|cfo|coo|md|managing|proprietor|chairman|vp|vice|associate|analyst|developer|architect|specialist|" +
    "coordinator|representative|supervisor|lead|advisor|adviser|accountant|administrator|assistant|gm|agm|dgm|technician|chief|" +
    "marketing|business development|in-charge|incharge|admin|hr)\\b")
private val ADDR_WORDS = Regex("(?i)\\b(road|rd|street|st|floor|plot|nagar|industrial|estate|sector|phase|avenue|ave|opp|opposite|survey|bldg|building|colony|village|taluka|district|highway|hwy|gidc|midc|pincode|pin|zip|po box|p\\.o\\.)\\b")
private val PIN_RX = Regex("\\b\\d{3}\\s?\\d{3}\\b|\\b\\d{5}(?:-\\d{4})?\\b")
private val LABEL_RX = Regex("(?i)^(www\\.|https?://|tel|ph|phone|mob|mobile|fax|email|e-mail|web|website|gst|gstin|cin|vat)\\b")
private val DOMAIN_RX = Regex("(?i)\\b[a-z0-9-]+\\.(com|net|org|in)\\b")

private fun isContactLine(l: String) =
    l.contains("@") || PHONE_RX.containsMatchIn(l) || LABEL_RX.containsMatchIn(l) || DOMAIN_RX.containsMatchIn(l)

private fun isAddressLine(l: String) = !isContactLine(l) && (
    ADDR_WORDS.containsMatchIn(l) || PIN_RX.containsMatchIn(l) ||
        Places.findState(l) != null || Places.findCountry(l) != null ||
        (l.any(Char::isDigit) && l.contains(','))
    )

private val HOST_RX = Regex("(?i)\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}\\b")
private val SECOND_LEVEL = setOf("com", "co", "net", "org", "ac", "gov", "edu")
private val FREE_MAIL = setOf("gmail", "googlemail", "yahoo", "hotmail", "outlook", "rediffmail", "live", "icloud", "aol",
    "proton", "protonmail", "zoho", "ymail", "msn")
private val NAME_STOP = setOf("to", "a", "an", "the", "of", "and", "for", "in", "with", "by", "at", "on")

/** Brand part of each email/web domain on the card, e.g. "sales@sendka.com" -> "sendka". Free-mail domains are skipped. */
private fun domainStems(contactLines: List<String>): List<String> =
    contactLines.flatMap { l -> HOST_RX.findAll(l).map { it.value.lowercase() }.toList() }
        .mapNotNull { host ->
            val labels = host.split('.').dropLast(1).toMutableList()
            while (labels.size > 1 && labels.last() in SECOND_LEVEL) labels.removeAt(labels.lastIndex)
            labels.lastOrNull()?.replace("-", "")?.takeIf { it.length >= 3 && it !in FREE_MAIL }
        }.distinct()

private fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** True when a card line reads like the brand in the email/web domain (tolerates one OCR slip). */
private fun matchesStem(line: String, stem: String): Boolean {
    val key = line.lowercase().filter(Char::isLetterOrDigit)
    val shorter = minOf(key.length, stem.length)
    return key == stem ||
        (shorter >= 4 && (key.startsWith(stem) || stem.startsWith(key))) ||
        (shorter >= 5 && editDistance(key, stem) <= 1)
}

/** 2-4 words, each starting with a capital (initials like "N." allowed), no digits, symbols or filler words. */
private fun looksLikeName(l: String): Boolean {
    if (l.any { it.isDigit() || it in "&|/@:" }) return false
    val words = l.split(Regex("\\s+"))
    return words.size in 2..4 && words.all { w ->
        val core = w.trimEnd('.', ',')
        core.isNotEmpty() && core.first().isUpperCase() && core.all { it.isLetter() || it == '\'' || it == '-' } &&
            core.lowercase() !in NAME_STOP
    }
}

/** Best-effort guesses; the visitor confirms every field on the Details screen. */
fun parseCard(text: String): Parsed {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val email = EMAIL_RX.find(text)?.value ?: ""
    val phone = PHONE_RX.find(text)?.value?.trim() ?: ""
    val stems = domainStems(lines.filter(::isContactLine))
    val plain = lines.filter { !isContactLine(it) && !isAddressLine(it) }

    val company = plain.firstOrNull { l -> stems.any { matchesStem(l, it) } }
        ?: plain.firstOrNull { LEGAL_RX.containsMatchIn(it) }
        ?: plain.firstOrNull { BUSINESS_RX.containsMatchIn(it) && !TITLE_RX.containsMatchIn(it) }
        ?: ""
    val title = plain.firstOrNull {
        it != company && TITLE_RX.containsMatchIn(it) && !it.contains('|') && it.split(Regex("\\s+")).size <= 6
    } ?: ""

    // Score name candidates: sitting next to the designation, or matching the email's local part, beats line order.
    val titleAt = lines.indexOf(title).takeIf { title.isNotEmpty() }
    val locals = EMAIL_RX.findAll(text).map { it.value.substringBefore('@').lowercase() }.toList()
    val name = plain.filter { l ->
        l != company && l != title && looksLikeName(l) && !LEGAL_RX.containsMatchIn(l) && !BUSINESS_RX.containsMatchIn(l) &&
            !TITLE_RX.containsMatchIn(l) && stems.none { matchesStem(l, it) }
    }.maxByOrNull { l ->
        val at = lines.indexOf(l)
        val nearTitle = if (titleAt != null && (at == titleAt - 1 || at == titleAt + 1)) 3 else 0
        val words = l.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
        val inEmail = if (words.any { w -> locals.any { it.contains(w) } }) 2 else 0
        nearTitle + inEmail
    } ?: ""
    val address = lines.filter { it != company && it != title && it != name && isAddressLine(it) }
        .joinToString(", ") { it.trimEnd(',', ';', ' ') }
    val state = Places.findState(address) ?: Places.findState(text) ?: ""
    val country = Places.findCountry(address) ?: Places.findCountry(text) ?: if (state.isNotEmpty()) "India" else ""
    return Parsed(name, title, company, email, phone, address, state, country)
}
