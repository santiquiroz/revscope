package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DocumentStatusCalculatorTest {

    private val rules = PicoYPlacaEngine.MEDELLIN_2026_S1

    @Test
    fun `soat vencido hace 4 dias marca nivel vencido`() {
        val now = utcMs(2026, 6, 5)
        val documents = baseDocuments(soatExpiresAt = utcMs(2026, 6, 1))

        val statuses = DocumentStatusCalculator.calculate(documents, rules, now)

        val soat = statuses.first { it.tipo == DocumentStatusCalculator.DocType.SOAT }
        assertEquals(DocumentStatusCalculator.Nivel.VENCIDO, soat.nivel)
        assertEquals("Venció hace 4 días", soat.detalle)
    }

    @Test
    fun `tecnomecanica que vence en 30 dias marca nivel atencion`() {
        val now = utcMs(2026, 6, 1)
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

    private fun utcMs(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

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
    }
}
