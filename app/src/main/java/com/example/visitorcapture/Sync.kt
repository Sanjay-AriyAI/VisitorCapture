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
        "AttendedBy", "AttendedByTitle", "AttendedByEmail", "AttendedByPhone", "AttendedByKey", "Photo")

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    private fun hasPhoto(v: Visitor) = v.cardPhotoPath?.let { File(it).exists() } == true

    fun toCsv(list: List<Visitor>): String {
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val sb = StringBuilder(HEADERS.joinToString(",")).append("\n")
        list.forEach { v ->
            val at = Date(v.createdAt)
            sb.append(listOf(v.uid, v.deviceName, dateFmt.format(at), timeFmt.format(at), isoFmt.format(at), v.createdAt.toString(),
                v.name, v.title, v.company, v.phone, v.email, v.address, v.state, v.country,
                v.customerType, v.marketExposure, v.products, v.otherDetail, v.additionalInterest,
                v.attendedByName, v.attendedByTitle, v.attendedByEmail, v.attendedByPhone, v.attendedByKey,
                if (hasPhoto(v)) "photos/${v.uid}.jpg" else "").joinToString(",") { q(it) }).append("\n")
        }
        return sb.toString()
    }

    /** Zip with visitors.csv + photos/<uid>.jpg */
    fun exportZip(ctx: Context, list: List<Visitor>, device: String): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val safe = device.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "device" }
        val f = File(dir, "visitors-$safe-$stamp.zip")
        ZipOutputStream(f.outputStream().buffered()).use { z ->
            z.putNextEntry(ZipEntry("visitors.csv")); z.write(toCsv(list).toByteArray(Charsets.UTF_8)); z.closeEntry()
            list.filter(::hasPhoto).forEach { v ->
                z.putNextEntry(ZipEntry("photos/${v.uid}.jpg"))
                File(v.cardPhotoPath!!).inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return f
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
        ctx.contentResolver.openInputStream(uri)?.use { raw ->
            val zin = ZipInputStream(raw.buffered())
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    if (e.name == "visitors.csv") csvText = zin.readBytes().toString(Charsets.UTF_8)
                    else if (e.name.startsWith("photos/")) {
                        val n = e.name.substringAfterLast('/')
                        if (n.matches(Regex("[A-Za-z0-9-]{8,64}\\.jpg"))) File(cards, n).outputStream().use { zin.copyTo(it) }
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
            val v = Visitor(uid = uid, deviceName = g(r, "Device"), name = g(r, "Name"), title = g(r, "Title"),
                company = g(r, "Company"), email = g(r, "Email"), phone = g(r, "Phone"),
                address = g(r, "Address"), state = g(r, "State"), country = g(r, "Country"),
                customerType = g(r, "CustomerType"), marketExposure = g(r, "MarketExposure"), products = g(r, "Products"), otherDetail = g(r, "OtherDetail"),
                additionalInterest = g(r, "AdditionalInterest"),
                cardPhotoPath = photo, createdAt = g(r, "TimestampMs").toLongOrNull() ?: System.currentTimeMillis(),
                attendedByKey = g(r, "AttendedByKey"), attendedByName = g(r, "AttendedBy"),
                attendedByTitle = g(r, "AttendedByTitle"), attendedByEmail = g(r, "AttendedByEmail"),
                attendedByPhone = g(r, "AttendedByPhone"))
            if (dao.insert(v) == -1L) skipped++ else added++
        }
        return added to skipped
    }
}
