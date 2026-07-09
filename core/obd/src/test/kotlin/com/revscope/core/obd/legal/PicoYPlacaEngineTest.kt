package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PicoYPlacaEngineTest {

    private val rules = PicoYPlacaEngine.MEDELLIN_2026_S1

    @Test
    fun `moto con primer digito 2 el lunes esta sin restriccion`() {
        val result = PicoYPlacaEngine.check("NZO28H", isMotorcycle = true, rules, MONDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `moto con primer digito 2 el viernes en horario esta restringida ahora`() {
        val result = PicoYPlacaEngine.check("NZO28H", isMotorcycle = true, rules, FRIDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_AHORA, result.status)
        assertEquals(20, result.endHour)
    }

    @Test
    fun `moto restringida el viernes fuera de horario despues de las 8pm`() {
        val result = PicoYPlacaEngine.check("NZO28H", isMotorcycle = true, rules, FRIDAY_2130_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO, result.status)
        assertEquals(20, result.endHour)
    }

    @Test
    fun `sabado no tiene restriccion sin importar el digito`() {
        val result = PicoYPlacaEngine.check("NZO28H", isMotorcycle = true, rules, SATURDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `fecha fuera de vigencia del semestre marca reglas vencidas`() {
        val result = PicoYPlacaEngine.check("NZO28H", isMotorcycle = true, rules, SEPT_1_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.REGLAS_VENCIDAS, result.status)
        assertNull(result.endHour)
    }

    @Test
    fun `carro con ultimo digito 3 el martes esta restringido ahora`() {
        val result = PicoYPlacaEngine.check("ABC123", isMotorcycle = false, rules, TUESDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_AHORA, result.status)
    }

    @Test
    fun `carro con ultimo digito que no rota el martes esta sin restriccion`() {
        // martes restringe [0,3]; una placa que termina en 5 no tiene restriccion ese dia
        val result = PicoYPlacaEngine.check("ABC125", isMotorcycle = false, rules, TUESDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `placa sin digitos numericos no tiene datos suficientes`() {
        val result = PicoYPlacaEngine.check("ABC", isMotorcycle = false, rules, FRIDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_DATOS, result.status)
    }

    @Test
    fun `parseRulesJson reconstruye las reglas desde json`() {
        val json = """
            {
              "cityId": "medellin",
              "displayName": "Medellín",
              "rotation": {"2": [1,7], "3": [0,3], "4": [4,6], "5": [5,9], "6": [2,8]},
              "startHour": 5,
              "endHour": 20,
              "carDigit": "LAST",
              "motoDigit": "FIRST",
              "validFromMs": 1770008400000,
              "validUntilMs": 1785560399000
            }
        """.trimIndent()

        val parsed = PicoYPlacaEngine.parseRulesJson(json)

        assertEquals(rules, parsed)
    }

    @Test
    fun `parseRulesJson invalido retorna null`() {
        assertNull(PicoYPlacaEngine.parseRulesJson("no es json"))
    }

    private companion object {
        // 2026-06-01 10:00:00 America/Bogota (UTC-5) = 2026-06-01 15:00:00 UTC — lunes
        const val MONDAY_10AM_MS = 1_780_326_000_000L

        // 2026-06-02 10:00:00 America/Bogota (UTC-5) = 2026-06-02 15:00:00 UTC — martes
        const val TUESDAY_10AM_MS = 1_780_412_400_000L

        // 2026-06-05 10:00:00 America/Bogota (UTC-5) = 2026-06-05 15:00:00 UTC — viernes
        const val FRIDAY_10AM_MS = 1_780_671_600_000L

        // 2026-06-05 21:30:00 America/Bogota (UTC-5) = 2026-06-06 02:30:00 UTC — viernes noche
        const val FRIDAY_2130_MS = 1_780_713_000_000L

        // 2026-06-06 10:00:00 America/Bogota (UTC-5) = 2026-06-06 15:00:00 UTC — sabado
        const val SATURDAY_10AM_MS = 1_780_758_000_000L

        // 2026-09-01 10:00:00 America/Bogota (UTC-5) = 2026-09-01 15:00:00 UTC — fuera del semestre
        const val SEPT_1_10AM_MS = 1_788_274_800_000L
    }
}
