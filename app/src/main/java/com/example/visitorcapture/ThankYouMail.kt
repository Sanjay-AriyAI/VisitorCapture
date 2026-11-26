package com.example.visitorcapture

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Properties
import javax.activation.DataHandler
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import javax.mail.util.ByteArrayDataSource

/** SMTP account used for the thank-you mail. Entered on the Mail settings screen; build-time values are only the default. */
data class SmtpSettings(
    val host: String = "", val port: Int = 587, val user: String = "", val password: String = "",
    val from: String = "", val fromName: String = "",
    val company: CompanyInfo = CompanyInfo()
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
                fromName = p.getString(P + "fromName", null) ?: BuildConfig.SMTP_FROM_NAME,
                // All four blank (never entered, or saved empty by an earlier build): start from the brand's own details.
                company = CompanyInfo(
                    address = p.getString(P + "coAddress", null) ?: "",
                    phone = p.getString(P + "coPhone", null) ?: "",
                    email = p.getString(P + "coEmail", null) ?: "",
                    website = p.getString(P + "coWebsite", null) ?: "")
                    .let { if (it.isEmpty) Brand.companyDefaults else it })
        }
        fun save(ctx: Context, s: SmtpSettings) {
            ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                .putString(P + "host", s.host.trim()).putInt(P + "port", s.port)
                .putString(P + "user", s.user.trim()).putString(P + "password", s.password)
                .putString(P + "from", s.from.trim()).putString(P + "fromName", s.fromName.trim())
                .putString(P + "coAddress", s.company.address.trim()).putString(P + "coPhone", s.company.phone.trim())
                .putString(P + "coEmail", s.company.email.trim()).putString(P + "coWebsite", s.company.website.trim()).apply()
        }
    }
}

/** The exhibitor's own contact details, shown to the visitor in the thank-you mail. Entered on the Mail settings screen. */
data class CompanyInfo(val address: String = "", val phone: String = "", val email: String = "", val website: String = "") {
    val isEmpty get() = address.isBlank() && phone.isBlank() && email.isBlank() && website.isBlank()
}

/** Status values stored in Visitor.thankYouStatus. */
object ThankYou {
    const val PENDING = "PENDING"   // saved with a valid e-mail, not mailed yet
    const val QUEUED = "QUEUED"     // staff pressed "Send thank-you mails": goes out as soon as the phone is online
    const val SENT = "SENT"
    const val FAILED = "FAILED"

    /** Content-ID of the company logo embedded in the HTML mail. */
    const val LOGO_CID = "company-logo"

    private val EMAIL_RX = Regex("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")

    fun isValidEmail(s: String) = EMAIL_RX.matches(s.trim())

    /** Initial status for a freshly saved visitor: only a plausible address is ever queued. */
    fun initialStatus(email: String) = if (isValidEmail(email)) PENDING else ""

    fun subject(company: String, updated: Boolean = false) =
        if (updated) "Updated: your visit summary from $company (final version)" else "Thank you for visiting $company"

    /** The record was mailed before, so the next mail is the "updated, final version" one. */
    fun isUpdate(v: Visitor) = v.thankYouSentAt > 0

    /**
     * True when an edit should trigger the "updated" mail: the earlier mail went out, the address is still valid,
     * and something the visitor can see in the mail changed. Edits to staff-only fields (notes, remarks) send nothing.
     */
    fun needsUpdateMail(old: Visitor, edited: Visitor) =
        old.thankYouStatus == SENT && isValidEmail(edited.email) &&
            (htmlBody(old, "") != htmlBody(edited, "") || old.mailableEmails() != edited.mailableEmails())

    /** One sentence on what happens next, from the follow-up and purpose of visit; "" if nothing was planned. */
    fun nextStep(v: Visitor): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy 'at' HH:mm", Locale.getDefault())
        // New records: the times picked in the Factory Visit / Follow-up popup
        val planned = listOfNotNull(
            if (v.factoryVisitAt > 0) "We look forward to welcoming you at our factory on ${fmt.format(Date(v.factoryVisitAt))}." else null,
            if (v.followUpAt > 0 && v.followUpAction.isBlank()) "We will follow up with you on ${fmt.format(Date(v.followUpAt))}." else null)
        if (planned.isNotEmpty()) return planned.joinToString(" ")
        // Older records: the follow-up action chosen in the former FOLLOW-UP section
        return when (v.followUpAction) {
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
    }

    /** Plain-text body (fallback part of the mail), personalised from the visit. Pure function so it can be unit-tested. */
    fun body(v: Visitor, company: String, updated: Boolean = false, info: CompanyInfo = CompanyInfo()): String {
        val first = v.name.trim().ifBlank { "" }
        val sb = StringBuilder()
        sb.append(if (first.isNotEmpty()) "Dear $first,\n\n" else "Dear Sir/Madam,\n\n")
        if (updated) sb.append("We have updated the details we recorded during your visit. This e-mail replaces our earlier " +
            "message; please treat it as the final version.\n\n")
        INTRO.forEach { (text, _) -> sb.append(text).append("\n\n") }

        val interests = Catalog.summarize(v.products.split("; ").filter { it.isNotBlank() })
        if (interests.isNotBlank() || v.additionalInterest.isNotBlank()) {
            sb.append("You showed interest in:\n")
            interests.lines().filter { it.isNotBlank() }.forEach { sb.append("  - ").append(it.trim()).append('\n') }
            if (v.additionalInterest.isNotBlank()) sb.append("  - ").append(v.additionalInterest.trim()).append('\n')
            sb.append('\n')
        }

        val next = nextStep(v)
        if (next.isNotEmpty()) sb.append(next).append("\n\n")

        sb.append("If you have any questions in the meantime, just reply to this e-mail")
        sb.append(if (v.attendedByPhone.isNotBlank()) " or call ${v.attendedByPhone.trim()}.\n\n" else ".\n\n")
        if (!info.isEmpty) {
            sb.append("Contact us:\n").append(company).append('\n')
            if (info.address.isNotBlank()) sb.append(info.address.trim()).append('\n')
            if (info.phone.isNotBlank()) sb.append("Phone: ").append(info.phone.trim()).append('\n')
            if (info.email.isNotBlank()) sb.append("E-mail: ").append(info.email.trim()).append('\n')
            if (info.website.isNotBlank()) sb.append("Website: ").append(info.website.trim()).append('\n')
            sb.append('\n')
        }
        sb.append("Warm regards,\n").append(SIGNATURE).append('\n')
        return sb.toString()
    }

    /** Fixed sign-off under "Warm regards" (per company, see Brand). */
    val SIGNATURE get() = Brand.signature

    /** Opening text of the mail, paragraph by paragraph; true marks a headline (bold in the HTML mail). Per company, see Brand. */
    val INTRO get() = Brand.intro

    /** The exhibitor's own contact block (name, address, phone, e-mail, website as links); "" when nothing was entered. */
    internal fun companyHtml(company: String, info: CompanyInfo): String {
        if (info.isEmpty) return ""
        val link = "color:$AMBER_DARK;font-weight:600;text-decoration:none;"
        val web = info.website.trim()
        // "a / b" holds several numbers or addresses: each gets its own link, one per line
        fun links(value: String, href: (String) -> String) = value.split("/").map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString("<br>") { """<a href="${esc(href(it))}" style="$link">${esc(it)}</a>""" }
        val rows = listOf(
            "Company" to esc(company),
            "Address" to if (info.address.isBlank()) "" else escLines(info.address),
            "Phone" to links(info.phone) { n -> "tel:" + n.filter { it.isDigit() || it == '+' } },
            "E-mail" to links(info.email) { "mailto:$it" },
            "Website" to if (web.isBlank()) "" else
                """<a href="${esc(if (web.startsWith("http", ignoreCase = true)) web else "https://$web")}" style="$link">${esc(web)}</a>""")
        val body = rows.filter { it.second.isNotBlank() }.joinToString("") { (k, html) ->
            """<tr>
              <td style="font-family:$FONT;padding:9px 12px 9px 0;width:36%;color:$MUTED;font-size:13px;vertical-align:top;border-bottom:1px solid $RULE;">${esc(k)}</td>
              <td style="font-family:$FONT;padding:9px 0;color:$NAVY;font-size:14px;font-weight:600;vertical-align:top;border-bottom:1px solid $RULE;">$html</td>
            </tr>"""
        }
        return """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">$body</table>"""
    }

    // ------------------------------------------------------------------ HTML mail

    // Brand colours, matching the app theme. Mail clients ignore <style> blocks and CSS variables, so everything is inline.
    internal const val NAVY = "#0B2033"
    internal const val AMBER = "#E8A33D"
    internal const val AMBER_DARK = "#A35F00"
    internal const val MUTED = "#5B6B7B"
    internal const val PAGE = "#F3F5F8"
    internal const val RULE = "#E6EBF1"
    internal const val FONT = "'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

    internal fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")
    internal fun escLines(s: String) = esc(s.trim()).replace(Regex("\r?\n"), "<br>")

    /** Makes web addresses in already-escaped text clickable; not every mail client does that by itself. */
    private fun linkify(html: String) = html.replace(Regex("https?://\\S+")) {
        """<a href="${it.value}" style="color:$AMBER_DARK;font-weight:600;word-break:break-all;">${it.value}</a>"""
    }

    /** A titled block with an amber rule under the heading. */
    internal fun section(title: String, inner: String) = """
        <tr><td style="padding:24px 32px 0 32px;">
          <div style="font-family:$FONT;font-size:11px;letter-spacing:1.6px;font-weight:700;color:$AMBER_DARK;text-transform:uppercase;padding-bottom:8px;border-bottom:2px solid $AMBER;">${esc(title)}</div>
          $inner
        </td></tr>"""

    /** Label / value table; blank values are left out, and an all-blank table renders nothing. */
    internal fun details(rows: List<Pair<String, String>>): String {
        val body = rows.filter { it.second.isNotBlank() }.joinToString("") { (k, v) ->
            """<tr>
              <td style="font-family:$FONT;padding:9px 12px 9px 0;width:36%;color:$MUTED;font-size:13px;vertical-align:top;border-bottom:1px solid $RULE;">${esc(k)}</td>
              <td style="font-family:$FONT;padding:9px 0;color:$NAVY;font-size:14px;font-weight:600;vertical-align:top;border-bottom:1px solid $RULE;">${escLines(v)}</td>
            </tr>"""
        }
        return if (body.isEmpty()) "" else """<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="border-collapse:collapse;">$body</table>"""
    }

    internal fun chips(items: List<String>) = items.joinToString("") {
        """<span style="display:inline-block;font-family:$FONT;background:#FFF3E0;color:#7A4600;border:1px solid #F3D19C;border-radius:14px;padding:5px 12px;margin:10px 6px 0 0;font-size:13px;font-weight:600;">${esc(it)}</span>"""
    }

    /** Products grouped as Product → machine → makes / models, from the stored "A > B > C" catalogue paths. */
    internal fun productsHtml(v: Visitor): String {
        val paths = v.products.split("; ").map { it.trim() }.filter { it.isNotBlank() }.map { it.split(" > ") }
        return paths.groupBy { it[0] }.entries.joinToString("") { (product, list) ->
            val machines = list.filter { it.size > 1 }.groupBy { it[1] }.entries.joinToString("") { (machine, x) ->
                val more = x.map { it.drop(2).joinToString(" · ") }.filter { it.isNotEmpty() }.distinct()
                """<li style="margin:0 0 6px 0;"><span style="color:$NAVY;font-weight:600;">${esc(machine)}</span>""" +
                    (if (more.isEmpty()) "" else """<span style="color:$MUTED;"> — ${esc(more.joinToString(", "))}</span>""") + "</li>"
            }
            val extra = if (product == Brand.otherProduct && v.otherDetail.isNotBlank())
                """<div style="color:$MUTED;font-size:13px;margin:4px 0 0 0;">${escLines(v.otherDetail)}</div>""" else ""
            """<div style="font-family:$FONT;margin:14px 0 0 0;padding:12px 14px;background:#FAFBFC;border:1px solid $RULE;border-left:4px solid $AMBER;border-radius:6px;">
              <div style="color:$NAVY;font-size:15px;font-weight:700;">${esc(product)}</div>
              ${if (machines.isEmpty()) "" else """<ul style="margin:8px 0 0 0;padding:0 0 0 18px;font-size:14px;line-height:1.45;">$machines</ul>"""}
              $extra
            </div>"""
        }
    }

    /**
     * Branded HTML body: greeting, the details captured from the card, profile, products of interest,
     * purpose of visit, next step and the staff member's signature. Pure function so it can be unit-tested.
     * Internal staff fields (discussion notes, remarks, follow-up remarks) are never included: they are not meant for the visitor.
     * [logoCid] is the Content-ID of an embedded logo, or null to show the company name as text.
     */
    fun htmlBody(v: Visitor, company: String, logoCid: String? = null, updated: Boolean = false, info: CompanyInfo = CompanyInfo()): String {
        val name = v.name.trim()
        val visitFmt = SimpleDateFormat("EEEE, d MMMM yyyy 'at' HH:mm", Locale.getDefault())
        val year = Calendar.getInstance().get(Calendar.YEAR)

        val intro = INTRO.joinToString("") { (text, headline) ->
            if (headline) """<p style="margin:16px 0 8px 0;font-weight:800;color:$AMBER_DARK;letter-spacing:0.3px;">${esc(text)}</p>"""
            else """<p style="margin:0 0 12px 0;">${linkify(esc(text))}</p>"""
        }

        val contact = companyHtml(company, info)
        val profile = details(listOf("Customer type" to v.customerType, "Market" to v.marketExposure))
        val products = productsHtml(v)
        val additional = v.additionalInterest.trim()
        val purposes = v.leadOutcomes.split("; ").map { it.trim() }.filter { it.isNotBlank() }
        val visit = details(listOf(
            "Date & time" to if (v.createdAt > 0) visitFmt.format(Date(v.createdAt)) else "",
            "Attended by" to listOf(v.attendedByName.trim(), v.attendedByTitle.trim()).filter { it.isNotBlank() }.joinToString(", ")))
        val next = nextStep(v)

        val header = if (logoCid != null)
            """<img src="cid:$logoCid" alt="${esc(company)}" width="180" style="display:block;border:0;outline:none;width:180px;max-width:60%;height:auto;">"""
        else """<div style="font-family:$FONT;font-size:22px;font-weight:800;color:$NAVY;">${esc(company)}</div>"""

        val sb = StringBuilder()
        sb.append("""<!DOCTYPE html>
<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light"><meta name="supported-color-schemes" content="light">
<title>${esc(subject(company, updated))}</title></head>
<body style="margin:0;padding:0;background:$PAGE;">
<div style="display:none;max-height:0;overflow:hidden;opacity:0;">${if (updated) "Your visit details have been updated. This is the final version."
    else "Thank you for visiting ${esc(company)}. Here is a summary of your visit."}</div>
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:$PAGE;">
<tr><td align="center" style="padding:24px 12px;">
<table role="presentation" width="760" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:760px;background:#FFFFFF;border-radius:12px;overflow:hidden;border:1px solid $RULE;">
  <tr><td style="padding:24px 32px 20px 32px;background:#FFFFFF;">$header</td></tr>
  <tr><td style="height:4px;line-height:4px;font-size:0;background:$AMBER;">&nbsp;</td></tr>
  <tr><td style="padding:28px 32px 0 32px;background:$NAVY;">
    ${if (updated) """<div style="font-family:$FONT;display:inline-block;background:$AMBER;color:$NAVY;font-size:11px;font-weight:800;letter-spacing:1.4px;text-transform:uppercase;border-radius:4px;padding:4px 10px;margin-bottom:10px;">Updated &middot; Final version</div>""" else ""}
    <div style="font-family:$FONT;font-size:24px;line-height:1.3;font-weight:700;color:#FFFFFF;">${
        if (updated) "Your visit details have been updated" else "Thank you for your visit" + (if (name.isNotEmpty()) ", " + esc(name) else "") + "!"}</div>
    <div style="font-family:$FONT;font-size:14px;color:#C9D4DF;padding:6px 0 26px 0;">${
        if (updated) "This e-mail replaces our earlier message." else "We're glad you stopped by our stand."}</div>
  </td></tr>
  <tr><td style="padding:26px 32px 0 32px;font-family:$FONT;font-size:15px;line-height:1.6;color:$NAVY;">
    <p style="margin:0 0 12px 0;">${if (name.isNotEmpty()) "Dear ${esc(name)}," else "Dear Sir/Madam,"}</p>
    ${if (updated) """<p style="margin:0 0 12px 0;padding:12px 14px;background:#FFF8EC;border-left:4px solid $AMBER;border-radius:4px;">
      We have reviewed and updated the details recorded during your visit. <b>Please treat this e-mail as the final version</b>
      and disregard the summary we sent earlier.</p>""" else ""}
    $intro
  </td></tr>
""")
        if (next.isNotEmpty()) sb.append("""
  <tr><td style="padding:20px 32px 0 32px;">
    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0"><tr>
      <td style="font-family:$FONT;background:#FFF8EC;border:1px solid #F3D19C;border-radius:8px;padding:14px 16px;">
        <div style="font-size:11px;letter-spacing:1.6px;font-weight:700;color:$AMBER_DARK;text-transform:uppercase;">Next step</div>
        <div style="font-size:15px;color:$NAVY;font-weight:600;padding-top:4px;line-height:1.5;">${esc(next)}</div>
      </td></tr></table>
  </td></tr>""")
        if (profile.isNotEmpty()) sb.append(section("Your profile", profile))
        if (products.isNotEmpty() || additional.isNotEmpty()) sb.append(section("Products of interest",
            products + (if (additional.isEmpty()) "" else
                """<div style="font-family:$FONT;margin:14px 0 0 0;font-size:13px;color:$MUTED;">Also interested in</div>
                   <div style="font-family:$FONT;font-size:14px;color:$NAVY;font-weight:600;padding-top:2px;line-height:1.5;">${escLines(additional)}</div>""")))
        if (purposes.isNotEmpty()) sb.append(section("Purpose of visit", "<div>${chips(purposes)}</div>"))
        if (visit.isNotEmpty()) sb.append(section("Your visit", visit))
        if (contact.isNotEmpty()) sb.append(section("Contact us", contact))
        sb.append("""
  <tr><td style="padding:26px 32px 0 32px;font-family:$FONT;font-size:14px;line-height:1.6;color:$NAVY;">
    <p style="margin:0;">If any of these details need correcting, or you have questions in the meantime, simply reply to this e-mail${
            if (v.attendedByPhone.isNotBlank()) " or call <a href=\"tel:${esc(v.attendedByPhone.filter { it.isDigit() || it == '+' })}\" style=\"color:$AMBER_DARK;font-weight:600;text-decoration:none;\">${esc(v.attendedByPhone.trim())}</a>" else ""}.</p>
  </td></tr>
  <tr><td style="padding:22px 32px 30px 32px;font-family:$FONT;font-size:14px;line-height:1.55;color:$NAVY;">
    <div style="color:$MUTED;">Warm regards,</div>
    <div style="font-size:16px;font-weight:700;padding-top:4px;">$SIGNATURE</div>
  </td></tr>
  <tr><td style="padding:16px 32px;background:$NAVY;font-family:$FONT;font-size:11px;line-height:1.6;color:#9FB0C1;text-align:center;">
    You received this e-mail because you shared your business card with ${esc(company)}.<br>
    © $year ${esc(company)}. All rights reserved.
  </td></tr>
</table>
<div style="font-family:$FONT;font-size:10px;color:#9AA7B4;padding:12px 0 0 0;text-align:center;">Developed &amp; Powered by <b>$DEVELOPER_BRAND</b>&trade;</div>
</td></tr></table>
</body></html>""")
        return sb.toString()
    }

    internal fun session(c: SmtpSettings): Session {
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

    internal fun fromAddress(c: SmtpSettings, name: String) = InternetAddress(c.from.ifBlank { c.user }.trim(), name, "UTF-8")

    /**
     * multipart/alternative: plain text first (fallback), then the HTML version.
     * With a [logo] (PNG bytes) the HTML part becomes multipart/related so the logo travels inline, not as an attachment.
     * [photos] are real attachments, so they wrap all of that in multipart/mixed.
     */
    internal fun content(plain: String, html: String, logo: ByteArray?, photos: List<MailPhoto> = emptyList()): MimeMultipart {
        val alt = MimeMultipart("alternative")
        alt.addBodyPart(MimeBodyPart().apply { setText(plain, "UTF-8") })
        val htmlPart = MimeBodyPart().apply { setContent(html, "text/html; charset=UTF-8") }
        if (logo == null) alt.addBodyPart(htmlPart)
        else {
            val related = MimeMultipart("related")
            related.addBodyPart(htmlPart)
            related.addBodyPart(MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource(logo, "image/png"))
                contentID = "<$LOGO_CID>"
                disposition = MimeBodyPart.INLINE
                fileName = "logo.png"
            })
            alt.addBodyPart(MimeBodyPart().apply { setContent(related) })
        }
        if (photos.isEmpty()) return alt
        val mixed = MimeMultipart("mixed")
        mixed.addBodyPart(MimeBodyPart().apply { setContent(alt) })
        photos.forEach { p ->
            mixed.addBodyPart(MimeBodyPart().apply {
                dataHandler = DataHandler(ByteArrayDataSource(p.bytes, p.type))
                disposition = MimeBodyPart.ATTACHMENT
                fileName = p.name
            })
        }
        return mixed
    }

    /** Company logo for the mail header, or null if it can't be read (the mail then shows the company name instead). */
    fun loadLogo(ctx: Context): ByteArray? = try {
        Brand.logo?.let { id -> ctx.resources.openRawResource(id).use { it.readBytes() } }
    } catch (e: Exception) { null }

    /** Blocking SMTP send; throws javax.mail.MessagingException on any failure. */
    fun send(c: SmtpSettings, v: Visitor, logo: ByteArray? = null, updated: Boolean = false) {
        val company = c.fromName.ifBlank { "our company" }
        val msg = MimeMessage(session(c)).apply {
            setFrom(fromAddress(c, company))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(v.mailableEmails().ifEmpty { listOf(v.email.trim()) }.joinToString(","), true))
            if (isValidEmail(v.attendedByEmail)) replyTo = arrayOf(InternetAddress(v.attendedByEmail.trim(), v.attendedByName.ifBlank { null }, "UTF-8"))
            setSubject(subject(company, updated), "UTF-8")
            setContent(content(body(v, company, updated, c.company), htmlBody(v, company, if (logo != null) LOGO_CID else null, updated, c.company), logo, MailPhotos.load(v)))
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
