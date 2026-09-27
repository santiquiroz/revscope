package com.revscope.core.obd.taller.pid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacidadesEcuTest {

    @Test
    fun `names every standard ELM protocol including automatic prefix`() {
        val expected = mapOf(
            "0" to "Automático (sin protocolo detectado)",
            "1" to "SAE J1850 PWM",
            "2" to "SAE J1850 VPW",
            "3" to "ISO 9141-2 (K-line)",
            "4" to "ISO 14230-4 (K-line, inicio a 5 baudios)",
            "5" to "ISO 14230-4 (K-line, inicio rápido)",
            "6" to "ISO 15765-4 CAN (11 bits, 500 kbit/s)",
            "7" to "ISO 15765-4 CAN (29 bits, 500 kbit/s)",
            "8" to "ISO 15765-4 CAN (11 bits, 250 kbit/s)",
            "9" to "ISO 15765-4 CAN (29 bits, 250 kbit/s)",
            "A" to "SAE J1939 CAN (29 bits, 250 kbit/s)",
            "B" to "USER1 CAN (11 bits, 125 kbit/s)",
            "C" to "USER2 CAN (11 bits, 50 kbit/s)",
        )

        expected.forEach { (dpn, name) ->
            assertEquals(name, CapacidadesEcu.desde(dpn, emptySet()).protocolo)
        }
        assertEquals(
            "ISO 15765-4 CAN (11 bits, 500 kbit/s)",
            CapacidadesEcu.desde("A6", emptySet()).protocolo,
        )
    }

    @Test
    fun `normalizes counts and groups announced PIDs by queried range`() {
        val result = CapacidadesEcu.desde(
            atDpn = "A5",
            pidsAnunciados = setOf("0c", "05", "20", "21", "40", "41", "60", "61"),
        )

        assertEquals(8, result.cantidadPids)
        assertEquals(listOf("05", "0C", "20"), result.pidsPorRango.getValue("01 00"))
        assertEquals(listOf("21", "40"), result.pidsPorRango.getValue("01 20"))
        assertEquals(listOf("41", "60"), result.pidsPorRango.getValue("01 40"))
        assertEquals(listOf("61"), result.pidsPorRango.getValue("01 60"))
    }

    @Test
    fun `K-line capability is honest about expected rate and few motorcycle PIDs`() {
        val result = CapacidadesEcu.desde("A5", setOf("05", "0C"))

        assertTrue(result.esKLine)
        assertEquals("Baja: una petición por vez; mídela en la captura", result.tasaEsperada)
        assertEquals(
            "Las ECUs de moto por K-line (p. ej. Delphi MT05) exponen pocos PIDs; " +
                "RevScope muestra solo los que la ECU anuncia.",
            result.explicacion,
        )
    }

    @Test
    fun `CAN capability does not claim the motorcycle limitation`() {
        val result = CapacidadesEcu.desde("6", setOf("0C"), tasaMedidaHz = 18.5)

        assertFalse(result.esKLine)
        assertEquals(18.5, result.tasaMedidaHz!!, 0.0)
        assertEquals("Alta; depende de la ECU y de los PIDs solicitados", result.tasaEsperada)
        assertEquals("RevScope muestra solo los PIDs que la ECU anuncia.", result.explicacion)
    }

    @Test
    fun `unknown ATDPN remains explicit`() {
        val result = CapacidadesEcu.desde("FF", emptySet())

        assertEquals("Protocolo no identificado", result.protocolo)
        assertEquals("No estimada; mídela en la captura", result.tasaEsperada)
    }
}
