package com.revscope.core.obd.taller.dtc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodificadorDtcTest {

    private fun decodificar(codigo: String) = checkNotNull(DecodificadorDtc.decodificar(codigo)) { codigo }

    @Test
    fun `P0xxx genérico da el subsistema por el tercer carácter`() {
        val esperados = mapOf(
            "P0101" to "Medición de aire y combustible",
            "P0261" to "Medición de aire y combustible (circuito de inyectores)",
            "P0325" to "Sistema de encendido o fallas de encendido",
            "P0442" to "Controles auxiliares de emisiones",
            "P0520" to "Velocidad del vehículo, control del ralentí y entradas auxiliares",
            "P0620" to "Computadora (ECU) y circuitos de salida",
            "P0700" to "Transmisión",
            "P0850" to "Transmisión",
            "P0A80" to "Propulsión híbrida",
        )

        esperados.forEach { (codigo, subsistema) ->
            val e = decodificar(codigo)
            assertEquals(codigo, SistemaDtc.TREN_MOTRIZ, e.sistema)
            assertEquals(codigo, AmbitoDtc.GENERICO, e.ambito)
            assertEquals(codigo, subsistema, e.subsistema)
        }
    }

    @Test
    fun `la explicación de un genérico nombra el sistema y el subsistema sin describir el código`() {
        val e = decodificar("P0261")

        assertEquals(
            "Código genérico definido por SAE · tren motriz (motor y transmisión) · " +
                "subsistema: medición de aire y combustible (circuito de inyectores)",
            e.explicacion,
        )
        assertEquals("subsistema: computadora (ECU) y circuitos de salida", decodificar("P0620").explicacion.substringAfterLast(" · "))
        assertTrue(DecodificadorDtc.mensajeSinGuia(e).contains("SAE J2012"))
    }

    @Test
    fun `P1xxx es del fabricante y no inventa descripción ni subsistema`() {
        listOf("P1122", "P1500", "P1A00").forEach { codigo ->
            val e = decodificar(codigo)
            assertEquals(codigo, AmbitoDtc.FABRICANTE, e.ambito)
            assertNull(codigo, e.subsistema)
            assertEquals("Código del fabricante: consulta el manual del modelo", e.explicacion)
            assertTrue(DecodificadorDtc.mensajeSinGuia(e).contains("no inventa"))
        }
    }

    @Test
    fun `P2xxx es genérico, P30-P33 del fabricante y P34-P39 genérico`() {
        assertEquals(AmbitoDtc.GENERICO, decodificar("P2135").ambito)
        assertEquals("Medición de aire y combustible", decodificar("P2135").subsistema)
        assertEquals(AmbitoDtc.FABRICANTE, decodificar("P3000").ambito)
        assertEquals(AmbitoDtc.FABRICANTE, decodificar("P33FF").ambito)
        assertEquals(AmbitoDtc.GENERICO, decodificar("P3400").ambito)
        assertNull(decodificar("P3400").subsistema)
    }

    @Test
    fun `C, B y U dan el sistema y separan genérico de fabricante sin subsistema`() {
        val casos = listOf(
            Triple("C0035", SistemaDtc.CHASIS, AmbitoDtc.GENERICO),
            Triple("C1234", SistemaDtc.CHASIS, AmbitoDtc.FABRICANTE),
            Triple("B0001", SistemaDtc.CARROCERIA, AmbitoDtc.GENERICO),
            Triple("B2AAA", SistemaDtc.CARROCERIA, AmbitoDtc.FABRICANTE),
            Triple("U0100", SistemaDtc.RED, AmbitoDtc.GENERICO),
            Triple("U1000", SistemaDtc.RED, AmbitoDtc.FABRICANTE),
            Triple("U3000", SistemaDtc.RED, AmbitoDtc.GENERICO),
        )

        casos.forEach { (codigo, sistema, ambito) ->
            val e = decodificar(codigo)
            assertEquals(codigo, sistema, e.sistema)
            assertEquals(codigo, ambito, e.ambito)
            assertNull(codigo, e.subsistema)
        }
        assertEquals("Código genérico definido por SAE · red de comunicación entre módulos", decodificar("U0100").explicacion)
    }

    @Test
    fun `normaliza mayúsculas y espacios y rechaza formatos inválidos`() {
        assertEquals("P0122", DecodificadorDtc.normalizar("  p0122 "))
        listOf("", "P012", "P01222", "X0122", "P4122", "P01G2", "P 0122").forEach { texto ->
            assertNull(texto, DecodificadorDtc.normalizar(texto))
            assertNull(texto, DecodificadorDtc.decodificar(texto))
        }
    }

    @Test
    fun `P0D a P0F genéricos no reciben un subsistema supuesto`() {
        val e = decodificar("P0D00")

        assertEquals(AmbitoDtc.GENERICO, e.ambito)
        assertNull(e.subsistema)
        assertFalse(e.explicacion.contains("subsistema"))
    }
}
