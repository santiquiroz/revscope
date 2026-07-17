package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class DocumentStatusCalculatorTest {

    private val rules = PicoYPlacaEngine.MEDELLIN_2026_S1

    @Test
    fun `soat vencido hace 4 dias marca nivel vencido`() {
        val now = bogotaNoonMs(2026, 6, 5)
        val documents = baseDocuments(soatExpiresAt = utcMs(2026, 6, 1))

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        val soat = statuses.first { it.tipo == DocumentStatusCalculator.DocType.SOAT }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, soat.nivel)
        assertEquals("Venció hace 4 días", soat.detalle)
    }

    @Test
    fun `soat que vence hoy evaluado a las 9pm bogota no marca vencido`() {
        val documents = baseDocuments(soatExpiresAt = utcMs(2026, 6, 10))

        val statuses = DocumentStatusCalculator.calculate(documents, rules, BOGOTA_9PM_JUN10_MS)

        val soat = statuses.first { it.tipo == DocumentStatusCalculator.DocType.SOAT }
        assertEquals(DocumentStatusCalculator.Nivel.ATENCION, soat.nivel)
        assertEquals("Vence hoy", soat.detalle)
    }

    @Test
    fun `tecnomecanica que vence en 30 dias marca nivel atencion`() {
        val now = bogotaNoonMs(2026, 6, 1)
        val documents = baseDocuments(rtmExpiresAt = utcMs(2026, 7, 1))

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        val rtm = statuses.first { it.tipo == DocumentStatusCalculator.DocType.RTM }
        assertEquals(DocumentStatusCalculator.Nivel.ATENCION, rtm.nivel)
    }

    @Test
    fun `todo riesgo configurado con vigencia larga marca nivel ok`() {
        val now = utcMs(2026, 6, 1)
        val documents = baseDocuments(insuranceExpiresAt = utcMs(2027, 1, 1))

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        val todoRiesgo = statuses.first { it.tipo == DocumentStatusCalculator.DocType.TODO_RIESGO }
        assertEquals(DocumentStatusCalculator.Nivel.OK, todoRiesgo.nivel)
    }

    @Test
    fun `licencia sin configurar marca sin_configurar y no aparece en el banner`() {
        val now = utcMs(2026, 6, 1)
        val documents = baseDocuments()

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        val licencia = statuses.first { it.tipo == DocumentStatusCalculator.DocType.LICENCIA }
        assertEquals(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR, licencia.nivel)
        assertNull(DocumentStatusCalculator.bannerText(statuses))
    }

    @Test
    fun `pico y placa restringido ahora marca nivel vencido y arma el banner`() {
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)

        val statuses = DocumentStatusCalculator.calculate(documents, rules, FRIDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, picoYPlaca.nivel)
        assertEquals(20, picoYPlaca.horaLimite)
        assertEquals("Pico y placa hasta las 20:00", DocumentStatusCalculator.bannerText(statuses))
    }

    @Test
    fun `soat vencido y pico y placa restringido combinan el banner`() {
        val documents = baseDocuments(
            plate = "ABC122",
            picoPlacaCity = "medellin",
            isMotorcycle = false,
            soatExpiresAt = utcMs(2026, 6, 1),
        )

        val statuses = DocumentStatusCalculator.calculate(documents, rules, FRIDAY_10AM_MS)

        assertEquals("SOAT vencido · Pico y placa hasta las 20:00", DocumentStatusCalculator.bannerText(statuses))
    }

    @Test
    fun `sin nada vencido ni pico y placa activo no hay banner`() {
        val now = utcMs(2026, 6, 1)
        val documents = baseDocuments(
            plate = "ABC125",
            picoPlacaCity = "medellin",
            soatExpiresAt = utcMs(2027, 1, 1),
        )

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        assertNull(DocumentStatusCalculator.bannerText(statuses))
    }

    @Test
    fun `pico y placa en bogota dia impar restringido marca nivel vencido`() {
        val documents = baseDocuments(plate = "ABC127", picoPlacaCity = "bogota", isMotorcycle = false)

        val statuses = DocumentStatusCalculator.calculate(documents, null, ODD_WEEKDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, picoYPlaca.nivel)
        assertEquals(21, picoYPlaca.horaLimite)
    }

    @Test
    fun `moto en bogota no tiene restriccion de pico y placa`() {
        val documents = baseDocuments(plate = "ABC127", picoPlacaCity = "bogota", isMotorcycle = true)

        val statuses = DocumentStatusCalculator.calculate(documents, null, ODD_WEEKDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.OK, picoYPlaca.nivel)
        assertEquals("Motos exentas en Bogotá", picoYPlaca.detalle)
    }

    @Test
    fun `cali sin reglas configuradas marca sin_configurar con detalle de rotacion pendiente`() {
        val documents = baseDocuments(plate = "ABC127", picoPlacaCity = "cali", isMotorcycle = false)

        val statuses = DocumentStatusCalculator.calculate(documents, null, ODD_WEEKDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR, picoYPlaca.nivel)
        assertEquals("Configura la rotación vigente de Cali en Ajustes (JSON)", picoYPlaca.detalle)
    }

    @Test
    fun `override de otra ciudad no aplica a cali y sigue sin configurar`() {
        val documents = baseDocuments(plate = "ABC127", picoPlacaCity = "cali", isMotorcycle = false)
        val overrideParaMedellin = PicoYPlacaEngine.MEDELLIN_2026_S1

        val statuses = DocumentStatusCalculator.calculate(documents, overrideParaMedellin, FRIDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR, picoYPlaca.nivel)
        assertEquals("Configura la rotación vigente de Cali en Ajustes (JSON)", picoYPlaca.detalle)
    }

    @Test
    fun `override que coincide con la ciudad del perfil reemplaza las reglas del registro`() {
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)
        val overrideSinRestricciones = PicoYPlacaEngine.MEDELLIN_2026_S1.copy(rotation = emptyMap())

        val statuses = DocumentStatusCalculator.calculate(documents, overrideSinRestricciones, FRIDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.OK, picoYPlaca.nivel)
    }

    // ── Fallback IA cuando las reglas curadas faltan o están vencidas ──────────

    /** Rotación S2 ficticia generada por IA: vigente ago-dic 2026, miércoles restringe [2,8]. */
    private val medellinS2PorIa = PicoYPlacaEngine.MEDELLIN_2026_S1.copy(
        rotation = mapOf(2 to listOf(1, 2), 3 to listOf(3, 4), 4 to listOf(2, 8), 5 to listOf(5, 6), 6 to listOf(9, 0)),
        validFromMs = utcMs(2026, 8, 1),
        validUntilMs = utcMs(2027, 1, 1),
    )

    @Test
    fun `medellin con reglas vencidas usa el fallback IA vigente`() {
        // Ago 5 2026: MEDELLIN_2026_S1 venció jul 31; la rotación IA restringe el 2 los miércoles
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)

        val statuses = DocumentStatusCalculator.calculate(
            documents, null, ODD_WEEKDAY_10AM_MS, aiFallbackRules = medellinS2PorIa,
        )

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, picoYPlaca.nivel)
        assertEquals(20, picoYPlaca.horaLimite)
    }

    @Test
    fun `medellin con reglas vencidas sin fallback pide actualizar el semestre`() {
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)

        val statuses = DocumentStatusCalculator.calculate(documents, null, ODD_WEEKDAY_10AM_MS)

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR, picoYPlaca.nivel)
        assertEquals("Actualiza las reglas del semestre", picoYPlaca.detalle)
    }

    @Test
    fun `fallback IA tambien vencido no reemplaza el estado de reglas vencidas`() {
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)
        val iaVencida = medellinS2PorIa.copy(validFromMs = 0L, validUntilMs = 1L)

        val statuses = DocumentStatusCalculator.calculate(
            documents, null, ODD_WEEKDAY_10AM_MS, aiFallbackRules = iaVencida,
        )

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.SIN_CONFIGURAR, picoYPlaca.nivel)
        assertEquals("Actualiza las reglas del semestre", picoYPlaca.detalle)
    }

    @Test
    fun `curadas vigentes ganan sobre el fallback IA`() {
        // Jun 5 viernes: S1 vigente restringe [2,8]; la IA (sin restricción) NO debe aplicar
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "medellin", isMotorcycle = false)
        val iaSinRestriccion = medellinS2PorIa.copy(rotation = emptyMap(), validFromMs = 0L)

        val statuses = DocumentStatusCalculator.calculate(
            documents, null, FRIDAY_10AM_MS, aiFallbackRules = iaSinRestriccion,
        )

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, picoYPlaca.nivel)
    }

    @Test
    fun `cali sin reglas curadas usa el fallback IA`() {
        val documents = baseDocuments(plate = "ABC122", picoPlacaCity = "cali", isMotorcycle = false)
        val caliPorIa = medellinS2PorIa.copy(cityId = "cali", displayName = "Cali")

        val statuses = DocumentStatusCalculator.calculate(
            documents, null, ODD_WEEKDAY_10AM_MS, aiFallbackRules = caliPorIa,
        )

        val picoYPlaca = statuses.first { it.tipo == DocumentStatusCalculator.DocType.PICO_Y_PLACA }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, picoYPlaca.nivel)
    }

    @Test
    fun `needsAiFallback solo cuando faltan reglas curadas vigentes`() {
        assertEquals(false, DocumentStatusCalculator.needsAiFallback(null, null, FRIDAY_10AM_MS))
        assertEquals(false, DocumentStatusCalculator.needsAiFallback("medellin", null, FRIDAY_10AM_MS))
        assertEquals(true, DocumentStatusCalculator.needsAiFallback("medellin", null, ODD_WEEKDAY_10AM_MS))
        assertEquals(true, DocumentStatusCalculator.needsAiFallback("cali", null, FRIDAY_10AM_MS))
        assertEquals(false, DocumentStatusCalculator.needsAiFallback("bogota", null, ODD_WEEKDAY_10AM_MS))
    }

    /** Stored expiry dates are UTC-midnight of the picked calendar day. */
    private fun utcMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** [nowMs] is a real instant — model it as noon in the caller's zone, not UTC midnight. */
    private fun bogotaNoonMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atTime(12, 0).atZone(ZoneId.of("America/Bogota")).toInstant().toEpochMilli()

    private fun baseDocuments(
        plate: String? = null,
        picoPlacaCity: String? = null,
        isMotorcycle: Boolean = false,
        soatExpiresAt: Long? = null,
        rtmExpiresAt: Long? = null,
        insuranceExpiresAt: Long? = null,
        licenseExpiresAt: Long? = null,
    ) = DocumentStatusCalculator.VehicleDocuments(
        plate = plate,
        picoPlacaCity = picoPlacaCity,
        isMotorcycle = isMotorcycle,
        soatExpiresAt = soatExpiresAt,
        rtmExpiresAt = rtmExpiresAt,
        insuranceExpiresAt = insuranceExpiresAt,
        licenseExpiresAt = licenseExpiresAt,
    )

    private companion object {
        // 2026-06-05 10:00:00 America/Bogota (UTC-5) = 2026-06-05 15:00:00 UTC — viernes
        const val FRIDAY_10AM_MS = 1_780_671_600_000L

        // 2026-08-05 10:00:00 America/Bogota — miércoles, día 5 (impar), dentro de la vigencia Bogotá
        const val ODD_WEEKDAY_10AM_MS = 1_785_942_000_000L

        // 2026-06-10 21:00:00 America/Bogota (UTC-5) = 2026-06-11 02:00:00 UTC.
        // Regression for the timezone bug: the old UTC-only calculation would read the
        // Bogota date as 2026-06-11 (one day ahead), turning a document that expires
        // "today" (stored as UTC midnight of 2026-06-10) into VENCIDO.
        const val BOGOTA_9PM_JUN10_MS = 1_781_143_200_000L
    }
}
