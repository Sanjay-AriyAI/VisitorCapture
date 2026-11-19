package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class DateRangeTest {
    private fun local(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis

    private fun utc(y: Int, m: Int, d: Int) =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(y, m - 1, d) }.timeInMillis

    private val today = local(2026, 11, 18)

    @Test fun singleDayCoversMidnightToMidnight() {
        val r = DateRange.days(local(2026, 11, 13, 15), local(2026, 11, 13, 9))
        assertTrue(local(2026, 11, 13, 0, 0) in r)
        assertTrue(local(2026, 11, 13, 23, 59) in r)
        assertFalse(local(2026, 11, 14, 0, 0) in r)
        assertFalse(local(2026, 11, 12, 23, 59) in r)
        assertEquals(local(2026, 11, 13), r.lastDay)
    }

    @Test fun reversedDaysAreSwapped() {
        assertEquals(DateRange.days(local(2026, 11, 10), local(2026, 11, 13)),
            DateRange.days(local(2026, 11, 13), local(2026, 11, 10)))
    }

    @Test fun presets() {
        assertEquals(DateRange(local(2026, 11, 18), local(2026, 11, 19)), DatePreset.TODAY.range(today))
        assertEquals(DateRange(local(2026, 11, 17), local(2026, 11, 18)), DatePreset.YESTERDAY.range(today))
        assertEquals(DateRange(local(2026, 11, 12), local(2026, 11, 19)), DatePreset.LAST_7.range(today))
        assertEquals(DateRange(local(2026, 11, 1), local(2026, 11, 19)), DatePreset.THIS_MONTH.range(today))
    }

    @Test fun pickerUtcDaysMapToLocalDays() {
        assertEquals(DateRange.days(local(2026, 11, 10), local(2026, 11, 13)),
            DateRange.fromPicker(utc(2026, 11, 10), utc(2026, 11, 13)))
        // only a start picked: that single day
        assertEquals(DateRange.days(local(2026, 11, 10), local(2026, 11, 10)), DateRange.fromPicker(utc(2026, 11, 10), null))
        assertEquals(utc(2026, 11, 13), utcDay(local(2026, 11, 13, 23, 30)))
    }

    @Test fun labelsAndTags() {
        val l = Locale.ENGLISH
        assertEquals("13 Nov 2026", DateRange.days(local(2026, 11, 13), local(2026, 11, 13)).label(l))
        assertEquals("10 – 13 Nov 2026", DateRange.days(local(2026, 11, 10), local(2026, 11, 13)).label(l))
        assertEquals("28 Oct – 2 Nov 2026", DateRange.days(local(2026, 10, 28), local(2026, 11, 2)).label(l))
        assertEquals("30 Dec 2025 – 2 Jan 2026", DateRange.days(local(2025, 12, 30), local(2026, 1, 2)).label(l))
        assertEquals("20261113", DateRange.days(local(2026, 11, 13), local(2026, 11, 13)).tag())
        assertEquals("20261110_20261113", DateRange.days(local(2026, 11, 10), local(2026, 11, 13)).tag())
    }

    @Test fun exportFileName() {
        val range = DateRange.days(local(2026, 11, 10), local(2026, 11, 13))
        assertEquals("Sanjay_S-moto_g54_5G-20261110-20261113.zip", Sync.exportName("Sanjay S.", "moto g54 5G", range, emptyList()))
        // single day: from and to are the same date
        assertEquals("Sanjay-moto_g54_5G-20261117-20261117.zip",
            Sync.exportName("Sanjay", "moto g54 5G", DateRange.days(local(2026, 11, 17), local(2026, 11, 17)), emptyList()))
        // all dates: first and last visit in the export
        fun visit(at: Long) = Visitor(uid = "u$at", deviceName = "d", name = "", title = "", company = "", email = "", phone = "",
            customerType = "", products = "", otherDetail = "", cardPhotoPath = null, createdAt = at, additionalInterest = "",
            leadOutcomes = "", discussionNotes = "", remarks = "", remarksOther = "",
            attendedByName = "", attendedByTitle = "", attendedByEmail = "", attendedByPhone = "")
        val list = listOf(visit(local(2026, 11, 18, 9)), visit(local(2026, 11, 12, 17)))
        assertEquals("Sanjay-moto_g54_5G-20261112-20261118.zip", Sync.exportName("Sanjay", "moto g54 5G", null, list))
        // no agent signed in / blank device
        assertEquals("agent-device.zip", Sync.exportName(" ", "", null, emptyList()))
    }
}
