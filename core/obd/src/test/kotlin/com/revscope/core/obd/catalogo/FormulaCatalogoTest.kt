package com.revscope.core.obd.catalogo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormulaCatalogoTest {
    @Test
    fun `evalua formula con varios bytes`() {
        assertEquals(5.13, FormulaCatalogo.evaluar("(F*256+G)/100", listOf(0, 0, 0, 0, 0, 2, 1))!!, 0.0001)
    }

    @Test
    fun `devuelve null si la formula usa un byte ausente`() {
        assertNull(FormulaCatalogo.evaluar("J-40", listOf(1, 2)))
    }

    @Test
    fun `devuelve null para formula invalida o no finita`() {
        assertNull(FormulaCatalogo.evaluar("esto no es una formula", List(26) { 0 }))
        assertNull(FormulaCatalogo.evaluar("1/0", List(26) { 0 }))
    }
}
