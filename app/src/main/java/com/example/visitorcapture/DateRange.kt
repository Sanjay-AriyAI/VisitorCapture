package com.example.visitorcapture

import java.text.SimpleDateFormat
import java.util.*

/**
 * Visit-date filter for the log list and exports, in this phone's time zone.
 * [from] is the start of the first day (inclusive), [to] the start of the day after the last day (exclusive).
 */
data class DateRange(val from: Long, val to: Long) {
    operator fun contains(at: Long) = at in from until to

    /** Last day of the range (its local midnight). */
    val lastDay: Long get() = addDays(to, -1)

    /** "13 Nov 2026" for one day, "10 – 13 Nov 2026" / "28 Oct – 2 Nov 2026" / "30 Dec 2025 – 2 Jan 2026" for several. */
    fun label(locale: Locale = Locale.getDefault()): String {
        val a = Calendar.getInstance().apply { timeInMillis = from }
        val b = Calendar.getInstance().apply { timeInMillis = lastDay }
        val full = SimpleDateFormat("d MMM yyyy", locale)
        if (from == lastDay) return full.format(Date(from))
        val start = when {
            a.get(Calendar.YEAR) != b.get(Calendar.YEAR) -> full
            a.get(Calendar.MONTH) != b.get(Calendar.MONTH) -> SimpleDateFormat("d MMM", locale)
            else -> SimpleDateFormat("d", locale)
        }
        return start.format(Date(from)) + " – " + full.format(Date(lastDay))
    }

    /** For file names: "20261113" or "20261110_20261113". */
    fun tag(): String {
        val f = SimpleDateFormat("yyyyMMdd", Locale.US)
        return if (from == lastDay) f.format(Date(from)) else f.format(Date(from)) + "_" + f.format(Date(lastDay))
    }

    companion object {
        /** Whole local days [firstDay]..[lastDay] (any instant within each day). */
        fun days(firstDay: Long, lastDay: Long): DateRange {
            val a = dayStart(minOf(firstDay, lastDay))
            return DateRange(a, addDays(dayStart(maxOf(firstDay, lastDay)), 1))
        }

        /** From the date picker, whose values are UTC midnights. */
        fun fromPicker(startUtc: Long, endUtc: Long?): DateRange = days(localAt(startUtc, 0, 0), localAt(endUtc ?: startUtc, 0, 0))
    }
}

/** Quick choices offered above the custom calendar. [today] is local midnight of today. */
enum class DatePreset(val label: String) {
    TODAY("Today"), YESTERDAY("Yesterday"), LAST_7("Last 7 days"), THIS_MONTH("This month");

    fun range(today: Long = startOfToday()): DateRange = when (this) {
        TODAY -> DateRange.days(today, today)
        YESTERDAY -> addDays(today, -1).let { DateRange.days(it, it) }
        LAST_7 -> DateRange.days(addDays(today, -6), today)
        THIS_MONTH -> DateRange.days(Calendar.getInstance().apply { timeInMillis = today; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis, today)
    }
}

/** Local midnight of the day holding [at]. */
internal fun dayStart(at: Long): Long = Calendar.getInstance().apply {
    timeInMillis = at
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Calendar-day arithmetic (safe across daylight-saving changes). */
internal fun addDays(day: Long, n: Int): Long = Calendar.getInstance().apply { timeInMillis = day; add(Calendar.DAY_OF_MONTH, n) }.timeInMillis

/** Local instant → the same calendar day as UTC midnight (what Material date pickers use). */
internal fun utcDay(local: Long): Long {
    val l = Calendar.getInstance().apply { timeInMillis = local }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear(); set(l.get(Calendar.YEAR), l.get(Calendar.MONTH), l.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** A date picker's UTC-midnight day + a local time → local instant. */
internal fun localAt(utcDay: Long, hour: Int, minute: Int): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcDay }
    return Calendar.getInstance().apply {
        clear(); set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), hour, minute)
    }.timeInMillis
}
