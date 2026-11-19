package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("", r["HandwrittenNotes"])
    }

    @Test fun handwrittenNotesExportExistingFilesOnly() {
        val dir = java.nio.file.Files.createTempDirectory("notes").toFile()
        val a = java.io.File(dir, "0f8c1d2e_note_1.jpg").apply { writeBytes(byteArrayOf(1)) }
        val b = java.io.File(dir, "0f8c1d2e_note_2.png").apply { writeBytes(byteArrayOf(2)) }
        val gone = java.io.File(dir, "0f8c1d2e_note_3.jpg")
        val r = row(visitor(followUpAt = 0).copy(notePhotoPaths = listOf(a, gone, b).joinToString("|") { it.absolutePath }))
        val folder = "leads/0f8c1d2e-1111-2222-3333-444455556666 - Anil Kumar - Sunrise Textiles"
        assertEquals("$folder/handwritten-note-1.jpg; $folder/handwritten-note-2.png", r["HandwrittenNotes"])
        dir.deleteRecursively()
    }

    @Test fun leadFolderStripsUnsafeCharacters() {
        val v = visitor(followUpAt = 0).copy(name = "  Anil  Kumar ", company = "A/B: \"Textiles\" <Pvt>?")
        assertEquals("leads/0f8c1d2e-1111-2222-3333-444455556666 - Anil Kumar - A B Textiles Pvt", Sync.leadFolder(v))
        assertEquals("leads/0f8c1d2e-1111-2222-3333-444455556666", Sync.leadFolder(v.copy(name = "", company = "")))
    }

    @Test fun detailsTextListsEnteredFieldsOnly() {
        val t = Sync.detailsText(visitor(followUpAt = 0).copy(products = "Pumps > A1; Valves", phone = ""))
        assertTrue("Name: Anil Kumar" in t)
        assertTrue("Company: Sunrise Textiles" in t)
        assertTrue("Products: Pumps > A1\r\n    Valves" in t)
        assertTrue("Discussion notes: Needs 20 pcs, \"urgent\"\r\n    second line" in t)
        assertTrue("Remarks (other): Price list asked" in t)
        assertTrue("Phone:" !in t.substringBefore("ATTENDED BY"))
        assertTrue("Follow-up date" !in t)
        assertTrue("ATTACHMENTS" !in t)
    }

    @Test fun noFollowUpDateExportsBlank() {
        val r = row(visitor(followUpAt = 0))
        assertEquals("", r["FollowUpDate"])
        assertEquals("", r["FollowUpAtMs"])
    }
}
