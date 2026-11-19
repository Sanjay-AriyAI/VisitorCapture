package com.example.visitorcapture

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.mail.Message
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import com.example.visitorcapture.ThankYou.AMBER
import com.example.visitorcapture.ThankYou.AMBER_DARK
import com.example.visitorcapture.ThankYou.FONT
import com.example.visitorcapture.ThankYou.MUTED
import com.example.visitorcapture.ThankYou.NAVY
import com.example.visitorcapture.ThankYou.PAGE
import com.example.visitorcapture.ThankYou.RULE
import com.example.visitorcapture.ThankYou.esc
import com.example.visitorcapture.ThankYou.escLines

/** The two things a reminder can be about. Each has its own time and status column on [Visitor]. */
enum class Reminder(val key: String, val outcome: String, val label: String) {
    FACTORY("factory", "Factory Visit", "Factory visit"),
    FOLLOW_UP("followup", "Follow-up", "Follow-up");

    fun at(v: Visitor) = when (this) { FACTORY -> v.factoryVisitAt; FOLLOW_UP -> v.followUpAt }
    fun status(v: Visitor) = when (this) { FACTORY -> v.factoryReminderStatus; FOLLOW_UP -> v.followUpReminderStatus }
    suspend fun setStatus(dao: VisitorDao, uid: String, status: String) = when (this) {
        FACTORY -> dao.setFactoryReminder(uid, status); FOLLOW_UP -> dao.setFollowUpReminder(uid, status)
    }

    companion object {
        const val SCHEDULED = "SCHEDULED"
        const val SENT = "SENT"
        const val FAILED = "FAILED"
        const val EXPIRED = "EXPIRED"     // the visit time passed before the mail could go out (e.g. phone off all along)
        const val DAY_MS = 24 * 60 * 60 * 1000L

        /** The mail goes out 24 hours before [at]; if that moment has already passed, it goes out straight away. */
        fun remindAt(at: Long) = at - DAY_MS

        /** Due now (a minute of slack, so an alarm firing a little early still counts). */
        fun isDue(at: Long, now: Long) = remindAt(at) <= now + 60_000L

        /** Status for a freshly picked time: only a future time with someone to mail is scheduled. */
        fun initialStatus(at: Long, v: Visitor, now: Long = System.currentTimeMillis()) =
            if (at > now && ReminderMail.recipients(v) != null) SCHEDULED else ""
    }
}

/** The 24-hour reminder mail for a factory visit or follow-up. Separate from the thank-you mail. */
object ReminderMail {
    private fun whenFmt() = SimpleDateFormat("EEEE, d MMMM yyyy 'at' HH:mm", Locale.getDefault())
    private fun shortFmt() = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.getDefault())

    /**
     * TO the visitor with the attending staff member in CC. With no valid visitor address the staff member
     * still gets it (TO). Null when there is nobody valid to mail.
     */
    fun recipients(v: Visitor): Pair<String, String?>? {
        val visitors = v.mailableEmails()
        val visitor = visitors.joinToString(", ").ifEmpty { null }   // all of the visitor's addresses
        val staff = v.attendedByEmail.trim().takeIf { ThankYou.isValidEmail(it) }
        return when {
            visitor != null -> visitor to staff?.takeIf { s -> visitors.none { it.equals(s, ignoreCase = true) } }
            staff != null -> staff to null
            else -> null
        }
    }

    fun subject(kind: Reminder, at: Long, company: String) =
        "Reminder: ${kind.label} on ${shortFmt().format(Date(at))} – $company"

    private fun lead(kind: Reminder, at: Long, company: String) = when (kind) {
        Reminder.FACTORY -> "This is a friendly reminder of your factory visit to $company scheduled for ${whenFmt().format(Date(at))}."
        Reminder.FOLLOW_UP -> "This is a friendly reminder of our follow-up on your enquiry, scheduled for ${whenFmt().format(Date(at))}."
    }

    private fun closing(kind: Reminder) = when (kind) {
        Reminder.FACTORY -> "We look forward to welcoming you. If the date or time no longer suits you, simply reply to this e-mail"
        Reminder.FOLLOW_UP -> "If you would like to share anything before then, or need a different time, simply reply to this e-mail"
    }

    private fun purposes(v: Visitor) = v.leadOutcomes.split("; ").map { it.trim() }.filter { it.isNotBlank() }

    /** Plain-text body (fallback part). Staff-only fields (notes, remarks) are never included. Pure, for unit tests. */
    fun body(v: Visitor, kind: Reminder, company: String): String {
        val at = kind.at(v)
        val sb = StringBuilder()
        val name = v.name.trim()
        sb.append(if (name.isNotEmpty()) "Dear $name,\n\n" else "Dear Sir/Madam,\n\n")
        sb.append(lead(kind, at, company)).append("\n\n")

        sb.append("Your enquiry\n")
        val p = purposes(v)
        if (p.isNotEmpty()) sb.append("Purpose of visit: ").append(p.joinToString(", ")).append('\n')
        val interests = Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() })
        interests.lines().filter { it.isNotBlank() }.forEach { sb.append("  - ").append(it.trim()).append('\n') }
        if (v.products.contains("Other Products") && v.otherDetail.isNotBlank()) sb.append("  - Other: ").append(v.otherDetail.trim()).append('\n')
        if (v.additionalInterest.isNotBlank()) sb.append("  - ").append(v.additionalInterest.trim()).append('\n')
        sb.append('\n')

        sb.append(closing(kind))
        sb.append(if (v.attendedByPhone.isNotBlank()) " or call ${v.attendedByPhone.trim()}.\n\n" else ".\n\n")
        sb.append("Warm regards,\n")
        sb.append(v.attendedByName.ifBlank { company }).append('\n')
        if (v.attendedByTitle.isNotBlank()) sb.append(v.attendedByTitle.trim()).append('\n')
        sb.append(company).append('\n')
        if (v.attendedByPhone.isNotBlank()) sb.append(v.attendedByPhone.trim()).append('\n')
        if (v.attendedByEmail.isNotBlank()) sb.append(v.attendedByEmail.trim()).append('\n')
        return sb.toString()
    }

    /** Branded HTML body, same look as the thank-you mail. Pure, for unit tests. */
    fun htmlBody(v: Visitor, kind: Reminder, company: String, logoCid: String? = null): String {
        val at = kind.at(v)
        val name = v.name.trim()
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val header = if (logoCid != null)
            """<img src="cid:$logoCid" alt="${esc(company)}" width="180" style="display:block;border:0;outline:none;width:180px;max-width:60%;height:auto;">"""
        else """<div style="font-family:$FONT;font-size:22px;font-weight:800;color:$NAVY;">${esc(company)}</div>"""
        val sig = listOf(v.attendedByTitle.trim(), company, v.attendedByPhone.trim(), v.attendedByEmail.trim())
            .filter { it.isNotBlank() }.joinToString("<br>") { esc(it) }

        val products = ThankYou.productsHtml(v)
        val additional = v.additionalInterest.trim()
        val p = purposes(v)
        val enquiry = buildString {
            if (p.isNotEmpty()) append("<div>${ThankYou.chips(p)}</div>")
            append(products)
            if (additional.isNotEmpty()) append("""<div style="font-family:$FONT;margin:14px 0 0 0;font-size:13px;color:$MUTED;">Also interested in</div>
                <div style="font-family:$FONT;font-size:14px;color:$NAVY;font-weight:600;padding-top:2px;line-height:1.5;">${escLines(additional)}</div>""")
        }

        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light">
<title>${esc(subject(kind, at, company))}</title></head>
<body style="margin:0;padding:0;background:$PAGE;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;">${esc(lead(kind, at, company))}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:$PAGE;">
<tr><td align="center" style="padding:24px 12px;">
<table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background:#FFFFFF;border-radius:12px;overflow:hidden;border:1px solid $RULE;">
  <tr><td style="padding:24px 32px 20px 32px;background:#FFFFFF;">$header</td></tr>
  <tr><td style="height:4px;line-height:4px;font-size:0;background:$AMBER;">&nbsp;</td></tr>
  <tr><td style="padding:28px 32px 0 32px;background:$NAVY;">
    <div style="font-family:$FONT;display:inline-block;background:$AMBER;color:$NAVY;font-size:11px;font-weight:800;letter-spacing:1.4px;text-transform:uppercase;border-radius:4px;padding:4px 10px;margin-bottom:10px;">Reminder</div>
    <div style="font-family:$FONT;font-size:24px;line-height:1.3;font-weight:700;color:#FFFFFF;">Upcoming ${esc(kind.label.lowercase())}</div>
    <div style="font-family:$FONT;font-size:14px;color:#C9D4DF;padding:6px 0 26px 0;">${esc(whenFmt().format(Date(at)))}</div>
  </td></tr>
  <tr><td style="padding:26px 32px 0 32px;font-family:$FONT;font-size:15px;line-height:1.6;color:$NAVY;">
    <p style="margin:0 0 12px 0;">${if (name.isNotEmpty()) "Dear ${esc(name)}," else "Dear Sir/Madam,"}</p>
    <p style="margin:0;">${esc(lead(kind, at, company))}</p>
  </td></tr>
""")
        if (enquiry.isNotEmpty()) sb.append(ThankYou.section("Your enquiry", enquiry))
        sb.append("""
  <tr><td style="padding:26px 32px 0 32px;font-family:$FONT;font-size:14px;line-height:1.6;color:$NAVY;">
    <p style="margin:0;">${esc(closing(kind))}${
            if (v.attendedByPhone.isNotBlank()) " or call <a href=\"tel:${esc(v.attendedByPhone.filter { it.isDigit() || it == '+' })}\" style=\"color:$AMBER_DARK;font-weight:600;text-decoration:none;\">${esc(v.attendedByPhone.trim())}</a>" else ""}.</p>
  </td></tr>
  <tr><td style="padding:22px 32px 30px 32px;font-family:$FONT;font-size:14px;line-height:1.55;color:$NAVY;">
    <div style="color:$MUTED;">Warm regards,</div>
    <div style="font-size:16px;font-weight:700;padding-top:4px;">${esc(v.attendedByName.trim().ifBlank { company })}</div>
    <div style="color:$MUTED;font-size:13px;">$sig</div>
  </td></tr>
  <tr><td style="padding:16px 32px;background:$NAVY;font-family:$FONT;font-size:11px;line-height:1.6;color:#9FB0C1;text-align:center;">
    You received this reminder because you scheduled a ${esc(kind.label.lowercase())} with ${esc(company)}.<br>
    © $year ${esc(company)}. All rights reserved.
  </td></tr>
</table>
</td></tr></table>
</body></html>""")
        return sb.toString()
    }

    /** Blocking SMTP send; throws javax.mail.MessagingException on any failure. */
    fun send(c: SmtpSettings, v: Visitor, kind: Reminder, logo: ByteArray? = null) {
        val (to, cc) = recipients(v) ?: throw javax.mail.internet.AddressException("No valid recipient")
        val company = c.fromName.ifBlank { "our company" }
        val msg = MimeMessage(ThankYou.session(c)).apply {
            setFrom(ThankYou.fromAddress(c, company))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, true))
            if (cc != null) setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc, true))
            if (ThankYou.isValidEmail(v.attendedByEmail))
                replyTo = arrayOf(InternetAddress(v.attendedByEmail.trim(), v.attendedByName.ifBlank { null }, "UTF-8"))
            setSubject(subject(kind, kind.at(v), company), "UTF-8")
            setContent(ThankYou.content(body(v, kind, company),
                htmlBody(v, kind, company, if (logo != null) ThankYou.LOGO_CID else null), logo))
        }
        Transport.send(msg)
    }
}
