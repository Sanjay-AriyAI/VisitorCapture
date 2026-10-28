package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTest {
    private fun visitor(followUpAt: Long) = Visitor(uid = "0f8c1d2e-1111-2222-3333-444455556666", deviceName = "Stand-A",
        name = "Anil Kumar", title = "", company = "Sunrise Textiles", email = "", phone = "",
        customerType = "OEM", products = "", otherDetail = "", cardPhotoPath = null, createdAt = 1_760_000_000_000,
        leadOutcomes = "Sample Request; Factory Visit", discussionNotes = "Needs 20 pcs, \"urgent\"\nsecond line",
        remarks = "Others", remarksOther = "Price list asked", followUpAction = "Schedule Factory Visit",
        followUpAt = followUpAt, followUpNote = "")

    private fun row(v: Visitor): Map<String, String> {
        val rows = Sync.parseCsv(Sync.toCsv(listOf(v)))
        assertEquals(2, rows.size)
        return rows[0].zip(rows[1]).toMap()
    }

    @Test fun leadOutcomeColumnsRoundTrip() {
        val r = row(visitor(followUpAt = 1_761_000_000_000))
        assertEquals("Sample Request; Factory Visit", r["LeadOutcome"])
        assertEquals("Needs 20 pcs, \"urgent\"\nsecond line", r["DiscussionNotes"])
        assertEquals("Others", r["Remarks"])
        assertEquals("Price list asked", r["RemarksOther"])
        assertEquals("Schedule Factory Visit", r["FollowUpAction"])
        assertEquals("1761000000000", r["FollowUpAtMs"])
        assertEquals(16, r["FollowUpDate"]!!.length)   // yyyy-MM-dd HH:mm in the phone's time zone
        assertEquals("", r["Photo"])
    }

    @Test fun noFollowUpDateExportsBlank() {
        val r = row(visitor(followUpAt = 0))
        assertEquals("", r["FollowUpDate"])
        assertEquals("", r["FollowUpAtMs"])
    }
}
