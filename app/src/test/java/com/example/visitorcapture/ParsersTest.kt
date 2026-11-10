package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val sendka = """
        SENDKA
        Belting & Conveyors
        SENTHILKUMAR N.
        PVC-PU-Cotton Conveyor | Flat Belt | Plain-Endless Teflon | PU Rope-Sheets
        Sidewall & Cleated Conveyors | Open Timing Belt | Mechanical Fasteners
        146/3, 146/3-1, Bharathi Street,
        Anjugam Nagar, Chinnavedampatti (Po),
        Coimbatore - 641 049.
        www.dkbelt.com
        +91 98435 34445
        info@dkbelt.com
        sales@sendka.com
        Tirupur Erode Madurai
    """.trimIndent()

    @Test fun logoCompanyIsFoundFromEmailDomain() {
        val p = parseCard(sendka)
        assertEquals("SENDKA", p.company)
        assertEquals("SENTHILKUMAR N.", p.name)   // not the "Belting & Conveyors" tagline
        assertEquals("", p.title)
        assertEquals("+91 98435 34445", p.phone)
        assertTrue(p.address.contains("Coimbatore - 641 049"))
    }

    @Test fun companyMatchToleratesOneOcrSlip() {
        assertEquals("SENOKA", parseCard(sendka.replaceFirst("SENDKA", "SENOKA")).company)
    }

    private val eyeshivNameBlock = "Samaira Kapoor\nGraphic Designer"
    private val eyeshivLogo = "EYESHIV\nCOMMITTED TO DELIVERING A BETTER"
    private val eyeshivContact = """
        +91-99103 69326 (WhatsApp)
        www.eyeshiv.com | www.shop.eyeshiv.com
        info@eyeshiv.com, eyeshiv@gmail.com
        38 A, Vasant Kunj Phase 1,
        New Delhi-110070
    """.trimIndent()

    @Test fun logoCompanyFromWebsiteAndWiderTitles() {
        // ML Kit may emit the logo block before or after the name block
        listOf("$eyeshivLogo\n$eyeshivNameBlock\n$eyeshivContact", "$eyeshivNameBlock\n$eyeshivLogo\n$eyeshivContact").forEach {
            val p = parseCard(it)
            assertEquals("EYESHIV", p.company)
            assertEquals("Samaira Kapoor", p.name)
            assertEquals("Graphic Designer", p.title)
            assertEquals("Delhi", p.state)
        }
    }

    // ---- regression cards from field testing --------------------------------------------------------------------

    private val dhl = """
        Anand Balaji V
        Territory Manager
        DHL Express (India) Pvt. Ltd.
        Mobile: +91 96262 50226
        Email: anand.balaji@dhl.com
        No. 2, Vijayakumar Cotton
        Press Compound,
        Thaneerpandal Road,
        Peelamedu,
        Coimbatore - 641004.
        Customer Service 1800 30 345
        1800 209 1345
        E Comm 1800 209 3345
        dhl.co.in
        DHL Express – Excellence. Simply delivered.
    """.trimIndent()

    @Test fun dhlCard() {
        val p = parseCard(dhl)
        assertEquals("Anand Balaji V", p.name)              // was "Press Compound,"
        assertEquals("Territory Manager", p.title)
        assertEquals("DHL Express (India) Pvt. Ltd.", p.company)   // was "Anand Balaji V"
        assertEquals("+91 96262 50226", p.phone)             // not a customer-service number
        assertEquals("anand.balaji@dhl.com", p.email)
        assertEquals("No. 2, Vijayakumar Cotton, Press Compound, Thaneerpandal Road, Peelamedu, Coimbatore - 641004", p.address)
        assertEquals("Tamil Nadu", p.state)                  // from the 641 PIN prefix
        assertEquals("India", p.country)
        assertTrue(p.flags.containsKey("state"))             // inferred, so the visitor is asked to check it
        assertTrue(p.flags.keys.none { it in setOf("name", "company", "phone", "email") })
    }

    @Test fun dhlCardWhenTaglineComesFirst() {
        val p = parseCard(dhl.lines().let { it.last() + "\n" + it.dropLast(1).joinToString("\n") })
        assertEquals("DHL Express (India) Pvt. Ltd.", p.company)
        assertEquals("Anand Balaji V", p.name)
    }

    @Test fun emailLocalPartIsNotTreatedAsACompanyDomain() {
        // "anand.balaji@dhl.com" used to yield the brand "anand" and so picked the person's name as the company
        val p = parseCard("Anand Balaji V\nTerritory Manager\nanand.balaji@dhl.com")
        assertEquals("Dhl", p.company)                       // the domain brand, never the person's name...
        assertTrue(p.flags["company"]!!.contains("Guessed")) // ...and flagged, because it is only a guess
        assertEquals("Anand Balaji V", p.name)
    }

    @Test fun aladdinCardWithInitialsAndLogoOnlyCompany() {
        val p = parseCard("A T Kumar\nCEO\n+9196777 77426\natkumar@aladdin365.corm\n33 UBI Avenue 3 #07-21 Vertex\nSingapore 408868")
        assertEquals("A T Kumar", p.name)                     // a lone "A" is an initial, not the article
        assertEquals("CEO", p.title)
        assertEquals("+91 96777 77426", p.phone)              // spacing normalised
        assertEquals("atkumar@aladdin365.com", p.email)       // ".corm" is not a domain
        assertTrue(p.flags.containsKey("email"))
        assertEquals("Aladdin365", p.company)                 // logo only, so guessed from the domain and flagged
        assertTrue(p.flags.containsKey("company"))
        assertEquals("Singapore", p.country)
    }

    // Verbatim scanner output from a real phone (keyboard text in the background, icons read as letters, logo misread).
    @Test fun aladdinRealScannerOutput() {
        val p = parseCard("""
            )
            backspace
            [
            P
            P
            enter
            shift
            pg
            ctrl
            Pg
            Ala lins
            AT Kumar
            CEO
            6+9196777 77426
            aladdin365.com
            V atkumar@aladdin365.com
            O 33 Ubi Avenue 3 #07-21 Vertex
            Singapore 408868
        """.trimIndent())
        assertEquals("AT Kumar", p.name)                       // "AT" is not the preposition
        assertEquals("CEO", p.title)
        assertEquals("+91 96777 77426", p.phone)
        assertEquals("atkumar@aladdin365.com", p.email)
        assertEquals("33 Ubi Avenue 3 #07-21 Vertex, Singapore 408868", p.address)   // icon letter "O" removed
        assertEquals("Singapore", p.country)
        assertEquals("", p.state)                               // 408868 is not an Indian PIN, so no state is inferred
        assertEquals("Aladdin365", p.company)
        assertTrue(p.flags.keys == setOf("company"))
    }

    @Test fun fillerWordsStillRuleOutSlogans() {
        assertEquals("", parseCard("Welcome To Naren\nThank You For Visiting").name)
        assertEquals("", parseCard("AT YOUR SERVICE").name)
    }

    @Test fun blockLetterBeforeANumberIsNotTreatedAsAnIcon() {
        assertEquals("B 12, Sector 4, Pune", parseCard("Ann Bee\nManager\nB 12, Sector 4, Pune").address)
    }

    @Test fun realTopLevelDomainsAreNeverChanged() {
        listOf("a@b.cn", "a@b.co.in", "a@b.in", "a@b.ac", "a@b.org").forEach {
            assertEquals(it, parseCard("Ann Bee\nManager\n$it").email)
        }
    }

    @Test fun sriPoojaCardWithUnreadableContactBlock() {
        // Only the large text was read; the small foil text came back as junk.
        val p = parseCard("R Bharath Kumar\nSerior Sales Executive\n4990se 87333\nNo. 2 Sira Road, Kalapatti")
        assertEquals("R Bharath Kumar", p.name)
        assertEquals("Senior Sales Executive", p.title)      // OCR slip repaired
        assertEquals("", p.phone)                            // "4990se 87333" is not a phone number...
        assertEquals("No. 2 Sira Road, Kalapatti", p.address) // ...and does not belong in the address either
        assertTrue(p.flags.containsKey("title"))             // repaired, so check it
        assertTrue(p.flags.containsKey("phone"))             // unreadable, so ask for it
        assertTrue(p.flags.containsKey("email"))
    }

    @Test fun sriPoojaCardFullyRead() {
        val p = parseCard("""
            R Bharath Kumar
            Senior Sales Executive
            +91 99526 87333
            sales.spinteriorscbe@grmail.com
            sripoojainteriors@gmail.com
            No. 2, Sitra Road, Kalapatti,
            Coimbatore - 641 014
            SP INTERIORS
            (A Unit of Sri Pooja Hardware & Electricals)
        """.trimIndent())
        assertEquals("R Bharath Kumar", p.name)
        assertEquals("Senior Sales Executive", p.title)
        assertEquals("SP INTERIORS", p.company)
        assertEquals("+91 99526 87333", p.phone)
        assertEquals("sripoojainteriors@gmail.com", p.email)  // personal-looking address beats the generic "sales." one
        assertEquals("No. 2, Sitra Road, Kalapatti, Coimbatore - 641 014", p.address)
        assertEquals("Tamil Nadu", p.state)
    }

    private val idfc = """
        DHARSHAN GOPALAKRISHNAN
        Associate Manager-Acquisition (Household)
        Branch Banking
        +91 63827 23881
        dharshan.g@idfcfirst.bank.in
        IDFC FIRST
        Bank
        ALWAYS YOU FIRST
        FIR ST
    """.trimIndent()

    @Test fun idfcCardWithMultiPartDotInDomain() {
        val p = parseCard(idfc)
        assertEquals("DHARSHAN GOPALAKRISHNAN", p.name)
        assertEquals("dharshan.g@idfcfirst.bank.in", p.email)
        assertEquals("+91 63827 23881", p.phone)
        assertEquals("", p.address)                      // "FIR ST" is the spaced-out "FIRST" logo, not a street
        assertEquals("IDFC FIRST", p.company)            // brand "idfcfirst", not the "bank" label of idfcfirst.bank.in
        assertTrue(p.flags.isEmpty())
    }

    @Test fun repairedEmailIsFlaggedButACleanOneIsNot() {
        assertTrue(parseCard("DHARSHAN G\nManager\ndharshan.g©idfcfirst.bank.in").flags.containsKey("email"))
        assertEquals("dharshan.g@idfcfirst.bank.in", parseCard("DHARSHAN G\nManager\ndharshan.g idfcfirst.bank.in").email)
        assertTrue(!parseCard(idfc).flags.containsKey("email"))
    }

    @Test fun emailSplitByAStraySpaceIsJoined() {
        val p = parseCard("R Bharath Kumar\nSenior Sales Executive\nsales.spinteriorscbe@gmail.com\nsripoojain teriors@gmail.com")
        assertEquals("sripoojainteriors@gmail.com", p.email)    // was "teriors@gmail.com"
        assertTrue(p.flags.containsKey("email"))
        assertEquals("a@b.com", parseCard("Ann Bee\nManager\nEmail a@b.com").email)   // labels are never glued on
    }

    @Test fun accentedLookalikeLettersDoNotCutTheEmailShort() {
        assertEquals("sripoojainteriors@gmail.com", parseCard("R Kumar\nManager\nsripoojaınteriors@gmail.com").email)
    }

    @Test fun companyNameEchoedInTheEmailBeatsAParentCompanyTagline() {
        val p = parseCard("R Bharath Kumar\nSenior Sales Executive\n(A Unit of Sri Pooja\nHardwae & Electricals)\nINTERIORS\n" +
            "sales.spinteriorscbe@gmail.com\nsripoojainteriors@gmail.com")
        assertEquals("INTERIORS", p.company)
        assertEquals("Hardware & Electricals", parseCard("Ann Bee\nManager\nHardware & Electricals)").company)
    }

    @Test fun pinWithLetterOInsteadOfZeroIsRepairedAndGivesTheState() {
        val p = parseCard("R Bharath Kumar\nSenior Sales Executive\nNo. 2, Sitra Road, Kalapatti,\nCoimbatore - 641 O14")
        assertEquals("No. 2, Sitra Road, Kalapatti, Coimbatore - 641 014", p.address)
        assertEquals("Tamil Nadu", p.state)
    }

    @Test fun streetAbbreviationAloneDoesNotMakeAnAddress() {
        assertEquals("", parseCard("Asha Nair\nManager\nFIR ST").address)
        assertEquals("12 Mount St, Chennai", parseCard("Asha Nair\nManager\n12 Mount St, Chennai").address)
    }

    @Test fun emailSurvivesCommonOcrMisreadsOfTheAtSign() {
        listOf("dharshan.g©idfcfirst.bank.in", "dharshan.g®idfcfirst.bank.in", "dharshan.g (at) idfcfirst.bank.in",
            "dharshan.g @ idfcfirst.bank.in", "dharshan.g@ idfcfirst.bank.in", "dharshan.g @idfcfirst.bank.in",
            "dharshan.g@idfcfirst.bank.in.", "E dharshan.g@idfcfirst.bank.in", "dharshan.g@idfcfirst.bank. in").forEach {
            assertEquals(it, "dharshan.g@idfcfirst.bank.in", parseCard("DHARSHAN G\nManager\n$it").email)
        }
    }

    @Test fun misspeltFreeMailDomainIsCorrectedAndFlagged() {
        val p = parseCard("Ravi Kumar\nManager\nravi.kumar@grmail.com")
        assertEquals("ravi.kumar@gmail.com", p.email)
        assertTrue(p.flags.containsKey("email"))
        assertEquals("ravi@gmial.com".let { parseCard("Ravi Kumar\nManager\n$it").email }, "ravi@gmail.com")
    }

    @Test fun realCompanyDomainIsNeverRewritten() {
        assertEquals("a.b@gmailing.com", parseCard("Ann Bee\nManager\na.b@gmailing.com").email)
        assertEquals("sales@sendka.com", parseCard("sales@sendka.com").email)
    }

    @Test fun helplineIsNeverUsedAsThePhone() {
        val p = parseCard("Asha Nair\nManager\nCustomer Service 1800 209 1345")
        assertEquals("", p.phone)
        assertTrue(p.flags["phone"]!!.contains("helpline"))
    }

    @Test fun mobileBeatsLandlineAndTollFree() {
        val p = parseCard("Tel: 0422 2345678\nMob: 98765 43210\n1800 123 4567\nKiran Das\nOwner")
        assertEquals("98765 43210", p.phone)
    }

    @Test fun textHeightBreaksATieBetweenNameCandidates() {
        val p = parseLines(listOf(OcrLine("Quality Products", 20), OcrLine("Sunil Rao", 60), OcrLine("Pune, Maharashtra", 18)))
        assertEquals("Sunil Rao", p.name)
    }

    @Test fun blankFieldsAreFlaggedNotGuessed() {
        val p = parseCard("")
        assertEquals("", p.name); assertEquals("", p.phone); assertEquals("", p.email)
        assertTrue(p.flags.keys.containsAll(listOf("name", "phone", "email")))
    }

    @Test fun cleanCardHasNoNameOrContactFlags() {
        val p = parseCard("Priya Rao\nSales Manager\nOrbit Group\npriya@orbit.example\n+91 90000 11111")
        assertTrue(p.flags.isEmpty())
    }

    @Test fun freeMailDomainIsNotUsedAsCompany() {
        val p = parseCard("Rahul Verma\nProprietor\nShree Balaji\nrahulverma@gmail.com\n+91 98765 12345")
        assertEquals("", p.company)
        assertEquals("Proprietor", p.title)
        assertEquals("Rahul Verma", p.name)
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
