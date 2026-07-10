package com.revscope.core.obd.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInfoAlertPolicyTest {

    @Test
    fun `municipio nuevo sin anuncio previo se anuncia`() {
        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            lastAnnouncement = null,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `mismo municipio ya anunciado hoy no se repite`() {
        val yaAnunciado = LocalInfoAlertPolicy.LastAnnouncement("Guarne", CityAlertPolicy.dayKey(NOW_MS))

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            lastAnnouncement = yaAnunciado,
            nowMs = NOW_MS,
        )

        assertFalse(result)
    }

    @Test
    fun `mismo municipio anunciado un dia distinto se vuelve a anunciar`() {
        val anunciadoAyer = LocalInfoAlertPolicy.LastAnnouncement("Guarne", CityAlertPolicy.dayKey(NOW_MS - ONE_DAY_MS))

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            lastAnnouncement = anunciadoAyer,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `otro municipio anunciado hoy no bloquea el anuncio de uno distinto`() {
        val otroMunicipioHoy = LocalInfoAlertPolicy.LastAnnouncement("Rionegro", CityAlertPolicy.dayKey(NOW_MS))

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            lastAnnouncement = otroMunicipioHoy,
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
