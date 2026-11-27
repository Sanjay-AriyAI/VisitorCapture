package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Test

class MailCcTest {
    @Test fun parsesCommaSemicolonSpaceAndNewLineSeparatedLists() {
        assertEquals(listOf("a@x.com", "b@y.com", "c@z.com", "d@w.com"),
            SmtpSettings.parseCc(" a@x.com, b@y.com;c@z.com\nd@w.com  "))
        assertEquals(emptyList<String>(), SmtpSettings.parseCc(""))
        assertEquals(emptyList<String>(), SmtpSettings.parseCc(" , ;\n"))
    }

    @Test fun parsingDropsDuplicatesIgnoringCase() {
        assertEquals(listOf("Boss@X.com"), SmtpSettings.parseCc("Boss@X.com, boss@x.com"))
    }

    @Test fun ccLeavesOutInvalidAndAddressesAlreadyOnTheMail() {
        val s = SmtpSettings(cc = listOf("boss@naren.example", "bad-address", "anil@sunrise.example", "sales@naren.example", "BOSS@naren.example"))
        assertEquals(listOf("boss@naren.example", "sales@naren.example"), s.ccFor(listOf("Anil@Sunrise.example")))
        assertEquals(emptyList<String>(), SmtpSettings().ccFor(listOf("anil@sunrise.example")))
    }

    @Test fun bccLeavesOutAddressesAlreadyInToOrCc() {
        val s = SmtpSettings(cc = listOf("boss@naren.example"), bcc = listOf("Boss@naren.example", "audit@naren.example", "oops"))
        val to = listOf("anil@sunrise.example")
        val cc = s.ccFor(to)
        assertEquals(listOf("audit@naren.example"), s.bccFor(to + cc))
    }
}
