package com.revscope.core.common.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionTest {
    @Test
    fun `país CO GB US y otro`() {
        assertEquals(Region.CO, Region.fromCountry("CO"))
        assertEquals(Region.GB, Region.fromCountry("GB"))
        assertEquals(Region.US, Region.fromCountry("US"))
        assertEquals(Region.OTHER, Region.fromCountry("DE"))
    }

    @Test
    fun `la SIM manda sobre el locale`() {
        assertEquals(Region.CO, Region.fromSources(simCountry = "co", networkCountry = null, localeCountry = "US"))
    }

    @Test
    fun `sin SIM usa la red`() {
        assertEquals(Region.GB, Region.fromSources(simCountry = "", networkCountry = "gb", localeCountry = "US"))
    }

    @Test
    fun `sin SIM ni red usa el locale`() {
        assertEquals(Region.US, Region.fromSources(simCountry = " ", networkCountry = null, localeCountry = "US"))
    }

    @Test
    fun `país desconocido es OTHER`() {
        assertEquals(Region.OTHER, Region.fromSources(simCountry = "de", networkCountry = null, localeCountry = "US"))
    }
}
