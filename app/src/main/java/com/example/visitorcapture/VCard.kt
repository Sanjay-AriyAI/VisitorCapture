package com.example.visitorcapture

/** A staff member, as read from their vCard (or MeCard) QR code. Pure Kotlin so it can be unit-tested. */
data class EmployeeInfo(
    val name: String,
    val title: String = "",
    val email: String = "",
    val phone: String = "",
    val company: String = "",
    val raw: String = ""            // the text of the QR code, kept for audit
) {
    /** Stable identity used to recognise the same person on every phone: email, else phone digits, else name. */
    val key: String
        get() = email.trim().lowercase()
            .ifBlank { phone.filter(Char::isDigit) }
            .ifBlank { name.trim().lowercase() }
}

object VCard {
    /** Returns null when [raw] is not a contact QR code (for example a web link). */
    fun parse(raw: String): EmployeeInfo? {
        val text = raw.trim()
        return when {
            text.startsWith("BEGIN:VCARD", ignoreCase = true) -> parseVCard(text)
            text.startsWith("MECARD:", ignoreCase = true) -> parseMeCard(text)
            else -> null
        }
    }

    private val UNESCAPED_SEMI = Regex("(?<!\\\\);")

    private fun unescape(s: String): String =
        Regex("\\\\(.)").replace(s) { m ->
            when (m.groupValues[1]) { "n", "N" -> " "; else -> m.groupValues[1] }
        }.trim()

    private fun parseVCard(text: String): EmployeeInfo? {
        // Undo line folding (a line starting with a space or tab continues the previous one).
        val unfolded = text.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "")
        var fn = ""; var n = ""; var org = ""; var title = ""; var tel = ""; var email = ""
        for (line in unfolded.lines()) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val prop = head.substringBefore(';').substringAfterLast('.').trim().uppercase()   // drop group prefix
            when (prop) {
                "FN" -> if (fn.isEmpty()) fn = unescape(value)
                "N" -> if (n.isEmpty()) {
                    val p = value.split(UNESCAPED_SEMI).map(::unescape)      // family;given;additional;prefix;suffix
                    n = listOf(p.getOrNull(1), p.getOrNull(2), p.getOrNull(0))
                        .filter { !it.isNullOrBlank() }.joinToString(" ")
                }
                "ORG" -> if (org.isEmpty()) org = value.split(UNESCAPED_SEMI).map(::unescape).firstOrNull { it.isNotBlank() } ?: ""
                "TITLE" -> if (title.isEmpty()) title = unescape(value)
                "TEL" -> if (tel.isEmpty()) tel = unescape(value).removePrefix("tel:").trim()
                "EMAIL" -> if (email.isEmpty()) email = unescape(value).removePrefix("mailto:").trim()
            }
        }
        val name = fn.ifBlank { n }.ifBlank { email }
        if (name.isBlank()) return null
        return EmployeeInfo(name = name, title = title, email = email, phone = tel, company = org, raw = text)
    }

    private fun parseMeCard(text: String): EmployeeInfo? {
        var n = ""; var tel = ""; var email = ""; var org = ""
        for (part in text.substring("MECARD:".length).split(UNESCAPED_SEMI)) {
            val colon = part.indexOf(':')
            if (colon <= 0) continue
            val v = unescape(part.substring(colon + 1))
            when (part.substring(0, colon).uppercase()) {
                "N" -> if (n.isEmpty()) n = v.split(",").map { it.trim() }.reversed().filter { it.isNotEmpty() }.joinToString(" ")   // "Last,First"
                "TEL" -> if (tel.isEmpty()) tel = v
                "EMAIL" -> if (email.isEmpty()) email = v
                "ORG" -> if (org.isEmpty()) org = v
            }
        }
        val name = n.ifBlank { email }
        if (name.isBlank()) return null
        return EmployeeInfo(name = name, email = email, phone = tel, company = org, raw = text)
    }
}
