package com.revscope.core.obd.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyStatusScheduleTest {

    private fun bogota(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneId.of(DailyStatusSchedule.ZONE_ID))
            .toInstant()
            .toEpochMilli()

    @Test
    fun `antes de las 5 30 apunta al mismo dia`() {
        val now = bogota(2026, 8, 11, 2, 0)
        assertEquals(bogota(2026, 8, 11, 5, 30), DailyStatusSchedule.nextTriggerAtMs(now))
    }

    @Test
    fun `despues de las 5 30 apunta al dia siguiente`() {
        val now = bogota(2026, 8, 11, 14, 45)
        assertEquals(bogota(2026, 8, 12, 5, 30), DailyStatusSchedule.nextTriggerAtMs(now))
    }

    @Test
    fun `exactamente a las 5 30 apunta al dia siguiente`() {
        val now = bogota(2026, 8, 11, 5, 30)
        assertEquals(bogota(2026, 8, 12, 5, 30), DailyStatusSchedule.nextTriggerAtMs(now))
    }

    @Test
    fun `el proximo disparo siempre esta en el futuro`() {
        val now = bogota(2026, 8, 11, 5, 29)
        assertTrue(DailyStatusSchedule.nextTriggerAtMs(now) > now)
    }

    @Test
    fun `cruce de fin de mes apunta al primero del mes siguiente`() {
        val now = bogota(2026, 8, 31, 23, 59)
        assertEquals(bogota(2026, 9, 1, 5, 30), DailyStatusSchedule.nextTriggerAtMs(now))
    }

    @Test
    fun `dos instantes del mismo dia comparten epochDay`() {
        val morning = bogota(2026, 8, 11, 5, 30)
        val evening = bogota(2026, 8, 11, 23, 30)
        assertEquals(
            DailyStatusSchedule.epochDayAt(morning),
            DailyStatusSchedule.epochDayAt(evening),
        )
    }

    @Test
    fun `sin aviso previo se notifica`() {
        assertTrue(DailyStatusSchedule.shouldNotify(null, bogota(2026, 8, 11, 5, 30)))
    }

    @Test
    fun `ya notificado hoy no vuelve a notificar`() {
        val now = bogota(2026, 8, 11, 5, 30)
        val today = DailyStatusSchedule.epochDayAt(now)
        assertFalse(DailyStatusSchedule.shouldNotify(today, bogota(2026, 8, 11, 18, 0)))
    }

    @Test
    fun `notificado ayer si vuelve a notificar hoy`() {
        val yesterday = DailyStatusSchedule.epochDayAt(bogota(2026, 8, 10, 5, 30))
        assertTrue(DailyStatusSchedule.shouldNotify(yesterday, bogota(2026, 8, 11, 5, 30)))
    }
}
