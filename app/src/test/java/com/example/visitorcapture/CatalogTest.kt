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
        assertEquals(listOf("Retrofits > OHTC > ELGI > PLC conversion", "Retrofits > OHTC > SIMTA",
            "Retrofits > OHTC > LUWA", "Retrofits > OHTC > Electro-Jet"), Catalog.paths("Retrofits", retrofit("OHTC")))
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
