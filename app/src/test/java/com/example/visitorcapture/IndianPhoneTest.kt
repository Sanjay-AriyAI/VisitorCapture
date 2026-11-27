package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IndianPhoneTest {
    @Test fun mobilesBecomeTenDigits() {
        assertEquals("6493792791", IndianPhone.mobile("6493792791"))
        assertEquals("9876543210", IndianPhone.mobile("+91 98765 43210"))
        assertEquals("9876543210", IndianPhone.mobile("098765-43210"))
        assertEquals("9123456789", IndianPhone.mobile("9123456789"))   // a mobile starting 91 keeps its digits
    }
    @Test fun notMobiles() {
        assertNull(IndianPhone.mobile("98765 4321"))      // 9 digits
        assertNull(IndianPhone.mobile("0422 2673333"))    // landline
        assertNull(IndianPhone.mobile("5876543210"))      // mobiles start with 6-9
    }
    @Test fun landlinesGetTheirStdCode() {
        assertEquals("0422 2673333", IndianPhone.office("0422 2673333"))
        assertEquals("0422 2673333", IndianPhone.office("+91 422 2673333"))
        assertEquals("0422 2673333", IndianPhone.office("04222673333"))
        assertEquals("0422 267333", IndianPhone.office("0422267333"))
        assertEquals("044 23456789", IndianPhone.office("+91-44-2345 6789"))   // Chennai, two-digit STD code
        assertNull(IndianPhone.office("98765 43210"))                          // a mobile is not an office number
        assertNull(IndianPhone.office("2673333"))                              // no STD code
    }
    @Test fun typing() {
        assertEquals("9876543210", IndianPhone.typingMobile("+91 98765 43210"))
        assertEquals("98765", IndianPhone.typingMobile("98765"))
        assertEquals("1234567890", IndianPhone.typingMobile("123456789012"))
        assertEquals("0422 267333", IndianPhone.typingOffice("0422-267333"))
    }
    @Test fun onlyForIndianVisitors() {
        assertTrue(IndianPhone.applies("", ""))
        assertTrue(IndianPhone.applies("India", ""))
        assertFalse(IndianPhone.applies("Bangladesh", ""))
    }
}
