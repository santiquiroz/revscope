package com.revscope.core.obd.connection

import org.junit.Assert.assertEquals
import org.junit.Test

class CortadorLineasMonitorTest {

    @Test
    fun `drena lineas separadas por retorno de carro`() {
        assertDrena("uno\rdos\r")
    }

    @Test
    fun `drena lineas separadas por salto de linea`() {
        assertDrena("uno\ndos\n")
    }

    @Test
    fun `drena lineas separadas por retorno y salto`() {
        assertDrena("uno\r\ndos\r\n")
    }

    @Test
    fun `conserva una linea parcial hasta recibir su separador`() {
        val pendiente = StringBuilder("completa\rparcial")
        val lineas = mutableListOf<String>()

        CortadorLineasMonitor.drenarLineas(pendiente, lineas::add)

        assertEquals(listOf("completa"), lineas)
        assertEquals("parcial", pendiente.toString())

        pendiente.append("\n")
        CortadorLineasMonitor.drenarLineas(pendiente, lineas::add)

        assertEquals(listOf("completa", "parcial"), lineas)
        assertEquals("", pendiente.toString())
    }

    @Test
    fun `entregar restos ignora lineas vacias`() {
        val lineas = mutableListOf<String>()

        CortadorLineasMonitor.entregarRestos("\runo\n\r dos\r\n", lineas::add)

        assertEquals(listOf("uno", " dos"), lineas)
    }

    private fun assertDrena(texto: String) {
        val lineas = mutableListOf<String>()
        val pendiente = StringBuilder(texto)

        CortadorLineasMonitor.drenarLineas(pendiente, lineas::add)

        assertEquals(listOf("uno", "dos"), lineas)
        assertEquals("", pendiente.toString())
    }
}
