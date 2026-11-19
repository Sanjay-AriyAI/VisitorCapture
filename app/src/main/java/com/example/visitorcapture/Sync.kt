package com.example.visitorcapture

import android.content.Context
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Export / import used to merge data collected on several staff devices. */
object Sync {
    // VisitDate / VisitTime / VisitedAt are the visit's date and time in the exporting phone's time zone;
    // TimestampMs is the same instant in UTC milliseconds and is what the importer reads.
    private val HEADERS = listOf("UID", "Device", "VisitDate", "VisitTime", "VisitedAt", "TimestampMs",
        "Name", "Title", "Company", "Phone", "Email", "Address", "State", "Country",
        "CustomerType", "MarketExposure", "Products", "OtherDetail", "AdditionalInterest",
        "AttendedBy", "AttendedByTitle", "AttendedByEmail", "AttendedByPhone", "AttendedByKey",
        "LeadOutcome", "DiscussionNotes", "Remarks", "RemarksOther",
        "FollowUpAction", "FollowUpDate", "FollowUpAtMs", "FollowUpNote", "Photo", "HandwrittenNotes",
        "FactoryVisitDate", "FactoryVisitAtMs", "FactoryReminder", "FollowUpReminder", "PhotoBack", "ExtraEmails", "Website", "ExtraPhones")

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun hasPhoto(v: Visitor) = v.cardPhotoPath?.let { File(it).exists() } == true
    private fun hasBackPhoto(v: Visitor) = v.cardBackPhotoPath?.let { File(it).exists() } == true

    /** File names allowed for handwritten-note images, in the zip and in the cards folder. */
    private val NOTE_NAME = Regex("[A-Za-z0-9_-]{8,100}\\.(jpg|png|webp)")

    /** Existing handwritten-note images of a visitor. */
    fun notePhotos(v: Visitor): List<File> =
        v.notePhotoPaths.split("|").filter { it.isNotBlank() }.map(::File).filter { it.exists() }

    /** Folder of one lead inside the zip: leads/<UID - Name - Company>, stripped of characters file systems reject. */
    fun leadFolder(v: Visitor): String {
        val raw = listOf(v.uid, v.name, v.company).map { it.trim() }.filter { it.isNotBlank() }.joinToString(" - ")
        val clean = raw.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ").replace(Regex("\\s+"), " ").trim()
        return "leads/" + clean.take(80).trimEnd('.', ' ')
    }

    /** Zip path of the lead's business-card photo. */
    fun cardEntry(v: Visitor) = "${leadFolder(v)}/business-card.jpg"

    /** Zip path of the (optional) photo of the back of the card. */
    fun cardBackEntry(v: Visitor) = "${leadFolder(v)}/business-card-back.jpg"

    /** Handwritten-note images with their zip paths: leads/<folder>/handwritten-note-N.<ext>. */
    fun noteEntries(v: Visitor): List<Pair<String, File>> = notePhotos(v).mapIndexed { i, f ->
        "${leadFolder(v)}/handwritten-note-${i + 1}.${f.extension.lowercase().ifBlank { "jpg" }}" to f
    }

    /** Readable summary of everything entered for one lead, written as details.txt in its folder. */
    fun detailsText(v: Visitor): String {
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val out = StringBuilder()
        fun section(title: String, rows: List<Pair<String, String>>) {
            val filled = rows.filter { it.second.isNotBlank() }
            if (filled.isEmpty()) return
            if (out.isNotEmpty()) out.append("\n")
            out.append(title.uppercase()).append("\n").append("-".repeat(title.length)).append("\n")
            filled.forEach { (k, value) ->
                val lines = value.trim().lines()
                out.append("$k: ").append(lines.first()).append("\n")
                lines.drop(1).forEach { out.append("    ").append(it).append("\n") }
            }
        }
        section("Visitor", listOf("Lead ID" to v.uid, "Visited at" to at.format(Date(v.createdAt)),
            "Name" to v.name, "Title" to v.title, "Company" to v.company, "Phone" to v.phone,
            "Secondary phone" to v.extraPhoneList().getOrElse(0) { "" }, "Third phone" to v.extraPhoneList().getOrElse(1) { "" }, "E-mail" to v.email,
            "Secondary e-mail" to v.extraEmailList().getOrElse(0) { "" }, "Third e-mail" to v.extraEmailList().getOrElse(1) { "" }, "Website" to v.website,
            "Address" to v.address, "State" to v.state, "Country" to v.country))
        section("Profile & interest", listOf("Customer type" to v.customerType, "Market exposure" to v.marketExposure,
            "Products" to v.products.split(";").map { it.trim() }.filter { it.isNotBlank() }.joinToString("\n"),
            "Other details" to v.otherDetail, "Additional interest" to v.additionalInterest))
        section("Outcome & follow-up", listOf("Lead outcome" to v.leadOutcomes, "Discussion notes" to v.discussionNotes,
            "Remarks" to v.remarks, "Remarks (other)" to v.remarksOther, "Follow-up action" to v.followUpAction,
            "Follow-up date" to (if (v.followUpAt > 0) at.format(Date(v.followUpAt)) else ""), "Follow-up note" to v.followUpNote,
            "Factory visit" to (if (v.factoryVisitAt > 0) at.format(Date(v.factoryVisitAt)) else ""),
            "Factory visit reminder" to v.factoryReminderStatus, "Follow-up reminder" to v.followUpReminderStatus))
        section("Attended by", listOf("Name" to v.attendedByName, "Title" to v.attendedByTitle,
            "E-mail" to v.attendedByEmail, "Phone" to v.attendedByPhone, "Device" to v.deviceName))
        val files = (if (hasPhoto(v)) listOf("business-card.jpg") else emptyList()) +
            (if (hasBackPhoto(v)) listOf("business-card-back.jpg") else emptyList()) +
            noteEntries(v).map { it.first.substringAfterLast('/') }
        section("Attachments", listOf("Files" to files.joinToString("\n")))
        return out.toString().replace("\n", "\r\n")
    }

    fun toCsv(list: List<Visitor>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val followFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder(HEADERS.joinToString(",")).append("\n")
        list.forEach { v ->
            val at = Date(v.createdAt)
            sb.append(listOf(v.uid, v.deviceName, dateFmt.format(at), timeFmt.format(at), isoFmt.format(at), v.createdAt.toString(),
                v.name, v.title, v.company, v.phone, v.email, v.address, v.state, v.country,
                v.customerType, v.marketExposure, v.products, v.otherDetail, v.additionalInterest,
                v.attendedByName, v.attendedByTitle, v.attendedByEmail, v.attendedByPhone, v.attendedByKey,
                v.leadOutcomes, v.discussionNotes, v.remarks, v.remarksOther, v.followUpAction,
                if (v.followUpAt > 0) followFmt.format(Date(v.followUpAt)) else "",
                if (v.followUpAt > 0) v.followUpAt.toString() else "", v.followUpNote,
                if (hasPhoto(v)) cardEntry(v) else "",
                noteEntries(v).joinToString("; ") { it.first },
                if (v.factoryVisitAt > 0) followFmt.format(Date(v.factoryVisitAt)) else "",
                if (v.factoryVisitAt > 0) v.factoryVisitAt.toString() else "",
                v.factoryReminderStatus, v.followUpReminderStatus,
                if (hasBackPhoto(v)) cardBackEntry(v) else "", v.extraEmails, v.website, v.extraPhones).joinToString(",") { q(it) }).append("\n")
        }
        return sb.toString()
    }

    /** Zip with visitors.csv (all leads) + leads/<UID - Name - Company>/ holding details.txt,
     *  business-card.jpg, business-card-back.jpg (if taken) and handwritten-note-N.<ext> for each lead.
     *  Named <agent>-<device>-<from>-<to>.zip, e.g. Sanjay_S-moto_g54_5G-20261110-20261113.zip (see [exportName]). */
    fun exportZip(ctx: Context, list: List<Visitor>, agent: String, device: String, range: DateRange? = null): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, exportName(agent, device, range, list))
        ZipOutputStream(f.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("visitors.csv")); z.write(toCsv(list).toByteArray(Charsets.UTF_8)); z.closeEntry()
            list.distinctBy { it.uid }.forEach { v ->
                z.putNextEntry(ZipEntry("${leadFolder(v)}/details.txt"))
                z.write(detailsText(v).toByteArray(Charsets.UTF_8)); z.closeEntry()
                if (hasPhoto(v)) {
                    z.putNextEntry(ZipEntry(cardEntry(v)))
                    File(v.cardPhotoPath!!).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
                if (hasBackPhoto(v)) {
                    z.putNextEntry(ZipEntry(cardBackEntry(v)))
                    File(v.cardBackPhotoPath!!).inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
                noteEntries(v).forEach { (path, n) ->
                    z.putNextEntry(ZipEntry(path))
                    n.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
            }
        }
        return f
    }

    /**
     * <agent>-<device>-<fromDate>-<toDate>.zip with dates as yyyyMMdd. With no [range] ("All dates") the
     * dates are the first and last visit in [list]. Characters other than letters, digits and _ become "_",
     * so the "-" separators stay unambiguous.
     */
    fun exportName(agent: String, device: String, range: DateRange?, list: List<Visitor>): String {
        fun safe(s: String, fallback: String) =
            s.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_').ifBlank { fallback }
        val r = range ?: list.takeIf { it.isNotEmpty() }?.let { l ->
            DateRange.days(l.minOf { it.createdAt }, l.maxOf { it.createdAt })
        }
        val day = SimpleDateFormat("yyyyMMdd", Locale.US)
        val dates = r?.let { "-" + day.format(Date(it.from)) + "-" + day.format(Date(it.lastDay)) } ?: ""
        return safe(agent, "agent") + "-" + safe(device, "device") + dates + ".zip"
    }

    fun parseCsv(text: String): List<List<String>> {
        val t = text.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQ = false
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (inQ) {
                if (c == '"') { if (i + 1 < t.length && t[i + 1] == '"') { sb.append('"'); i++ } else inQ = false }
                else sb.append(c)
            } else when (c) {
                '"' -> inQ = true
                ',' -> { row.add(sb.toString()); sb.clear() }
                '\n' -> { row.add(sb.toString()); sb.clear(); rows.add(row); row = mutableListOf() }
                '\r' -> {}
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) { row.add(sb.toString()); rows.add(row) }
        return rows.filter { r -> r.any { it.isNotEmpty() } }
    }

    /** Merge a zip or csv exported from another device. Returns (added, skipped as duplicate/invalid). */
    suspend fun import(ctx: Context, dao: VisitorDao, uri: Uri): Pair<Int, Int> {
        val cards = File(ctx.filesDir, "cards").apply { mkdirs() }
        var csvText: String? = null
        val leadFiles = mutableMapOf<String, File>()   // zip path in leads/ -> file saved in cards/
        val uidRx = Regex("[A-Za-z0-9-]{8,64}")
        val noteRx = Regex("handwritten-note-(\\d{1,3})\\.(jpg|png|webp)")
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            val zin = ZipInputStream(raw.buffered())
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    val parts = e.name.split('/')
                    if (e.name == "visitors.csv") csvText = zin.readBytes().toString(Charsets.UTF_8)
                    else if (parts.size == 3 && parts[0] == "leads") {
                        val uid = parts[1].substringBefore(" - ").trim()
                        val file = parts[2]
                        val target = when {
                            !uid.matches(uidRx) -> null
                            file == "business-card.jpg" -> File(cards, "$uid.jpg")
                            file == "business-card-back.jpg" -> File(cards, "${uid}_back.jpg")
                            else -> noteRx.matchEntire(file)?.let { m ->
                                File(cards, "${uid}_note_imp_${m.groupValues[1]}.${m.groupValues[2]}") }
                        }
                        if (target != null) {
                            target.outputStream().use { zin.copyTo(it) }
                            leadFiles[e.name] = target
                        }
                    } else if (e.name.startsWith("photos/")) {
                        val n = e.name.substringAfterLast('/')
                        if (n.matches(Regex("[A-Za-z0-9-]{8,64}\\.jpg"))) File(cards, n).outputStream().use { zin.copyTo(it) }
                    } else if (e.name.startsWith("notes/")) {
                        val n = e.name.substringAfterLast('/')
                        if (n.matches(NOTE_NAME)) File(cards, n).outputStream().use { zin.copyTo(it) }
                    }
                }
                e = zin.nextEntry
            }
        }
        if (csvText == null)   // plain CSV
            csvText = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        val rows = parseCsv(csvText ?: return 0 to 0)
        val h = rows.firstOrNull()?.withIndex()?.associate { it.value to it.index } ?: return 0 to 0
        if ("UID" !in h) return 0 to 0
        fun g(r: List<String>, k: String) = h[k]?.let { r.getOrNull(it) } ?: ""
        var added = 0; var skipped = 0
        for (r in rows.drop(1)) {
            val uid = g(r, "UID").trim()
            if (!uid.matches(Regex("[A-Za-z0-9-]{8,64}"))) { skipped++; continue }
            val photo = File(cards, "$uid.jpg").takeIf { it.exists() }?.absolutePath
            val backPhoto = File(cards, "${uid}_back.jpg").takeIf { it.exists() }?.absolutePath
            val notes = g(r, "HandwrittenNotes").split(";").map { it.trim() }.filter { it.isNotEmpty() }
                .mapNotNull { p -> leadFiles[p] ?: p.substringAfterLast('/').takeIf { it.matches(NOTE_NAME) }?.let { File(cards, it) } }
                .filter { it.exists() }.joinToString("|") { it.absolutePath }
            val v = Visitor(uid = uid, deviceName = g(r, "Device"), name = g(r, "Name"), title = g(r, "Title"),
                company = g(r, "Company"), email = g(r, "Email"), extraEmails = g(r, "ExtraEmails"), website = g(r, "Website"), phone = g(r, "Phone"), extraPhones = g(r, "ExtraPhones"),
                address = g(r, "Address"), state = g(r, "State"), country = g(r, "Country"),
                customerType = g(r, "CustomerType"), marketExposure = g(r, "MarketExposure"), products = g(r, "Products"), otherDetail = g(r, "OtherDetail"),
                additionalInterest = g(r, "AdditionalInterest"), notePhotoPaths = notes,
                cardPhotoPath = photo, cardBackPhotoPath = backPhoto, createdAt = g(r, "TimestampMs").toLongOrNull() ?: System.currentTimeMillis(),
                attendedByKey = g(r, "AttendedByKey"), attendedByName = g(r, "AttendedBy"),
                attendedByTitle = g(r, "AttendedByTitle"), attendedByEmail = g(r, "AttendedByEmail"),
                attendedByPhone = g(r, "AttendedByPhone"),
                leadOutcomes = g(r, "LeadOutcome"), discussionNotes = g(r, "DiscussionNotes"),
                remarks = g(r, "Remarks"), remarksOther = g(r, "RemarksOther"),
                followUpAction = g(r, "FollowUpAction"), followUpAt = g(r, "FollowUpAtMs").toLongOrNull() ?: 0L,
                followUpNote = g(r, "FollowUpNote"),
                // reminder statuses are not imported: the phone that captured the visit sends its reminders
                factoryVisitAt = g(r, "FactoryVisitAtMs").toLongOrNull() ?: 0L)
            if (dao.insert(v) == -1L) skipped++ else added++
        }
        return added to skipped
    }
}
