package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeometryTest {

    @Test
    fun `sin puntos no hay bounds`() {
        assertNull(boundsOf(emptyList()))
    }

    @Test
    fun `bounds de un punto es degenerado pero valido`() {
        val b = boundsOf(listOf(6.2 to -75.6))!!
        assertEquals(6.2, b[0], 1e-9)
        assertEquals(-75.6, b[1], 1e-9)
        assertEquals(6.2, b[2], 1e-9)
        assertEquals(-75.6, b[3], 1e-9)
    }

    @Test
    fun `bounds cubre todos los puntos`() {
        val b = boundsOf(listOf(6.2 to -75.6, 6.3 to -75.4, 6.1 to -75.7))!!
        assertEquals(6.1, b[0], 1e-9)
        assertEquals(-75.7, b[1], 1e-9)
        assertEquals(6.3, b[2], 1e-9)
        assertEquals(-75.4, b[3], 1e-9)
    }

    @Test
    fun `el circulo se cierra y tiene los vertices pedidos`() {
        val poly = geodesicCircle(6.2442, -75.5812, 250.0)
        val ring = poly.coordinates()[0]
        assertTrue(ring.size >= 60)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 1e-9)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 1e-9)
    }
}
