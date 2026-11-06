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
}
