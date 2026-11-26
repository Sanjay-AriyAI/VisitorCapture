package com.example.visitorcapture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarketTest {
    @Test fun indiaIsDomesticByDefault() {
        assertEquals("Domestic Visitor", marketFromCountry("India", ""))
        assertEquals("Domestic Visitor", marketFromCountry(" INDIA. ", ""))
        assertEquals("Domestic Visitor", marketFromCountry("IN", "India"))
    }
    @Test fun otherCountriesAreInternational() {
        assertEquals("International Visitor", marketFromCountry("Bangladesh", ""))
        assertEquals("International Visitor", marketFromCountry("Indonesia", "India"))
    }
    @Test fun homeCountryFromSettings() {
        assertEquals("Domestic Visitor", marketFromCountry("Bangladesh", "Bangladesh"))
        assertEquals("International Visitor", marketFromCountry("India", "Bangladesh"))
    }
    @Test fun blankCountryLeavesItUnpicked() = assertNull(marketFromCountry("  ", ""))
}
