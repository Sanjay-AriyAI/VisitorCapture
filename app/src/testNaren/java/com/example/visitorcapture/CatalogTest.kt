package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    private fun retrofit(machine: String) = Catalog.machinesFor("Retrofits").first { it.name == machine }

    @Test fun retrofitKitsAreListedPerMake() {
        val carding = Catalog.paths("Retrofits", retrofit("Carding"))
        assertTrue("Retrofits > Carding > Trutzschler > Pressure transducer" in carding)
        assertTrue("Retrofits > Carding > LMW > Pressure transducer" !in carding)
    }

    @Test fun makeWithoutKitsIsStillSelectable() {
        assertEquals(listOf("Retrofits > OHTC > ELGI > PLC conversion", "Retrofits > OHTC > SIMTA > PLC Conversion"), Catalog.paths("Retrofits", retrofit("OHTC")))
    }

    @Test fun retrofitOthersIsFreeTextOnly() {
        val comber = retrofit("Comber")
        assertEquals(listOf("LMW", "Rieter", "Others"), comber.sections.map { it.title })
        assertTrue(comber.sections.last().freeText)
        assertTrue(Catalog.paths("Retrofits", comber).none { it.contains("Others") })
        assertEquals("Retrofits > Comber > Others > ", Catalog.freeTextPrefix("Retrofits", comber, comber.sections.last()))
        assertEquals(listOf("Retrofits > Lap Former > LMW > HMI conversion", "Retrofits > Lap Former > Rieter > HMI conversion"),
            Catalog.paths("Retrofits", retrofit("Lap Former")))
        assertEquals(listOf("LMW", "Others"), retrofit("Spinning").sections.map { it.title })
        assertTrue(retrofit("Winding - Savio").sections.last().freeText)
    }

    @Test fun titanIsInOpenEndForBothSpares() {
        fun openEnd(p: String) = Catalog.machinesFor(p).first { it.name == "Open-End" }.sections.single().items
        assertEquals(listOf("Rieter", "Saurer", "Titan", "Others"), openEnd("Mechanical Spares"))
        assertEquals(listOf("Rieter", "Saurer", "Titan", "Others"), openEnd("Electronics Spares"))
    }

    @Test fun electronicClearerFollowsWindingInElectronicsOnly() {
        val electronics = Catalog.machinesFor("Electronics Spares")
        val eyc = electronics[electronics.indexOfFirst { it.name == "Winding" } + 1]
        assertEquals("Electronic Clearer (EYC)", eyc.name)
        assertEquals(listOf("Uster", "Loepfe", "Premier"), eyc.sections.single().items)
        assertTrue(Catalog.machinesFor("Mechanical Spares").none { it.name == eyc.name })
    }

    @Test fun eachDefaultContactIsItsOwnLinkInTheMail() {
        val h = ThankYou.companyHtml("Naren Group", Brand.companyDefaults)
        listOf("tel:+919842991141\"", "tel:+919965516076\"", "mailto:export@narenonline.com\"",
            "mailto:customerssupport@narenonline.com\"", "href=\"https://narengroup.in\"")
            .forEach { assertTrue("missing: $it", h.contains(it)) }
    }

    @Test fun otherProductsCardsAreTheirOwnPaths() {
        assertEquals(listOf("Other Products > Carding Belts", "Other Products > Ceramic Scissors",
            "Other Products > Direct Coupling", "Other Products > HMI Conversion"),
            Catalog.machinesFor("Other Products").flatMap { Catalog.paths("Other Products", it) })
    }

    @Test fun otherProductsSparePartsAreSummarisedWithoutEmptyBrackets() {
        assertEquals("Other Products: Carding Belts; HMI Conversion",
            Catalog.summarize(listOf("Other Products", "Other Products > Carding Belts", "Other Products > HMI Conversion")))
        assertEquals("Other Products", Catalog.summarize(listOf("Other Products")))
    }

    @Test fun summaryShowsMakeAndKit() {
        assertEquals("Retrofits: Winding - Savio (Orion Fixed yarn feeler conversion); Compressor (Digital pressure monitoring display unit (4 inch / 8 inch))",
            Catalog.summarize(listOf("Retrofits > Winding - Savio > Orion > Fixed yarn feeler conversion",
                "Retrofits > Compressor > Digital pressure monitoring display unit (4 inch / 8 inch)")))
    }
}
