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

    // ── Bogotá — esquema DATE_PARITY (par/impar calendario), motos exentas ──────

    private val bogotaRules = PicoYPlacaEngine.BOGOTA_2026

    @Test
    fun `bogota dia impar carro con placa terminada en 7 en horario esta restringido ahora`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, ODD_WEEKDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_AHORA, result.status)
        assertEquals(21, result.endHour)
    }

    @Test
    fun `bogota dia impar carro con placa terminada en 7 fuera de horario a las 10pm`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, ODD_WEEKDAY_22H_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO, result.status)
        assertEquals(21, result.endHour)
    }

    @Test
    fun `bogota dia par carro con placa terminada en 7 esta sin restriccion`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, EVEN_WEEKDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `bogota dia impar placa terminada en 7 justo a las 6am inicia restringida`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, ODD_WEEKDAY_6AM_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_AHORA, result.status)
    }

    @Test
    fun `bogota dia impar placa terminada en 7 justo a las 9pm ya no esta restringida`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, ODD_WEEKDAY_21H_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO, result.status)
    }

    @Test
    fun `bogota moto esta exenta sin importar el digito ni el dia`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = true, bogotaRules, ODD_WEEKDAY_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `bogota fin de semana esta sin restriccion aunque el digito rote`() {
        val result = PicoYPlacaEngine.check("ABC127", isMotorcycle = false, bogotaRules, WEEKEND_10AM_MS)
        assertEquals(PicoYPlacaEngine.Status.SIN_RESTRICCION, result.status)
    }

    @Test
    fun `parseRulesJson reconstruye reglas de bogota con date parity y motos exentas`() {
        val json = """
            {
              "cityId": "bogota",
              "displayName": "Bogotá",
              "startHour": 6,
              "endHour": 21,
              "carDigit": "LAST",
              "motoDigit": "LAST",
              "validFromMs": ${bogotaRules.validFromMs},
              "validUntilMs": ${bogotaRules.validUntilMs},
              "scheme": "DATE_PARITY",
              "dateParityRestricted": {"ODD_DAY": [6,7,8,9,0], "EVEN_DAY": [1,2,3,4,5]},
              "motosExentas": true
            }
        """.trimIndent()

        val parsed = PicoYPlacaEngine.parseRulesJson(json)

        assertEquals(bogotaRules, parsed)
    }

    // ── Zona horaria por ciudad (reglas generadas por IA fuera de Colombia) ─────

    private val cdmxRules = rules.copy(
        cityId = "cdmx",
        displayName = "Ciudad de México",
        rotation = mapOf(2 to listOf(5, 6)),
        timeZoneId = "America/Mexico_City",
    )

    @Test
    fun `check evalua la hora en la zona horaria de las reglas`() {
        // 20:00 Bogotá = 19:00 CDMX (UTC-6): fuera de horario en Bogotá, restringido aún en CDMX
        val result = PicoYPlacaEngine.check("ABC125", isMotorcycle = false, cdmxRules, MONDAY_20H_BOGOTA_MS)
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_AHORA, result.status)
    }

    @Test
    fun `check con zona horaria explicita sigue teniendo prioridad`() {
        val result = PicoYPlacaEngine.check(
            "ABC125", isMotorcycle = false, cdmxRules, MONDAY_20H_BOGOTA_MS, timeZoneId = "America/Bogota",
        )
        assertEquals(PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO, result.status)
    }

    @Test
    fun `parseRulesJson lee timeZoneId opcional con default bogota`() {
        val conTz = PicoYPlacaEngine.parseRulesJson(
            """{"cityId":"cdmx","displayName":"CDMX","rotation":{"2":[5,6]},"startHour":5,"endHour":20,
                "carDigit":"LAST","motoDigit":"FIRST","validFromMs":0,"validUntilMs":9999999999999,
                "timeZoneId":"America/Mexico_City"}""",
        )
        assertEquals("America/Mexico_City", conTz?.timeZoneId)

        val sinTz = PicoYPlacaEngine.parseRulesJson(
            """{"cityId":"medellin","displayName":"Medellín","rotation":{"2":[1,7]},"startHour":5,"endHour":20,
                "carDigit":"LAST","motoDigit":"FIRST","validFromMs":0,"validUntilMs":9999999999999}""",
        )
        assertEquals("America/Bogota", sinTz?.timeZoneId)
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

        // 2026-08-05 10:00:00 America/Bogota — miércoles, día 5 (impar)
        const val ODD_WEEKDAY_10AM_MS = 1_785_942_000_000L

        // 2026-08-05 22:00:00 America/Bogota — mismo día impar, fuera de horario (termina 21h)
        const val ODD_WEEKDAY_22H_MS = 1_785_985_200_000L

        // 2026-08-04 10:00:00 America/Bogota — martes, día 4 (par)
        const val EVEN_WEEKDAY_10AM_MS = 1_785_855_600_000L

        // 2026-08-01 10:00:00 America/Bogota — sábado, día 1 (impar mas fin de semana)
        const val WEEKEND_10AM_MS = 1_785_596_400_000L

        // 2026-08-05 06:00:00 America/Bogota — mismo día impar, límite inferior del horario (inclusive)
        const val ODD_WEEKDAY_6AM_MS = 1_785_927_600_000L

        // 2026-08-05 21:00:00 America/Bogota — mismo día impar, límite superior del horario (exclusive)
        const val ODD_WEEKDAY_21H_MS = 1_785_981_600_000L

        // 2026-06-01 20:00:00 America/Bogota (UTC-5) = 19:00 America/Mexico_City (UTC-6) — lunes
        const val MONDAY_20H_BOGOTA_MS = 1_780_362_000_000L
    }
}
