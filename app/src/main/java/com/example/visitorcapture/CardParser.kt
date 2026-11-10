package com.example.visitorcapture

/** One line of recognised text. [height] is the height of its bounding box in pixels (0 when unknown). */
data class OcrLine(val text: String, val height: Int = 0)

/**
 * What the card reader managed to pull out of the text on a business card.
 * [flags] maps a field key ("name", "title", "company", "phone", "email", "address", "state") to a short message
 * telling the visitor why that field needs a second look. A field with no flag is one the parser is confident about.
 */
data class Parsed(
    val name: String = "", val title: String = "", val company: String = "",
    val email: String = "", val phone: String = "",
    val address: String = "", val state: String = "", val country: String = "",
    val flags: Map<String, String> = emptyMap(),
    /** The unprocessed text the scanner read. Only shown in debug builds, to diagnose a bad read. */
    val raw: String = ""
)

/** How much useful contact information a parse found. Used to pick the best of several OCR passes (rotations). */
fun Parsed.score(): Int =
    (if (email.isNotEmpty()) 3 else 0) + (if (phone.isNotEmpty()) 3 else 0) + (if (title.isNotEmpty()) 2 else 0) +
        (if (name.isNotEmpty()) 1 else 0) + (if (company.isNotEmpty()) 1 else 0) + (if (address.isNotEmpty()) 1 else 0)

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

    private val PIN_FIND = Regex("(?<!\\d)([1-9]\\d{2})\\s?(\\d{3})(?!\\d)")

    /** Indian state from the first two digits of a 6-digit PIN code (a few 3-digit exceptions first). Null when unsure. */
    fun stateFromPin(text: String): String? {
        val p3 = PIN_FIND.find(text)?.groupValues?.get(1) ?: return null
        val p2 = p3.take(2).toInt()
        return when {
            p3 == "403" -> "Goa"
            p3 == "605" -> "Puducherry"
            p3 == "160" -> "Chandigarh"
            p2 == 11 -> "Delhi"
            p2 in 12..13 -> "Haryana"
            p2 in 14..16 -> "Punjab"
            p2 == 17 -> "Himachal Pradesh"
            p2 in 30..34 -> "Rajasthan"
            p2 in 36..39 -> "Gujarat"
            p2 in 40..44 -> "Maharashtra"
            p2 == 50 -> "Telangana"
            p2 in 51..53 -> "Andhra Pradesh"
            p2 in 56..59 -> "Karnataka"
            p2 in 60..64 -> "Tamil Nadu"
            p2 in 67..69 -> "Kerala"
            p2 in 70..74 -> "West Bengal"
            p2 in 75..77 -> "Odisha"
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Patterns
// ---------------------------------------------------------------------------------------------------------------------

private val EMAIL_RX = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
private val PHONE_RX = Regex("(\\+?\\d[\\d\\s().-]{7,}\\d)")
// Legal suffixes are trusted on their own; business words ("Engineering", "Services") also appear in job titles.
private val LEGAL_RX = Regex("(?i)\\b(ltd|limited|inc|gmbh|pvt|private|llp|llc|corp|corporation|co\\.|company)\\b")
// Same, without the loose "co." / "company" – safe to trust even on a line that otherwise looks like an address.
private val LEGAL_STRONG_RX = Regex("(?i)\\b(ltd|limited|inc|gmbh|pvt|private|llp|llc|corp|corporation)\\b")
private val BUSINESS_RX = Regex("(?i)\\b(industries|group|systems|enterprises?|traders|trading|solutions|technologies|tech|engineering|engineers|works|associates|exports?|international|agencies|mills|textiles|labs|consultancy|services|" +
    "interiors|hardware|electricals?|electronics|stores|motors|pumps|steels?|pharma|foods|builders|constructions|infra|infrastructure|automation|controls|machines|machinery|tools|bearings|polymers|plastics|chemicals|packaging|logistics|couriers|transport|travels)\\b")
private val TITLE_RX = Regex("(?i)\\b(manager|director|engineer|head|sales|officer|president|owner|executive|procurement|purchase|partner|founder|" +
    "designer|consultant|ceo|cto|cfo|coo|md|managing|proprietor|chairman|vp|vice|associate|analyst|developer|architect|specialist|" +
    "coordinator|representative|supervisor|lead|advisor|adviser|accountant|administrator|assistant|gm|agm|dgm|technician|chief|" +
    "marketing|business development|in-charge|incharge|admin|hr|senior|junior|sr|jr|deputy|regional|territory|zonal)\\b")
private val ADDR_WORDS = Regex("(?i)\\b(road|rd|street|st|floor|plot|nagar|industrial|estate|sector|phase|avenue|ave|opp|opposite|survey|bldg|building|colony|village|taluka|district|highway|hwy|gidc|midc|pincode|pin|zip|po box|p\\.o\\.|compound|lane|layout|block|tower|complex)\\b")
// Abbreviations that also turn up in logos and slogans ("F I R S T" is read as "FIR ST"): only count on a line with a digit or comma.
private val ADDR_WEAK = Regex("(?i)\\b(st|rd|ave|opp|pin|zip)\\b")
private fun hasAddressWord(l: String) =
    ADDR_WORDS.findAll(l).any { m -> !ADDR_WEAK.matches(m.value) } || (ADDR_WEAK.containsMatchIn(l) && (l.any(Char::isDigit) || l.contains(',')))

private val PIN6_RX = Regex("(?<!\\d)\\d{3}\\s?\\d{3}(?!\\d)")
private val ZIP5_RX = Regex("\\b\\d{5}(?:-\\d{4})?\\b")
private val PIN6_LINE_RX = Regex("^\\d{3}\\s?\\d{3}\\.?$")
private val LABEL_RX = Regex("(?i)^(www\\.|https?://|tel|ph|phone|mob|mobile|fax|email|e-mail|web|website|gst|gstin|cin|vat)\\b")
private val DOMAIN_RX = Regex("(?i)\\b[a-z0-9-]+\\.(com|net|org|in)\\b")

private fun letterCount(l: String) = l.count(Char::isLetter)
private fun digitCount(l: String) = l.count(Char::isDigit)

private fun isContactLine(l: String) =
    l.contains("@") || PHONE_RX.containsMatchIn(l) || LABEL_RX.containsMatchIn(l) || DOMAIN_RX.containsMatchIn(l)

/** A line that is almost all digits but is neither a phone number nor a PIN code: a mangled phone fragment such as "4990se 87333". */
private fun isNoise(l: String) =
    !isContactLine(l) && letterCount(l) <= 2 && digitCount(l) >= 4 && !PIN6_LINE_RX.matches(l.trim())

private fun isAddressLine(l: String) = !isContactLine(l) && !isNoise(l) && (
    hasAddressWord(l) || PIN6_RX.containsMatchIn(l) ||
        (letterCount(l) >= 3 && ZIP5_RX.containsMatchIn(l)) ||
        Places.findState(l) != null || Places.findCountry(l) != null ||
        (l.any(Char::isDigit) && l.contains(','))
    )

private val HOST_RX = Regex("(?i)\\b[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}\\b")
private val SECOND_LEVEL = setOf("com", "co", "net", "org", "ac", "gov", "edu")
private val FREE_MAIL = setOf("gmail", "googlemail", "yahoo", "hotmail", "outlook", "rediffmail", "live", "icloud", "aol",
    "proton", "protonmail", "zoho", "ymail", "msn")
private val NAME_STOP = setOf("to", "a", "an", "the", "of", "and", "for", "in", "with", "by", "at", "on")
// The little pin/phone/mail icons beside card lines are read as stray letters: "O 33 Ubi Avenue 3" -> "33 Ubi Avenue 3".
// Only letters icons commonly turn into, and only in front of a number (so "B 12, Sector 4" keeps its block letter).
private val ICON_PREFIX_RX = Regex("^[OoQqVv©®]\\s+(?=\\d)")
private fun stripIconPrefix(line: String) = ICON_PREFIX_RX.replace(line, "")

private val PARENT_RX =Regex("(?i)\\b(unit of|division of|a subsidiary|subsidiary of|formerly|member of)\\b")

/** Drops a bracket the OCR cut in half: "Hardware & Electricals)" -> "Hardware & Electricals". */
private fun cleanCompany(s: String): String {
    var t = s.trim()
    if (t.count { it == '(' } < t.count { it == ')' }) t = t.trimEnd(')').trim()
    if (t.count { it == '(' } > t.count { it == ')' }) t = t.trimStart('(').trim()
    return t
}

// A PIN whose zero was read as the letter O: "Coimbatore - 641 O14".
private val PIN_LOOKALIKE_RX = Regex("(?<=[\\s,-])(\\d[0-9OoIl]{2})\\s?([0-9OoIl]{3})(?=\\.?$)")
private fun repairPin(line: String): String = PIN_LOOKALIKE_RX.replace(line) { m ->
    fun digits(s: String) = s.replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1')
    if (m.value.count(Char::isDigit) >= 3 && letterCount(line) >= 3) digits(m.groupValues[1]) + (if (m.value.contains(' ')) " " else "") + digits(m.groupValues[2]) else m.value
}

private val GENERIC_LABELS =setOf("www", "shop", "mail", "web", "store", "app", "blog", "support")

// ---- OCR damage to e-mail addresses: the '@' read as a look-alike, stray spaces, or lost altogether -----------------
private val AT_LOOKALIKE_RX = Regex("(?<=[A-Za-z0-9])\\s*(?:[©®＠]|\\(\\s*(?:at|a|@)\\s*\\)|\\[\\s*at\\s*\\]|\\{\\s*at\\s*\\})\\s*(?=[A-Za-z0-9])")
private val SPLIT_LOCAL_RX = Regex("^(?:\\S{1,2}\\s+)?([a-z0-9]{4,})\\s+([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,})\\.?$")
private val LABEL_WORDS = setOf("email", "mail", "mailto", "contact", "write", "emailid")
private val SPACED_AT_RX =Regex("\\s*@\\s*")
private val SPACED_DOT_RX = Regex("(@[A-Za-z0-9.-]*\\.)\\s+(?=[a-z]{2,3}\\b)")
private val DROPPED_AT_RX = Regex("^(?:\\S{1,2}\\s+)?([A-Za-z0-9%+-]+[._][A-Za-z0-9._%+-]*[A-Za-z0-9])\\s+((?!www\\.)[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)*\\.(?:com|in|net|org|co))\\.?$")

/** Puts a damaged e-mail line back together: "dharshan.g © idfcfirst.bank. in" -> "dharshan.g@idfcfirst.bank.in". Other lines are returned unchanged. */
internal fun repairEmailLine(line: String): String {
    var s = AT_LOOKALIKE_RX.replace(line, "@")
    if (s.contains('@')) {
        // Accented look-alikes of ASCII letters ("sripoojaın", "gmaıl") would otherwise cut the address short.
        s = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "").replace('ı', 'i')
        // OCR split the local part with a stray space: "sripoojain teriors@gmail.com".
        SPLIT_LOCAL_RX.find(s)?.takeIf { it.groupValues[1] !in LABEL_WORDS && !LABEL_RX.containsMatchIn(s) }?.let {
            s = it.groupValues[1] + it.groupValues[2]
        }
        s = SPACED_AT_RX.replace(s, "@")
        s = SPACED_DOT_RX.replace(s) { it.groupValues[1] }
    } else if (!LABEL_RX.containsMatchIn(s)) {
        DROPPED_AT_RX.find(s)?.let { s = it.groupValues[1] + "@" + it.groupValues[2] }
    }
    return s
}

private val GENERIC_LOCAL_RX = Regex("(?i)^(info|sales|contact|admin|support|enquiry|enquiries|hello|office|mail|care|marketing|service|services|accounts|hr)([._\\-\\d]|$)")

// ---------------------------------------------------------------------------------------------------------------------
// Small helpers
// ---------------------------------------------------------------------------------------------------------------------

private fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        prev = cur
    }
    return prev[b.length]
}

/** Equal, one character off, or two neighbouring characters swapped. */
private fun oneSlip(a: String, b: String): Boolean {
    if (a == b) return true
    if (editDistance(a, b) <= 1) return true
    if (a.length != b.length) return false
    return (0 until a.length - 1).any { i ->
        a[i] != a[i + 1] && a.substring(0, i) + a[i + 1] + a[i] + a.substring(i + 2) == b
    }
}

/** A free-mail brand, or a one-slip misspelling of one ("grmail", "gmial"). Short brands must match exactly. */
private fun isFreeMail(label: String) =
    label in FREE_MAIL || FREE_MAIL.any { it.length >= 5 && label.length >= 5 && oneSlip(label, it) }

private fun freeMailFor(label: String): String? =
    if (label in FREE_MAIL) label else FREE_MAIL.firstOrNull { it.length >= 5 && label.length >= 5 && oneSlip(label, it) }

/** Brand part of each web/e-mail domain on the card, e.g. "sales@sendka.com" -> "sendka". Free-mail domains are skipped. */
private fun domainStems(contactLines: List<String>): List<String> =
    contactLines.flatMap { l ->
        // Only the part after '@' is a domain; the part before it ("anand.balaji") is a person, not a website.
        val hosts = EMAIL_RX.findAll(l).map { it.value.substringAfter('@').lowercase() }.toList()
        val rest = EMAIL_RX.replace(l, " ")
        hosts + HOST_RX.findAll(rest).map { it.value.lowercase() }.toList()
    }.flatMap { host ->
        // Every meaningful label counts: "idfcfirst.bank.in" is the brand "idfcfirst", not just "bank".
        val labels = host.split('.').dropLast(1).toMutableList()
        while (labels.size > 1 && labels.last() in SECOND_LEVEL) labels.removeAt(labels.lastIndex)
        labels.map { it.replace("-", "") }.filter { it.length >= 3 && it !in GENERIC_LABELS && !isFreeMail(it) }
    }.distinct()

private fun firstWordKey(line: String) = line.trim().split(Regex("\\s+")).firstOrNull().orEmpty().lowercase().filter(Char::isLetterOrDigit)

/** True when a card line reads like the brand in the email/web domain (tolerates one OCR slip). */
private fun matchesStem(line: String, stem: String): Boolean {
    val key = line.lowercase().filter(Char::isLetterOrDigit)
    val shorter = minOf(key.length, stem.length)
    return key == stem ||
        (shorter >= 4 && (key.startsWith(stem) || stem.startsWith(key))) ||
        (shorter >= 5 && editDistance(key, stem) <= 1) ||
        // "DHL Express (India) Pvt. Ltd." for dhl.com: a short brand leading a longer company line
        (stem.length >= 3 && firstWordKey(line) == stem)
}

/** 2-4 words, each starting with a capital (initials like "N." allowed), no digits, symbols or filler words. */
private fun looksLikeName(l: String, allowStopWords: Boolean = false): Boolean {
    if (l.any { it.isDigit() || it in "&|/@:" }) return false
    val words = l.split(Regex("\\s+"))
    return words.size in 2..4 && words.all { w ->
        val core = w.trimEnd('.', ',')
        // Filler words ("To", "For") rule out slogans like "Welcome To Naren". They are allowed when something else vouches
        // for the line (it matches the e-mail address or sits next to the job title): "AT Kumar" is a name, not "at".
        core.isNotEmpty() && core.first().isUpperCase() && core.all { it.isLetter() || it == '\'' || it == '-' } &&
            (core.length == 1 || allowStopWords || core.lowercase() !in NAME_STOP)
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Job-title spelling repair
// ---------------------------------------------------------------------------------------------------------------------

private val TITLE_VOCAB = listOf(
    "manager", "director", "engineer", "executive", "officer", "senior", "junior", "assistant", "associate", "proprietor",
    "president", "consultant", "designer", "developer", "architect", "specialist", "coordinator", "representative",
    "supervisor", "accountant", "administrator", "technician", "marketing", "procurement", "purchase", "founder",
    "partner", "chairman", "managing", "territory", "regional", "general", "deputy", "analyst", "advisor", "adviser",
    "business", "development", "sales", "service", "technical", "production", "quality", "executive"
)

private fun matchCase(original: String, fixed: String) = when {
    original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> fixed.uppercase()
    original.first().isUpperCase() -> fixed.replaceFirstChar { it.uppercase() }
    else -> fixed
}

/**
 * Repairs OCR slips in a line that is clearly a job title ("Serior Sales Executive" -> "Senior Sales Executive").
 * Only lines that already contain a real title word are touched, and only words one slip away from a known title word.
 */
private fun repairTitle(line: String): String {
    if (!TITLE_RX.containsMatchIn(line) || isContactLine(line) || line.split(Regex("\\s+")).size > 6) return line
    return Regex("[\\p{L}]{5,}").replace(line) { m ->
        val w = m.value
        val lw = w.lowercase()
        if (lw in TITLE_VOCAB) w
        else TITLE_VOCAB.firstOrNull { it.length >= 5 && editDistance(lw, it) == 1 }?.let { matchCase(w, it) } ?: w
    }
}

// ---------------------------------------------------------------------------------------------------------------------
// Phone numbers
// ---------------------------------------------------------------------------------------------------------------------

private val MOBILE_LABEL_RX = Regex("(?i)\\b(mobile|mob|cell|whatsapp|m)\\s*[:.\\-]|whatsapp")
private val NOT_DIRECT_RX = Regex("(?i)\\b(fax|customer\\s*service|toll|helpline|e\\s*comm|support|care)\\b")

private data class PhoneCand(val value: String, val score: Int)

private fun phoneCandidates(lines: List<String>): List<PhoneCand> = lines.flatMap { line ->
    PHONE_RX.findAll(line).mapNotNull { m ->
        val value = m.value.trim()
        val digits = value.filter(Char::isDigit)
        if (digits.length !in 10..13) return@mapNotNull null
        val national = when {
            digits.startsWith("91") && digits.length == 12 -> digits.drop(2)
            digits.startsWith("0") -> digits.drop(1)
            else -> digits
        }
        var s = 0
        if (MOBILE_LABEL_RX.containsMatchIn(line)) s += 3
        if (Regex("^[6-9]\\d{9}$").matches(national)) s += 2 else if (value.startsWith("+")) s += 1
        if (NOT_DIRECT_RX.containsMatchIn(line) || national.startsWith("1800") || national.startsWith("1860")) s -= 5
        // Indian mobiles are written one way: "+91 98765 43210" / "98765 43210" (OCR often drops or moves the spaces).
        val shown = if (Regex("^[6-9]\\d{9}$").matches(national) && (digits.length == 10 || digits.length == 12 && digits.startsWith("91"))) {
            val grouped = national.take(5) + " " + national.drop(5)
            if (digits.length == 12) "+91 $grouped" else grouped
        } else value
        PhoneCand(shown, s)
    }.toList()
}

// ---------------------------------------------------------------------------------------------------------------------
// E-mail addresses
// ---------------------------------------------------------------------------------------------------------------------

private data class EmailCand(val value: String, val corrected: Boolean)

/** Fixes a misspelt free-mail domain ("name@grmail.com" -> "name@gmail.com"). Anything else is left as read. */
private fun fixEmail(raw: String): EmailCand {
    val local = raw.substringBefore('@')
    val labels = raw.substringAfter('@').lowercase().split('.').toMutableList()
    if (labels.size < 2) return EmailCand(raw, false)
    var changed = false
    // ".corm", ".con", ".comm": a top-level domain that does not exist but is one slip away from ".com"
    val tld = labels.last()
    if (tld !in KNOWN_TLDS && tld.length in 2..4 && editDistance(tld, "com") <= 1) { labels[labels.lastIndex] = "com"; changed = true }
    val brand = labels[labels.size - 2]
    freeMailFor(brand)?.takeIf { it != brand }?.let { labels[labels.size - 2] = it; changed = true }
    return EmailCand("$local@${labels.joinToString(".")}", changed)
}

private val KNOWN_TLDS = setOf(
    "com", "net", "org", "edu", "gov", "mil", "int", "info", "biz", "io", "co", "in", "uk", "us", "de", "fr", "cn", "jp", "au", "ca",
    "ae", "sg", "my", "id", "nz", "za", "ch", "it", "es", "nl", "se", "no", "fi", "dk", "pl", "ru", "br", "mx", "ar", "kr", "tw", "hk",
    "tr", "sa", "eg", "ng", "ke", "lk", "bd", "pk", "np", "vn", "th", "ph", "cm", "om", "ac", "ai", "me", "tv", "cc", "xyz", "tech",
    "online", "store", "shop", "app", "dev", "bank", "ind", "res", "nic"
)

// ---------------------------------------------------------------------------------------------------------------------
// The parser
// ---------------------------------------------------------------------------------------------------------------------

/** Plain-text entry point (no layout information). */
fun parseCard(text: String): Parsed = parseLines(text.lines().map { OcrLine(it) })

/** Best-effort guesses; every field the parser is unsure about is flagged for the visitor to confirm. */
fun parseLines(raw: List<OcrLine>): Parsed {
    val flags = linkedMapOf<String, String>()
    val repairedTitles = mutableSetOf<String>()
    var emailRepaired = false
    val lines = raw.map { OcrLine(it.text.trim(), it.height) }.filter { it.text.isNotEmpty() }.map {
        val mended = repairEmailLine(it.text)
        if (mended != it.text && EMAIL_RX.containsMatchIn(mended) &&
            EMAIL_RX.findAll(mended).map { m -> m.value }.toList() != EMAIL_RX.findAll(it.text).map { m -> m.value }.toList()) emailRepaired = true
        OcrLine(stripIconPrefix(repairPin(mended)), it.height)
    }.map {
        val fixed = repairTitle(it.text)
        if (fixed != it.text) repairedTitles += fixed
        OcrLine(fixed, it.height)
    }
    val texts = lines.map { it.text }
    val text = texts.joinToString("\n")
    val n = lines.size

    // Classify every line once.
    val contact = texts.map(::isContactLine)
    val noise = texts.map(::isNoise)
    val addr = texts.map(::isAddressLine).toMutableList()
    // "Press Compound," / "Peelamedu," carry no address keyword of their own but continue an address that has a trailing comma.
    for (i in 0 until n) {
        if (addr[i] || contact[i] || noise[i] || !texts[i].trimEnd().endsWith(',')) continue
        if (TITLE_RX.containsMatchIn(texts[i]) || LEGAL_STRONG_RX.containsMatchIn(texts[i])) continue
        if ((i > 0 && addr[i - 1]) || (i < n - 1 && addr[i + 1])) addr[i] = true
    }

    val stems = domainStems(texts.filterIndexed { i, _ -> contact[i] })
    fun stemMatch(l: String) = stems.any { matchesStem(l, it) }

    // ---- company: brand in the e-mail/web domain > legal suffix > business word -------------------------------------
    // A company line may mention a country ("DHL Express (India) Pvt. Ltd."), so address-looking lines with a legal
    // suffix and no digits stay eligible.
    val pool = (0 until n).filter { i ->
        !contact[i] && !noise[i] &&
            (!addr[i] || (LEGAL_STRONG_RX.containsMatchIn(texts[i]) && texts[i].none(Char::isDigit)))
    }
    // Among lines that merely contain a business word: one echoed in an e-mail address is the company's own name
    // ("INTERIORS" for sripoojainteriors@gmail.com); a "(A Unit of ...)" tagline is a parent company, so it ranks last.
    val localKeys = EMAIL_RX.findAll(text).map { it.value.substringBefore('@').lowercase().filter(Char::isLetterOrDigit) }.toList()
    fun businessScore(i: Int): Int {
        val key = texts[i].lowercase().filter(Char::isLetterOrDigit)
        val echoed = if (key.length >= 5 && localKeys.any { it.contains(key) }) 3 else 0
        val tagline = if (texts[i].trimStart().startsWith("(") || PARENT_RX.containsMatchIn(texts[i])) -2 else 0
        return echoed + tagline
    }

    // The longest matching brand wins ("idfcfirst" over "bank"); the sort is stable, so card order breaks ties.
    fun stemStrength(i: Int) = stems.filter { matchesStem(texts[i], it) }.maxOfOrNull { it.length } ?: 0
    val stemLines = pool.filter { stemMatch(texts[it]) }.sortedByDescending(::stemStrength)
    val companyIdx: Int? =
        stemLines.firstOrNull { LEGAL_RX.containsMatchIn(texts[it]) }   // "DHL Express (India) Pvt. Ltd." over the "DHL ..." tagline
            ?: stemLines.firstOrNull()
            ?: pool.firstOrNull { LEGAL_RX.containsMatchIn(texts[it]) }
            ?: pool.filter { BUSINESS_RX.containsMatchIn(texts[it]) && !TITLE_RX.containsMatchIn(texts[it]) }
                .maxByOrNull(::businessScore)   // maxBy keeps the first of equals, so card order still breaks ties
    // A logo is a picture, not text. When no line names the company, the brand in the e-mail/web domain is the best
    // available guess ("atkumar@aladdin365.com" -> "Aladdin365"); it is flagged for the visitor to confirm.
    val domainGuess = if (companyIdx == null) stems.maxByOrNull { it.length }?.replaceFirstChar { it.uppercase() } else null
    val company = companyIdx?.let { cleanCompany(texts[it]) } ?: domainGuess ?: ""

    val plain = (0 until n).filter { !contact[it] && !noise[it] && !addr[it] && it != companyIdx }

    // ---- title ---------------------------------------------------------------------------------------------------
    val titleIdx = plain.firstOrNull {
        TITLE_RX.containsMatchIn(texts[it]) && !texts[it].contains('|') && texts[it].split(Regex("\\s+")).size <= 6
    }
    val title = titleIdx?.let { texts[it] } ?: ""
    if (title in repairedTitles) flags["title"] = "Spelling was corrected. Please check."

    // ---- e-mail --------------------------------------------------------------------------------------------------
    val emails = EMAIL_RX.findAll(text).map { fixEmail(it.value) }.distinctBy { it.value.lowercase() }.toList()
    val locals = emails.map { it.value.substringBefore('@').lowercase() }

    // ---- name: sitting next to the designation, matching an e-mail address, or standing out in size ----------------
    val localLetters = locals.map { it.filter(Char::isLetter) }
    fun vouched(i: Int): Boolean {
        val key = texts[i].lowercase().filter(Char::isLetter)
        val nearTitle = titleIdx != null && (i == titleIdx - 1 || i == titleIdx + 1)
        return nearTitle || (key.length >= 4 && localLetters.any { it.contains(key) })
    }
    val candidates = plain.filter { i ->
        val l = texts[i]
        i != titleIdx && looksLikeName(l, vouched(i)) && !LEGAL_RX.containsMatchIn(l) && !BUSINESS_RX.containsMatchIn(l) &&
            !TITLE_RX.containsMatchIn(l) && !ADDR_WORDS.containsMatchIn(l) && !l.trimEnd().endsWith(',') && !stemMatch(l)
    }
    val tallest = candidates.maxOfOrNull { lines[it].height } ?: 0
    fun nameScore(i: Int): Int {
        val nearTitle = if (titleIdx != null && (i == titleIdx - 1 || i == titleIdx + 1)) 3 else 0
        val words = texts[i].lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
        val inEmail = if (words.any { w -> locals.any { it.contains(w) } }) 2 else 0
        val tall = if (tallest > 0 && lines[i].height >= tallest * 0.9) 1 else 0
        return nearTitle + inEmail + tall
    }
    val nameIdx = candidates.maxByOrNull(::nameScore)
    val name = nameIdx?.let { texts[it] } ?: ""
    if (nameIdx != null && nameScore(nameIdx) == 0 && candidates.size > 1) flags["name"] = "Not sure this is the right name. Please check."

    // ---- final e-mail pick: one that matches the person, then a personal-looking one, then the first ---------------
    val nameWords = name.lowercase().split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
    val email = emails.firstOrNull { e -> nameWords.any { e.value.substringBefore('@').lowercase().contains(it) } }
        ?: emails.firstOrNull { !GENERIC_LOCAL_RX.containsMatchIn(it.value.substringBefore('@')) }
        ?: emails.firstOrNull()
    if (email != null && (email.corrected || emailRepaired)) flags["email"] = "This was misread or misspelt on the card and was repaired. Please check it."

    // ---- phone ---------------------------------------------------------------------------------------------------
    val phoneCand = phoneCandidates(texts.filterIndexed { i, _ -> contact[i] }).maxByOrNull { it.score }
    // A helpline / toll-free / fax number is not the visitor's number: leave the field empty rather than fill it wrongly.
    val phone = phoneCand?.takeIf { it.score >= 0 }?.value ?: ""
    val onlyHelpline = phoneCand != null && phone.isEmpty()

    // ---- address / state / country ------------------------------------------------------------------------------
    val address = (0 until n).filter { addr[it] && it != companyIdx && it != titleIdx && it != nameIdx }
        .joinToString(", ") { texts[it].trimEnd(',', ';', ' ') }.trimEnd('.', ' ')
    var state = Places.findState(address) ?: Places.findState(text) ?: ""
    var country = Places.findCountry(address) ?: Places.findCountry(text) ?: ""
    if (state.isEmpty() && (country.isEmpty() || country == "India")) {
        Places.stateFromPin(address)?.let { state = it; flags["state"] = "Filled in from the PIN code. Please check." }
    }
    if (country.isEmpty() && state.isNotEmpty()) country = "India"

    // ---- fields the card should have but we could not read --------------------------------------------------------
    val missing = "Couldn't read this from the card. Please type it in."
    if (name.isEmpty()) flags["name"] = missing
    if (phone.isEmpty()) flags["phone"] = if (onlyHelpline) "Only a helpline number was found on the card. Please type the direct number." else missing
    if (email == null) flags["email"] = missing
    if (company.isEmpty()) flags["company"] = "Not read from the card. Add it if it is printed there."
    else if (domainGuess != null) flags["company"] = "Guessed from the email domain. Please check it against the card."

    return Parsed(name, title, company, email?.value ?: "", phone, address, state, country, flags)
}
