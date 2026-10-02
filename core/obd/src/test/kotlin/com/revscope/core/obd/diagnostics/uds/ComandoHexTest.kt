package com.revscope.core.obd.diagnostics.uds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComandoHexTest {
    @Test
    fun normalizaComandoHexadecimal() {
        val comando = ComandoHex.validar(" 22f190 ").getOrThrow()

        assertEquals("22 F1 90", comando.texto)
        assertEquals(listOf(0x22, 0xF1, 0x90), comando.bytes)
        assertEquals(ClaseComando.LECTURA, comando.clase)
    }

    @Test
    fun devuelveMensajesDeValidacionEnEspanol() {
        assertEquals("comando vacío", mensajeError(""))
        assertEquals("solo dígitos hexadecimales", mensajeError("12G0"))
        assertEquals("número impar de dígitos hex", mensajeError("123"))
        assertEquals(
            "máximo 7 bytes: el ELM327 solo envía tramas simples",
            mensajeError("0102030405060708"),
        )
    }

    @Test
    fun normalizaComandosAtYPermiteSoloConsultasSeguras() {
        val casosLectura = mapOf("at rv" to "AT RV", "ATDPN" to "AT DPN", "AT I" to "AT I")
        casosLectura.forEach { (entrada, esperado) ->
            val comando = ComandoHex.validar(entrada).getOrThrow()
            assertEquals(esperado, comando.texto)
            assertTrue(comando.bytes.isEmpty())
            assertEquals(ClaseComando.LECTURA, comando.clase)
        }
        assertEquals(ClaseComando.BLOQUEADO, ComandoHex.validar("AT Z").getOrThrow().clase)
    }

    @Test
    fun clasificaServiciosDeLecturaYEscritura() {
        val lecturas = listOf(0x01, 0x02, 0x03, 0x06, 0x07, 0x09, 0x0A, 0x19, 0x1A, 0x21, 0x22, 0x23, 0x24, 0x3E)
        lecturas.forEach { assertEquals(ClaseComando.LECTURA, ComandoHex.clasificar(listOf(it))) }

        val escrituras = listOf(0x04, 0x08, 0x11, 0x14, 0x27, 0x28, 0x2E, 0x2F, 0x31, 0x85)
        escrituras.forEach { assertEquals(ClaseComando.ESCRITURA, ComandoHex.clasificar(listOf(it))) }
        assertEquals(ClaseComando.ESCRITURA, ComandoHex.clasificar(emptyList()))
    }

    @Test
    fun clasificaSubfuncionesDeSesionYOperacionesBloqueadas() {
        assertEquals(ClaseComando.LECTURA, ComandoHex.clasificar(listOf(0x10, 0x01)))
        assertEquals(ClaseComando.BLOQUEADO, ComandoHex.clasificar(listOf(0x10, 0x02)))
        assertEquals(ClaseComando.LECTURA, ComandoHex.clasificar(listOf(0x10, 0x03)))
        assertEquals(ClaseComando.ESCRITURA, ComandoHex.clasificar(listOf(0x10)))
        assertEquals(ClaseComando.ESCRITURA, ComandoHex.clasificar(listOf(0x10, 0x04)))
        listOf(0x34, 0x35, 0x36, 0x37, 0x38, 0x3D).forEach {
            assertEquals(ClaseComando.BLOQUEADO, ComandoHex.clasificar(listOf(it)))
        }
    }

    private fun mensajeError(entrada: String): String {
        val resultado = ComandoHex.validar(entrada)
        assertFalse(resultado.isSuccess)
        return resultado.exceptionOrNull()?.message.orEmpty()
    }
}
