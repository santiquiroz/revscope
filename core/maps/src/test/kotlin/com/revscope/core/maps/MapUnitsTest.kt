package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapUnitsTest {

    @Test
    fun `en pantalla 3x ocho pixeles fisicos son dos coma seis siete dp`() {
        assertEquals(2.667f, physicalPxToDp(8f, 3f), 0.001f)
    }

    @Test
    fun `en pantalla 1x el valor no cambia`() {
        assertEquals(8f, physicalPxToDp(8f, 1f), 0.001f)
    }

    @Test
    fun `densidad cero no divide por cero`() {
        assertEquals(8f, physicalPxToDp(8f, 0f), 0.001f)
    }

    @Test
    fun `el casing de dieciseis pixeles sigue siendo mas ancho que el segmento de doce`() {
        val casing = physicalPxToDp(16f, 3f)
        val segment = physicalPxToDp(12f, 3f)
        assertTrue(casing > segment)
    }
}
