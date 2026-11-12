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
        assertTrue(b.contains("Sunrise Textiles"))
        assertTrue(b.contains("Carding"))
        assertTrue(b.contains("Control panel PLC conversion"))
        assertTrue(b.contains("Priya Rao"))
        assertTrue(b.contains("+91 90000 11111"))
    }

    @Test fun bodyCopesWithABlankVisitor() {
        val b = ThankYou.body(visitor().copy(name = "", company = "", products = "", attendedByName = "", attendedByPhone = ""), "Naren Group")
        assertTrue(b.startsWith("Dear Sir/Madam,"))
        assertFalse(b.contains("You showed interest"))
        assertTrue(b.contains("Naren Group"))
    }

    @Test fun followUpIsMentionedWhenPlanned() {
        val b = ThankYou.body(visitor().copy(followUpAction = "Schedule Factory Visit", followUpAt = 1_800_000_000_000L), "Naren Group")
        assertTrue(b.contains("factory"))
    }
}
