package com.example.visitorcapture

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** SMTP account used for the thank-you mail. Entered on the Mail settings screen; build-time values are only the default. */
data class SmtpSettings(
    val host: String = "", val port: Int = 587, val user: String = "", val password: String = "",
    val from: String = "", val fromName: String = ""
) {
    val complete get() = host.isNotBlank() && user.isNotBlank() && password.isNotBlank()

    companion object {
        private const val P = "smtp."
        fun load(ctx: Context): SmtpSettings {
            val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
            return SmtpSettings(
                host = p.getString(P + "host", null) ?: BuildConfig.SMTP_HOST,
                port = p.getInt(P + "port", BuildConfig.SMTP_PORT),
                user = p.getString(P + "user", null) ?: BuildConfig.SMTP_USER,
                password = p.getString(P + "password", null) ?: BuildConfig.SMTP_PASSWORD,
                from = p.getString(P + "from", null) ?: BuildConfig.SMTP_FROM,
                fromName = p.getString(P + "fromName", null) ?: BuildConfig.SMTP_FROM_NAME)
        }
        fun save(ctx: Context, s: SmtpSettings) {
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(P + "host", s.host.trim()).putInt(P + "port", s.port)
                .putString(P + "user", s.user.trim()).putString(P + "password", s.password)
                .putString(P + "from", s.from.trim()).putString(P + "fromName", s.fromName.trim()).apply()
        }
    }
}

/** Status values stored in Visitor.thankYouStatus. */
object ThankYou {
    const val PENDING = "PENDING"   // saved with a valid e-mail, not mailed yet
    const val QUEUED = "QUEUED"     // staff pressed "Send thank-you mails": goes out as soon as the phone is online
    const val SENT = "SENT"
    const val FAILED = "FAILED"

    private val EMAIL_RX = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun isValidEmail(s: String) = EMAIL_RX.matches(s.trim())

    /** Initial status for a freshly saved visitor: only a plausible address is ever queued. */
    fun initialStatus(email: String) = if (isValidEmail(email)) PENDING else ""

    fun subject(company: String) = "Thank you for visiting $company"

    /** Plain-text body, personalised from what was recorded during the visit. Pure function so it can be unit-tested. */
    fun body(v: Visitor, company: String): String {
        val first = v.name.trim().ifBlank { "" }
        val sb = StringBuilder()
        sb.append(if (first.isNotEmpty()) "Dear $first,\n\n" else "Dear Sir/Madam,\n\n")
        sb.append("Thank you for visiting the $company stand")
        if (v.attendedByName.isNotBlank()) sb.append(" and for taking the time to speak with ${v.attendedByName}")
        sb.append(". It was a pleasure to meet")
        sb.append(if (v.company.isNotBlank()) " you and learn about ${v.company}.\n\n" else " you.\n\n")

        val interests = Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() })
        if (interests.isNotBlank() || v.additionalInterest.isNotBlank()) {
            sb.append("You showed interest in:\n")
            interests.lines().filter { it.isNotBlank() }.forEach { sb.append("  - ").append(it.trim()).append('\n') }
            if (v.additionalInterest.isNotBlank()) sb.append("  - ").append(v.additionalInterest.trim()).append('\n')
            sb.append('\n')
        }

        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy 'at' HH:mm", Locale.getDefault())
        val next = when (v.followUpAction) {
            "Send Quotation" -> "We will send you the quotation shortly" +
                (if (v.followUpAt > 0) ", by ${SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(v.followUpAt))}." else ".")
            "Schedule Factory Visit" -> if (v.followUpAt > 0) "We look forward to welcoming you at our factory on ${fmt.format(Date(v.followUpAt))}."
                else "We will be in touch to arrange your factory visit."
            "Automatic E-mail Reminder" -> "We will follow up with you by e-mail."
            else -> when {
                "Sample Request" in v.leadOutcomes -> "We will get back to you shortly regarding your sample request."
                "Order" in v.leadOutcomes -> "We will confirm the details of your order shortly."
                "Factory Visit" in v.leadOutcomes -> "We will be in touch to arrange your factory visit."
                v.remarks == "Call Back" -> "Our team will call you back soon."
                else -> ""
            }
        }
        if (next.isNotEmpty()) sb.append(next).append("\n\n")

        sb.append("If you have any questions in the meantime, just reply to this e-mail")
        sb.append(if (v.attendedByPhone.isNotBlank()) " or call ${v.attendedByPhone.trim()}.\n\n" else ".\n\n")
        sb.append("Warm regards,\n")
        sb.append(v.attendedByName.ifBlank { company }).append('\n')
        if (v.attendedByTitle.isNotBlank()) sb.append(v.attendedByTitle.trim()).append('\n')
        sb.append(company).append('\n')
        if (v.attendedByPhone.isNotBlank()) sb.append(v.attendedByPhone.trim()).append('\n')
        if (v.attendedByEmail.isNotBlank()) sb.append(v.attendedByEmail.trim()).append('\n')
        return sb.toString()
    }

    private fun session(c: SmtpSettings): Session {
        val props = Properties().apply {
            put("mail.smtp.host", c.host.trim())
            put("mail.smtp.port", c.port.toString())
            put("mail.smtp.auth", "true")
            if (c.port == 465) put("mail.smtp.ssl.enable", "true")
            else { put("mail.smtp.starttls.enable", "true"); put("mail.smtp.starttls.required", "true") }
            put("mail.smtp.connectiontimeout", "15000")
            put("mail.smtp.timeout", "20000")
            put("mail.smtp.writetimeout", "20000")
        }
        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(c.user.trim(), c.password)
        })
    }

    private fun fromAddress(c: SmtpSettings, name: String) = InternetAddress(c.from.ifBlank { c.user }.trim(), name, "UTF-8")

    /** Blocking SMTP send; throws javax.mail.MessagingException on any failure. */
    fun send(c: SmtpSettings, v: Visitor) {
        val company = c.fromName.ifBlank { "our company" }
        val msg = MimeMessage(session(c)).apply {
            setFrom(fromAddress(c, company))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(v.email.trim(), true))
            if (isValidEmail(v.attendedByEmail)) replyTo = arrayOf(InternetAddress(v.attendedByEmail.trim(), v.attendedByName.ifBlank { null }, "UTF-8"))
            setSubject(subject(company), "UTF-8")
            setText(body(v, company), "UTF-8")
        }
        Transport.send(msg)
    }

    /** Sends a short test mail to the account's own address so the settings can be checked on the spot. Blocking. */
    fun sendTest(c: SmtpSettings) {
        val to = c.from.ifBlank { c.user }.trim()
        val msg = MimeMessage(session(c)).apply {
            setFrom(fromAddress(c, c.fromName.ifBlank { "Visitor Capture" }))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true))
            setSubject("Visitor Capture test mail", "UTF-8")
            setText("Your mail settings work. Thank-you e-mails will be sent from this address.", "UTF-8")
        }
        Transport.send(msg)
    }
}
