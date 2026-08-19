package com.revscope.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SunTimesTest {

    // Medellín: lat 6.24, lon -75.58 (UTC-5). Sol sale ~06:00 local (11:00 UTC),
    // se pone ~18:10 local (23:10 UTC), estable todo el año por estar en el trópico.

    @Test
    fun `mediodia en Medellin es de dia`() {
        val noonLocal = Instant.parse("2026-08-19T17:00:00Z") // 12:00 UTC-5
        assertFalse(SunTimes.isNight(6.24, -75.58, noonLocal.toEpochMilli()))
    }

    @Test
    fun `once de la noche en Medellin es de noche`() {
        val nightLocal = Instant.parse("2026-08-20T04:00:00Z") // 23:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, nightLocal.toEpochMilli()))
    }

    @Test
    fun `tres de la manana es de noche`() {
        val predawn = Instant.parse("2026-08-19T08:00:00Z") // 03:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, predawn.toEpochMilli()))
    }

    @Test
    fun `ocho de la noche es de noche y nueve de la manana es de dia`() {
        val evening = Instant.parse("2026-08-20T01:00:00Z") // 20:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, evening.toEpochMilli()))
        val morning = Instant.parse("2026-08-19T14:00:00Z") // 09:00 UTC-5
        assertFalse(SunTimes.isNight(6.24, -75.58, morning.toEpochMilli()))
    }
}
