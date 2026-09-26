package com.revscope.core.obd.taller.dtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasosGuiaTest {

    @Test
    fun `la clave de un paso es el código y su número desde 1`() {
        assertEquals("P0122#1", PasosGuia.clave("P0122", 1))
    }

    @Test
    fun `un paso está marcado solo si su clave exacta está en la sesión`() {
        val marcados = setOf("P0122#2", "P0123#1")

        assertTrue(PasosGuia.marcado(marcados, "P0122", 2))
        assertFalse(PasosGuia.marcado(marcados, "P0122", 1))
        assertFalse(PasosGuia.marcado(marcados, "P0123", 2))
    }

    @Test
    fun `cuenta los pasos marcados de un código sin mezclar otros códigos`() {
        val marcados = setOf("P0122#2", "P0122#5", "P0123#1", "P0122#9")

        assertEquals(2, PasosGuia.contarMarcados(marcados, "P0122", totalPasos = 8))
    }
}
