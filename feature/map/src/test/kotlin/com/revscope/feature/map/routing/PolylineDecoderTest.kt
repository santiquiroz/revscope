package com.revscope.feature.map.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decodes the canonical Google example`() {
        // Ejemplo oficial de la spec: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].lat, 1e-5)
        assertEquals(-120.2, points[0].lon, 1e-5)
        assertEquals(40.7, points[1].lat, 1e-5)
        assertEquals(-120.95, points[1].lon, 1e-5)
        assertEquals(43.252, points[2].lat, 1e-5)
        assertEquals(-126.453, points[2].lon, 1e-5)
    }

    @Test
    fun `empty string decodes to no points`() {
        assertTrue(PolylineDecoder.decode("").isEmpty())
    }

    @Test
    fun `single point round trip`() {
        val points = PolylineDecoder.decode("_p~iF~ps|U")
        assertEquals(1, points.size)
        assertEquals(38.5, points[0].lat, 1e-5)
        assertEquals(-120.2, points[0].lon, 1e-5)
    }
}
