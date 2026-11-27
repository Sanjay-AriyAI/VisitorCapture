package com.example.visitorcapture

/**
 * Indian phone formats, used where [BrandSpec.indianPhones] is on (LRT):
 * mobiles as 10 plain digits ("9876543210"), landlines as STD code + number ("0422 2673333").
 */
object IndianPhone {
    private val MOBILE_RX = Regex("[6-9]\\d{9}")
    // Two-digit STD codes (Delhi, Pune, Mumbai, Kolkata, Hyderabad, Chennai, Ahmedabad, Bengaluru); the rest are 3+ digits.
    private val METRO = setOf("11", "20", "22", "33", "40", "44", "79", "80")

    /** National digits: "+91 98765 43210", "098765 43210", "91-422-2673333" lose the 91 / 0 prefix. */
    private fun national(s: String): String {
        val d = s.filter(Char::isDigit)
        return when {
            d.length >= 11 && d.startsWith("91") -> d.drop(2)
            d.startsWith("0") -> d.dropWhile { it == '0' }
            else -> d
        }
    }

    /** The 10-digit mobile number in [s], or null if it is not an Indian mobile. */
    fun mobile(s: String): String? = national(s).takeIf { MOBILE_RX.matches(it) }

    /** Landline as "0422 2673333" (STD code with its 0, a space, the number); null if [s] is not a landline. */
    fun office(s: String): String? {
        val n = national(s)
        if (n.length !in 9..10 || MOBILE_RX.matches(n)) return null
        val std = if (n.take(2) in METRO) 2 else 3
        return "0" + n.take(std) + " " + n.drop(std)
    }

    /** What is kept while typing a mobile: digits only, at most 10 (a pasted "+91 ..." is reduced to its 10 digits). */
    fun typingMobile(s: String): String = mobile(s) ?: s.filter(Char::isDigit).take(10)

    /** What is kept while typing an office number: digits and spaces (a dash becomes a space). */
    fun typingOffice(s: String): String = s.replace('-', ' ').filter { it.isDigit() || it == ' ' }.take(16)

    /** India (or blank) means the Indian formats apply; numbers of visitors from other countries are left as typed. */
    fun applies(country: String, homeCountry: String): Boolean =
        country.isBlank() || marketFromCountry(country, homeCountry) == "Domestic Visitor"
}
