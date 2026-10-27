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
private val COMPANY_RX = Regex("(?i)\\b(ltd|limited|inc|gmbh|pvt|llc|corp|co\\.|company|industries|group|systems)\\b")
private val TITLE_RX = Regex("(?i)\\b(manager|director|engineer|head|sales|officer|president|owner|executive|procurement|purchase|partner|founder)\\b")
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

/** Best-effort guesses; the visitor confirms every field on the Details screen. */
fun parseCard(text: String): Parsed {
    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val email = EMAIL_RX.find(text)?.value ?: ""
    val phone = PHONE_RX.find(text)?.value?.trim() ?: ""
    val company = lines.firstOrNull { COMPANY_RX.containsMatchIn(it) && !isContactLine(it) && !isAddressLine(it) } ?: ""
    val title = lines.firstOrNull { it != company && TITLE_RX.containsMatchIn(it) && !isContactLine(it) && !isAddressLine(it) } ?: ""
    val name = lines.firstOrNull {
        it != company && it != title && !isContactLine(it) && !isAddressLine(it) &&
            it.none(Char::isDigit) && it.split(" ").size in 2..4
    } ?: ""
    val address = lines.filter { it != company && it != title && it != name && isAddressLine(it) }
        .joinToString(", ") { it.trimEnd(',', ';', ' ') }
    val state = Places.findState(address) ?: Places.findState(text) ?: ""
    val country = Places.findCountry(address) ?: Places.findCountry(text) ?: if (state.isNotEmpty()) "India" else ""
    return Parsed(name, title, company, email, phone, address, state, country)
}
