package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UidGenTest {
    private val at = 1_794_000_000_000L

    @Test fun staffNameGivesThreeUppercaseLetters() {
        assertTrue(UidGen.prefix("Sivabalan R", "moto g54", at).startsWith("SIV-"))
    }

    @Test fun fallsBackToDeviceThenVis() {
        assertTrue(UidGen.prefix("", "moto g54", at).startsWith("MOT-"))
        assertTrue(UidGen.prefix("", "", at).startsWith("VIS-"))
    }

    @Test fun accentsAndShortNamesAreHandled() {
        assertTrue(UidGen.prefix("Éa", "", at).startsWith("EAX-"))
    }

    @Test fun sequenceIsTwoDigitsAndValid() {
        val uid = UidGen.withSeq(UidGen.prefix("Siva", "d", at), 0)
        assertTrue(uid.matches(Regex("[A-Z]{3}-\\d{2}-\\d{4}-0001")))
        assertEquals("0002", UidGen.withSeq("X-", 1).takeLast(4))
    }
}
