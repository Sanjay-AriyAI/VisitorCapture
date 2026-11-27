package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {
    private val now = 1_800_000_000_000L
    private val day = Reminder.DAY_MS

    private fun visitor() = Visitor(uid = "u1", deviceName = "Stand-A", name = "Anil Kumar", title = "", company = "Sunrise Textiles",
        email = "anil@sunrise.example", phone = "", customerType = "OEM",
        products = "Mechanical Spares > Carding > LMW; Retrofits > Blow Room > LMW > Control panel PLC conversion",
        otherDetail = "", cardPhotoPath = null, createdAt = now, additionalInterest = "Spindle tapes, 200 nos",
        leadOutcomes = "Product Enquiry; Factory Visit; Follow-up",
        discussionNotes = "INTERNAL: offered 12% discount", remarks = "Others", remarksOther = "INTERNAL remark",
        attendedByName = "Priya Rao", attendedByTitle = "Sales Manager", attendedByEmail = "priya@naren.example",
        attendedByPhone = "+91 90000 11111", factoryVisitAt = now + 3 * day, followUpAt = now + 5 * day)

    @Test fun remindsTwentyFourHoursBefore() {
        val at = now + 3 * day
        assertEquals(at - day, Reminder.remindAt(at))
        assertFalse(Reminder.isDue(at, now))
        assertTrue(Reminder.isDue(at, at - day))
    }

    @Test fun lessThanADayAwayIsDueStraightAway() {
        assertTrue(Reminder.isDue(now + 5 * 60 * 60 * 1000L, now))
    }

    @Test fun onlyFutureTimesWithSomeoneToMailAreScheduled() {
        val v = visitor()
        assertEquals(Reminder.SCHEDULED, Reminder.initialStatus(now + day, v, now))
        assertEquals("", Reminder.initialStatus(now - 1, v, now))
        assertEquals("", Reminder.initialStatus(0L, v, now))
        assertEquals("", Reminder.initialStatus(now + day, v.copy(email = "nope", attendedByEmail = ""), now))
        assertEquals(Reminder.SCHEDULED, Reminder.initialStatus(now + day, v.copy(email = ""), now))   // staff still reminded
    }

    @Test fun visitorInToStaffInCc() {
        assertEquals("anil@sunrise.example" to "priya@naren.example", ReminderMail.recipients(visitor()))
        assertEquals("priya@naren.example" to null, ReminderMail.recipients(visitor().copy(email = "")))
        assertEquals("anil@sunrise.example" to null, ReminderMail.recipients(visitor().copy(attendedByEmail = "")))
        assertNull(ReminderMail.recipients(visitor().copy(email = "", attendedByEmail = "")))
    }

    @Test fun eachKindUsesItsOwnTime() {
        val v = visitor()
        assertEquals(v.factoryVisitAt, Reminder.FACTORY.at(v))
        assertEquals(v.followUpAt, Reminder.FOLLOW_UP.at(v))
        assertTrue(ReminderMail.subject(Reminder.FACTORY, v.factoryVisitAt, "Naren Group").startsWith("Reminder: Factory visit on "))
        assertTrue(ReminderMail.subject(Reminder.FOLLOW_UP, v.followUpAt, "Naren Group").startsWith("Reminder: Follow-up on "))
    }

    @Test fun mailCarriesTheEnquiryButNotInternalNotes() {
        for (kind in Reminder.values()) {
            val plain = ReminderMail.body(visitor(), kind, "Naren Group")
            val html = ReminderMail.htmlBody(visitor(), kind, "Naren Group", ThankYou.LOGO_CID)
            for (b in listOf(plain, html)) {
                listOf("Anil Kumar", "Carding", "Control panel PLC conversion", "Spindle tapes, 200 nos", "Product Enquiry", "Priya Rao")
                    .forEach { assertTrue("$kind missing: $it", b.contains(it)) }
                assertFalse(b.contains("INTERNAL remark"))
                assertEquals(Brand.mailShowsVisitDescription, b.contains("offered 12% discount"))   // shown only where the company sends it
            }
            assertTrue(html.contains("cid:${ThankYou.LOGO_CID}"))
        }
        assertTrue(ReminderMail.body(visitor(), Reminder.FACTORY, "Naren Group").contains("factory visit"))
        assertTrue(ReminderMail.body(visitor(), Reminder.FOLLOW_UP, "Naren Group").contains("follow-up"))
    }

    @Test fun thankYouMailMentionsThePlannedVisit() {
        assertTrue(ThankYou.nextStep(visitor()).contains("welcoming you at our factory"))
    }
}
