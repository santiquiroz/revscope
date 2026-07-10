package com.revscope.core.obd.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityAlertPolicyTest {

    @Test
    fun `ciudad detectada distinta al perfil restringida ahora sin anuncio previo se anuncia`() {
        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `ciudad detectada igual a la del perfil no se anuncia`() {
        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "medellin",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertFalse(result)
    }

    @Test
    fun `sin restriccion no se anuncia`() {
        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.SIN_RESTRICCION,
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertFalse(result)
    }

    @Test
    fun `restringido hoy fuera de horario tambien se anuncia`() {
        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_HOY_FUERA_DE_HORARIO,
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `misma ciudad ya anunciada hoy no se repite`() {
        val yaAnunciada = CityAlertPolicy.LastAnnouncement("bogota", CityAlertPolicy.dayKey(NOW_MS))

        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = yaAnunciada,
            nowMs = NOW_MS,
        )

        assertFalse(result)
    }

    @Test
    fun `misma ciudad anunciada un dia distinto se vuelve a anunciar`() {
        val anunciadaAyer = CityAlertPolicy.LastAnnouncement("bogota", CityAlertPolicy.dayKey(NOW_MS - ONE_DAY_MS))

        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = anunciadaAyer,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `otra ciudad anunciada hoy no bloquea el anuncio de una ciudad distinta`() {
        val otraCiudadHoy = CityAlertPolicy.LastAnnouncement("cali", CityAlertPolicy.dayKey(NOW_MS))

        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = "medellin",
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = otraCiudadHoy,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `perfil sin ciudad configurada permite anunciar la ciudad detectada`() {
        val result = CityAlertPolicy.shouldAnnounce(
            detectedCityId = "bogota",
            profileCityId = null,
            status = PicoYPlacaEngine.Status.RESTRINGIDO_AHORA,
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    private companion object {
        // 2026-06-01 10:00:00 America/Bogota (UTC-5)
        const val NOW_MS = 1_780_326_000_000L
        const val ONE_DAY_MS = 86_400_000L
    }
}
