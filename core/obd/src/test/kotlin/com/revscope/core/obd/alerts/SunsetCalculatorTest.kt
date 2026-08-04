package com.revscope.core.obd.alerts

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SunsetCalculatorTest {

    // 2026-08-04 15:00 UTC (10:00 en Medellín)
    private val AUG_4_2026_15_UTC = 1_785_855_600_000L

    @Test
    fun `atardecer en medellin cae cerca de las 6pm hora local`() {
        val sunset = SunsetCalculator.sunsetUtcMillis(AUG_4_2026_15_UTC, 6.2442, -75.5812)
        // Medellín (~6°N) atardece ~18:10 local todo el año = ~23:10 UTC (1_785_885_000_000).
        checkNotNull(sunset)
        assertTrue(
            "sunset=$sunset esperado ~23:10 UTC ±25 min",
            abs(sunset - 1_785_885_000_000L) < 25 * 60_000L,
        )
    }

    @Test
    fun `latitud polar en verano retorna null`() {
        // Tromsø (69.6°N) a inicios de julio: sol de medianoche, no hay atardecer
        val july1 = 1_782_950_400_000L
        assertNull(SunsetCalculator.sunsetUtcMillis(july1, 69.65, 18.96))
    }

    @Test
    fun `atardecer siempre despues del mediodia solar local`() {
        val sunset = SunsetCalculator.sunsetUtcMillis(AUG_4_2026_15_UTC, 6.2442, -75.5812)
        checkNotNull(sunset)
        // Mediodía solar Medellín ≈ 17:02 UTC
        assertTrue(sunset > AUG_4_2026_15_UTC + 2 * 3_600_000L)
    }
}
