package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardParserTest {
    @Test fun indianCardGetsAddressStateAndCountry() {
        val p = parseCard("""
            Anil Kumar
            Procurement Manager
            Sunrise Textiles Pvt Ltd
            Plot 21, Sector 4, GIDC Estate
            Coimbatore, Tamil Nadu 641004
            India
            Tel: +91 98765 43210
            anil@sunrise-textiles.example
            www.sunrise-textiles.example
        """.trimIndent())
        assertEquals("Anil Kumar", p.name)
        assertEquals("Procurement Manager", p.title)
        assertEquals("Sunrise Textiles Pvt Ltd", p.company)
        assertEquals("+91 98765 43210", p.phone)
        assertEquals("anil@sunrise-textiles.example", p.email)
        assertEquals("Plot 21, Sector 4, GIDC Estate, Coimbatore, Tamil Nadu 641004, India", p.address)
        assertEquals("Tamil Nadu", p.state)
        assertEquals("India", p.country)
    }

    @Test fun foreignCardGetsCountryButNoState() {
        val p = parseCard("""
            Maria Lopez
            Plant Engineer
            Norte Fibras SA
            Av. Industrial 450
            Lima, Peru
            +51 1 234 5678
            maria@nortefibras.example
        """.trimIndent())
        assertEquals("Maria Lopez", p.name)
        assertEquals("Plant Engineer", p.title)
        assertEquals("Av. Industrial 450, Lima, Peru", p.address)
        assertEquals("", p.state)
        assertEquals("Peru", p.country)
    }

    @Test fun ukCard() {
        val p = parseCard("John Smith\nDirector\nAcme Systems Ltd\n12 High Street, London EC1A 1BB\nUnited Kingdom\n+44 20 7946 0958")
        assertEquals("John Smith", p.name)
        assertEquals("Acme Systems Ltd", p.company)
        assertEquals("12 High Street, London EC1A 1BB, United Kingdom", p.address)
        assertEquals("United Kingdom", p.country)
        assertEquals("", p.state)
    }

    @Test fun cardWithoutAddressLeavesFieldsBlank() {
        val p = parseCard("Priya Rao\nSales Manager\nOrbit Group\npriya@orbit.example\n+91 90000 11111")
        assertEquals("Priya Rao", p.name)
        assertEquals("", p.address)
        assertEquals("", p.state)
        assertEquals("", p.country)
    }

    @Test fun indianStateImpliesIndia() {
        val p = parseCard("Ravi Patel\nOwner\nPatel Industries\nSurat, Gujarat")
        assertEquals("Gujarat", p.state)
        assertEquals("India", p.country)
    }

    @Test fun shortCountryCodesDoNotFireInsideWords() {
        // "uk" inside "Duke" and "usa" inside "Musa" must not be taken as countries
        val p = parseCard("Musa Duke\nManager\nDuke Traders Ltd")
        assertEquals("", p.country)
    }

    @Test fun nameIsNotTakenFromAnAddressLine() {
        val p = parseCard("Industrial Estate Road\nAnil Kumar\nSales Manager\nSunrise Ltd\nPune, Maharashtra")
        assertEquals("Anil Kumar", p.name)
        assertEquals("Maharashtra", p.state)
    }
}

class VCardTest {
    @Test fun vcard30() {
        val e = VCard.parse("""
            BEGIN:VCARD
            VERSION:3.0
            N:Sharma;Priya;;;
            FN:Priya Sharma
            ORG:Naren Engineering;Service
            TITLE:Service Engineer
            TEL;TYPE=CELL:+91 98765 00000
            EMAIL;TYPE=WORK:Priya@Naren.example
            END:VCARD
        """.trimIndent())
        assertNotNull(e)
        assertEquals("Priya Sharma", e!!.name)
        assertEquals("Naren Engineering", e.company)
        assertEquals("Service Engineer", e.title)
        assertEquals("+91 98765 00000", e.phone)
        assertEquals("Priya@Naren.example", e.email)
        assertEquals("priya@naren.example", e.key)
    }

    @Test fun vcard21WithoutFnUsesNField() {
        val e = VCard.parse("BEGIN:VCARD\r\nVERSION:2.1\r\nN:Kumar;Rahul;Singh\r\nTEL;CELL:98765 11111\r\nEMAIL;INTERNET:rahul@naren.example\r\nEND:VCARD\r\n")
        assertEquals("Rahul Singh Kumar", e!!.name)
        assertEquals("98765 11111", e.phone)
        assertEquals("rahul@naren.example", e.email)
    }

    @Test fun vcard40TelUriAndGroupPrefix() {
        val e = VCard.parse("BEGIN:VCARD\nVERSION:4.0\nitem1.FN:Asha Menon\nitem1.TEL;VALUE=uri:tel:+91-98765-22222\nEND:VCARD")
        assertEquals("Asha Menon", e!!.name)
        assertEquals("+91-98765-22222", e.phone)
    }

    @Test fun foldedLinesAreJoined() {
        val e = VCard.parse("BEGIN:VCARD\nVERSION:3.0\nFN:Priya\n  Sharma\nORG:Naren\nEND:VCARD")
        assertEquals("Priya Sharma", e!!.name)
    }

    @Test fun escapedCommaInOrganisation() {
        val e = VCard.parse("BEGIN:VCARD\nFN:Dev Rao\nORG:Naren\\, Coimbatore;Sales\nEND:VCARD")
        assertEquals("Naren, Coimbatore", e!!.company)
    }

    @Test fun meCard() {
        val e = VCard.parse("MECARD:N:Menon,Asha;TEL:9876533333;EMAIL:asha@naren.example;ORG:Naren;;")
        assertEquals("Asha Menon", e!!.name)
        assertEquals("9876533333", e.phone)
        assertEquals("asha@naren.example", e.email)
    }

    @Test fun keyFallsBackToPhoneThenName() {
        assertEquals("919876544444", EmployeeInfo(name = "A", phone = "+91 98765 44444").key)
        assertEquals("a b", EmployeeInfo(name = "A B").key)
    }

    @Test fun nonContactQrCodesAreRejected() {
        assertNull(VCard.parse("https://example.com/staff/42"))
        assertNull(VCard.parse(""))
        assertNull(VCard.parse("BEGIN:VCARD\nVERSION:3.0\nEND:VCARD"))   // nothing identifying
    }
}
