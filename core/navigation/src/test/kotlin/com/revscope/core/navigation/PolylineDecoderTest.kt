package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decodes the canonical Google example`() {
        // Ejemplo oficial de la spec: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", precision = 5)

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
        assertTrue(PolylineDecoder.decode("", precision = 5).isEmpty())
    }

    @Test
    fun `single point round trip`() {
        val points = PolylineDecoder.decode("_p~iF~ps|U", precision = 5)
        assertEquals(1, points.size)
        assertEquals(38.5, points[0].lat, 1e-5)
        assertEquals(-120.2, points[0].lon, 1e-5)
    }

    @Test
    fun `decodificar con la precision equivocada desplaza el punto por diez`() {
        // Esta es la trampa que documenta el spec: no falla, solo manda la ruta a otro lado.
        val correcto = PolylineDecoder.decode("_p~iF~ps|U", precision = 5).first()
        val equivocado = PolylineDecoder.decode("_p~iF~ps|U", precision = 6).first()

        assertEquals(38.5, correcto.lat, 1e-5)
        assertEquals(3.85, equivocado.lat, 1e-5)
    }

    @Test
    fun `precision seis decodifica polyline6`() {
        // Mismo punto (38.5, -120.2) codificado con precisión 6.
        val points = PolylineDecoder.decode("_izlhA~rlgdF", precision = 6)
        assertEquals(1, points.size)
        assertEquals(38.5, points[0].lat, 1e-5)
        assertEquals(-120.2, points[0].lon, 1e-5)
    }
}
