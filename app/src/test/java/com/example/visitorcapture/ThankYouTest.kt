package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThankYouTest {
    private fun visitor() = Visitor(uid = "u1", deviceName = "Stand-A", name = "Anil Kumar", title = "", company = "Sunrise Textiles",
        email = "anil@sunrise.example", phone = "", customerType = "OEM", products = "Mechanical Spares > Carding > LMW; Retrofits > Blow Room > LMW > Control panel PLC conversion",
        otherDetail = "", cardPhotoPath = null, createdAt = 0L,
        attendedByName = "Priya Rao", attendedByTitle = "Sales Manager", attendedByEmail = "priya@naren.example", attendedByPhone = "+91 90000 11111")

    @Test fun onlyPlausibleAddressesAreQueued() {
        assertEquals(ThankYou.PENDING, ThankYou.initialStatus("anil@sunrise.example"))
        assertEquals(ThankYou.PENDING, ThankYou.initialStatus(" anil@sunrise.co.in "))
        assertEquals("", ThankYou.initialStatus(""))
        assertEquals("", ThankYou.initialStatus("anil@sunrise"))
        assertEquals("", ThankYou.initialStatus("anil sunrise.example"))
        assertEquals("", ThankYou.initialStatus("a@b.com\nBcc: x@y.com"))
    }

    @Test fun bodyIsPersonalisedFromTheVisit() {
        val b = ThankYou.body(visitor(), "Naren Group")
        assertTrue(b.startsWith("Dear Anil Kumar,"))
        assertTrue(b.contains(Brand.intro.first().first))
        assertTrue(b.contains("Carding"))
        assertTrue(b.contains("Control panel PLC conversion"))
        assertTrue(b.contains("+91 90000 11111"))
        assertTrue(b.trimEnd().endsWith("Warm regards,\n${Brand.signature}"))
        assertFalse(b.contains("Sales Manager"))
    }

    @Test fun bodyCopesWithABlankVisitor() {
        val b = ThankYou.body(visitor().copy(name = "", company = "", products = "", attendedByName = "", attendedByPhone = ""), "Naren Group")
        assertTrue(b.startsWith("Dear Sir/Madam,"))
        assertFalse(b.contains("You showed interest"))
        assertTrue(b.contains(Brand.signature))
    }

    private fun fullVisitor() = visitor().copy(title = "Purchase Head", phone = "+91 98765 43210",
        address = "12, Industrial Estate\nPeenya", state = "Karnataka", country = "India",
        marketExposure = "Domestic Visitor", additionalInterest = "Spindle tapes, 200 nos",
        leadOutcomes = "Product Enquiry; Sample Request", createdAt = 1_790_000_000_000L,
        followUpAction = "Send Quotation", followUpAt = 1_790_500_000_000L,
        discussionNotes = "INTERNAL: offered 12% discount", remarks = "Others", remarksOther = "INTERNAL remark")

    private val naren = CompanyInfo(address = "1, Mill Road\nCoimbatore 641014", phone = "+91 422 000 0000",
        email = "sales@naren.example", website = "www.naren.example")

    @Test fun htmlBodyCarriesTheVisitAndOurCompanyDetails() {
        val h = ThankYou.htmlBody(fullVisitor(), "Naren Group", ThankYou.LOGO_CID, info = naren)
        listOf("Anil Kumar", Brand.intro.first().first, Brand.signature, "OEM", "Domestic Visitor",
            "Mechanical Spares", "Carding", "Retrofits", "Blow Room", "Control panel PLC conversion",
            "Spindle tapes, 200 nos", "Product Enquiry", "Sample Request", "quotation", "Priya Rao", "Sales Manager",
            "Contact us", "1, Mill Road<br>Coimbatore 641014", "tel:+914220000000", "mailto:sales@naren.example",
            "https://www.naren.example", "cid:${ThankYou.LOGO_CID}").forEach { assertTrue("missing: $it", h.contains(it)) }
    }

    @Test fun htmlBodyDoesNotEchoTheVisitorsOwnContactDetails() {
        val h = ThankYou.htmlBody(fullVisitor(), "Naren Group", info = naren)
        listOf("Your contact details", "Purchase Head", "+91 98765 43210", "Industrial Estate", "Karnataka")
            .forEach { assertFalse("leaked: $it", h.contains(it)) }
        assertFalse(ThankYou.htmlBody(fullVisitor(), "Naren Group").contains("Contact us"))   // nothing entered: no block
    }

    @Test fun htmlBodyNeverLeaksInternalStaffNotes() {
        val h = ThankYou.htmlBody(fullVisitor(), "Naren Group")
        assertFalse(h.contains("INTERNAL"))
        assertFalse(h.contains("cid:"))                     // no logo given: company name as text
    }

    @Test fun htmlBodyEscapesWhatWasTyped() {
        val h = ThankYou.htmlBody(visitor().copy(name = "<script>alert(1)</script> & Co"), "Naren Group")
        assertFalse(h.contains("<script>"))
        assertTrue(h.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Co"))
    }

    @Test fun htmlBodyLeavesOutEmptySections() {
        val h = ThankYou.htmlBody(visitor().copy(name = "", company = "", products = "", customerType = "",
            attendedByName = "", attendedByTitle = "", attendedByPhone = ""), "Naren Group")
        assertTrue(h.contains("Dear Sir/Madam,"))
        assertFalse(h.contains("Products of interest"))
        assertFalse(h.contains("Your profile"))
        assertFalse(h.contains("Next step"))
    }

    /** Writes app/build/thank-you-preview.html so the layout can be checked in a browser. */
    @Test fun writePreview() {
        val logo = java.io.File("src/main/res/drawable-nodpi/naren_logo.png")
        val html = ThankYou.htmlBody(fullVisitor(), "Naren Group", "LOGO", info = naren).replace("cid:LOGO",
            if (logo.exists()) "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(logo.readBytes()) else "")
        java.io.File("build").mkdirs()
        java.io.File("build/thank-you-preview.html").writeText(html)
    }

    private fun mailed() = fullVisitor().copy(thankYouStatus = ThankYou.SENT, thankYouSentAt = 1_790_100_000_000L)

    @Test fun editingVisitorDetailsAfterTheMailWentOutSendsAnUpdate() {
        assertTrue(ThankYou.needsUpdateMail(mailed(), mailed().copy(customerType = "Dealer")))
        assertFalse(ThankYou.needsUpdateMail(mailed(), mailed().copy(phone = "+91 11111 22222")))   // not shown in the mail
        assertTrue(ThankYou.needsUpdateMail(mailed(), mailed().copy(products = "Retrofits > Blow Room")))
        assertTrue(ThankYou.needsUpdateMail(mailed(), mailed().copy(leadOutcomes = "Order")))
    }

    @Test fun noUpdateMailForInternalEditsOrUnsentRecords() {
        assertFalse(ThankYou.needsUpdateMail(mailed(), mailed()))
        assertFalse(ThankYou.needsUpdateMail(mailed(), mailed().copy(discussionNotes = "changed", remarks = "Call Back")))
        assertFalse(ThankYou.needsUpdateMail(mailed().copy(thankYouStatus = ThankYou.PENDING), mailed().copy(phone = "1")))
        assertFalse(ThankYou.needsUpdateMail(mailed(), mailed().copy(email = "not-an-address")))
    }

    @Test fun updatedMailSaysItIsTheFinalVersion() {
        val h = ThankYou.htmlBody(mailed(), "Naren Group", updated = true)
        assertTrue(h.contains("Final version"))
        assertTrue(h.contains("final version"))
        assertTrue(ThankYou.body(mailed(), "Naren Group", updated = true).contains("final version"))
        assertTrue(ThankYou.subject("Naren Group", updated = true).startsWith("Updated:"))
        assertFalse(ThankYou.htmlBody(mailed(), "Naren Group").contains("Final version"))
        assertTrue(ThankYou.isUpdate(mailed()))
    }

    @Test fun writeUpdatedPreview() {
        val logo = java.io.File("src/main/res/drawable-nodpi/naren_logo.png")
        val html = ThankYou.htmlBody(mailed(), "Naren Group", "LOGO", updated = true).replace("cid:LOGO",
            if (logo.exists()) "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(logo.readBytes()) else "")
        java.io.File("build").mkdirs()
        java.io.File("build/thank-you-updated-preview.html").writeText(html)
    }

    @Test fun followUpIsMentionedWhenPlanned() {
        val b = ThankYou.body(visitor().copy(followUpAction = "Schedule Factory Visit", followUpAt = 1_800_000_000_000L), "Naren Group")
        assertTrue(b.contains("factory"))
    }
}
