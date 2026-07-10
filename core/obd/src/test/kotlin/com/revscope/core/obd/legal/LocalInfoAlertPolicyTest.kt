package com.revscope.core.obd.legal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalInfoAlertPolicyTest {

    @Test
    fun `municipio nuevo sin anuncio previo se anuncia`() {
        val announcedToday = mutableMapOf<String, String>()

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            announcedToday = announcedToday,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `mismo municipio ya anunciado hoy no se repite`() {
        val announcedToday = mutableMapOf(
            "Guarne" to CityAlertPolicy.dayKey(NOW_MS)
        )

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            announcedToday = announcedToday,
            nowMs = NOW_MS,
        )

        assertFalse(result)
    }

    @Test
    fun `mismo municipio anunciado un dia distinto se vuelve a anunciar`() {
        val announcedToday = mutableMapOf(
            "Guarne" to CityAlertPolicy.dayKey(NOW_MS - ONE_DAY_MS)
        )

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            announcedToday = announcedToday,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `otro municipio anunciado hoy no bloquea el anuncio de uno distinto`() {
        val announcedToday = mutableMapOf(
            "Rionegro" to CityAlertPolicy.dayKey(NOW_MS)
        )

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            announcedToday = announcedToday,
            nowMs = NOW_MS,
        )

        assertTrue(result)
    }

    @Test
    fun `Guarne entonces Rionegro entonces Guarne mismo dia no re-anuncia Guarne`() {
        val announcedToday = mutableMapOf<String, String>()
        val dayKeyToday = CityAlertPolicy.dayKey(NOW_MS)

        // First visit to Guarne
        val firstGuarne = LocalInfoAlertPolicy.shouldAnnounce("Guarne", announcedToday, NOW_MS)
        assertTrue(firstGuarne)
        announcedToday["Guarne"] = dayKeyToday

        // Visit to Rionegro
        val rionegro = LocalInfoAlertPolicy.shouldAnnounce("Rionegro", announcedToday, NOW_MS)
        assertTrue(rionegro)
        announcedToday["Rionegro"] = dayKeyToday

        // Return to Guarne same day — should NOT re-announce
        val secondGuarne = LocalInfoAlertPolicy.shouldAnnounce("Guarne", announcedToday, NOW_MS)
        assertFalse(secondGuarne)
    }

    @Test
    fun `Guarne revisitado al dia siguiente se vuelve a anunciar`() {
        val announcedToday = mutableMapOf(
            "Guarne" to CityAlertPolicy.dayKey(NOW_MS)
        )
        val nextDayMs = NOW_MS + ONE_DAY_MS

        val result = LocalInfoAlertPolicy.shouldAnnounce(
            municipio = "Guarne",
            announcedToday = announcedToday,
            nowMs = nextDayMs,
        )

        assertTrue(result)
    }

    private companion object {
        // 2026-06-01 10:00:00 America/Bogota (UTC-5)
        const val NOW_MS = 1_780_326_000_000L
        const val ONE_DAY_MS = 86_400_000L
    }
}
