package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.TelemetryPointEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class TripStatsCalculatorTest {

    private fun speedPoint(timestampMs: Long, kmh: Float) = TelemetryPointEntity(
        sessionId = 1L,
        timestamp = timestampMs,
        pid = "0D",
        value = kmh,
    )

    @Test
    fun `returns zero for fewer than two points`() {
        assertEquals(0.0, TripStatsCalculator.distanceKm(emptyList()), 0.0001)
        assertEquals(0.0, TripStatsCalculator.distanceKm(listOf(speedPoint(0, 60f))), 0.0001)
    }

    @Test
    fun `constant 60 kmh for one minute is one kilometre`() {
        val points = (0..60).map { second -> speedPoint(second * 1000L, 60f) }
        assertEquals(1.0, TripStatsCalculator.distanceKm(points), 0.001)
    }

    @Test
    fun `linear acceleration integrates as trapezoid`() {
        // 0 → 60 km/h over 60 s: average 30 km/h → 0.5 km
        val points = (0..60).map { s -> speedPoint(s * 1000L, s.toFloat()) }
        assertEquals(0.5, TripStatsCalculator.distanceKm(points), 0.001)
    }

    @Test
    fun `gaps longer than five seconds do not add phantom distance`() {
        val points = listOf(
            speedPoint(0, 60f),
            speedPoint(1_000, 60f),
            speedPoint(61_000, 60f), // 60 s link loss at 60 km/h would be a phantom km
            speedPoint(62_000, 60f),
        )
        // Only the two 1-second intervals count: 2 × (60 km/h × 1/3600 h)
        assertEquals(2 * 60.0 / 3600.0, TripStatsCalculator.distanceKm(points), 0.0001)
    }

    @Test
    fun `stationary trip accumulates nothing`() {
        val points = (0..30).map { s -> speedPoint(s * 1000L, 0f) }
        assertEquals(0.0, TripStatsCalculator.distanceKm(points), 0.0001)
    }
}
