package com.revscope.core.obd.session

import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.obd.trip.FuelCostCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure coverage for [SessionAggregator.gpsFallbackMotion] — the GPS-only trip fallback
 * used by [SessionAggregator.close] ONLY when a session has no "0D" OBD telemetry.
 */
class SessionAggregatorTest {

    private fun gpsPoint(timestampMs: Long, lat: Double, lon: Double, speedKmh: Float) = GpsPointEntity(
        sessionId = 1L,
        timestamp = timestampMs,
        latitude = lat,
        longitude = lon,
        speedKmh = speedKmh,
    )

    @Test
    fun `empty track yields zero speed and distance`() {
        val motion = SessionAggregator.gpsFallbackMotion(emptyList())
        assertEquals(0f, motion.maxSpeed, 0.0001f)
        assertEquals(0f, motion.distanceKm, 0.0001f)
    }

    @Test
    fun `single point yields its speed and zero distance`() {
        val motion = SessionAggregator.gpsFallbackMotion(listOf(gpsPoint(0, 6.2442, -75.5812, 12f)))
        assertEquals(12f, motion.maxSpeed, 0.0001f)
        assertEquals(0f, motion.distanceKm, 0.0001f)
    }

    @Test
    fun `max speed is the highest recorded GPS speed`() {
        val points = listOf(
            gpsPoint(0, 6.2442, -75.5812, 20f),
            gpsPoint(1_000, 6.2452, -75.5812, 65f),
            gpsPoint(2_000, 6.2462, -75.5812, 40f),
        )
        val motion = SessionAggregator.gpsFallbackMotion(points)
        assertEquals(65f, motion.maxSpeed, 0.0001f)
    }

    @Test
    fun `distance sums haversine segments across the track`() {
        // Four points ~111 m apart going north (0.001° latitude ≈ 111 m) —
        // same reference track as TripStatsCalculatorTest's gpsDistanceKm coverage.
        val points = (0..3).map { i -> gpsPoint(i * 1_000L, 6.2442 + i * 0.001, -75.5812, 40f) }
        val motion = SessionAggregator.gpsFallbackMotion(points)
        assertEquals(0.333, motion.distanceKm.toDouble(), 0.01)
    }

    @Test
    fun `without a fuel price the trip keeps liters but has no cost`() {
        val fuel = SessionAggregator.tripFuel(FuelCostCalculator.FuelResult(2.0, 0.0, false), pricePerGallon = null)
        assertEquals(2.0, fuel.liters, 1e-9)
        assertNull(fuel.cost)
    }

    @Test
    fun `with a fuel price the trip keeps its cost`() {
        val fuel = SessionAggregator.tripFuel(FuelCostCalculator.FuelResult(2.0, 8_453.0, false), 16_000.0)
        assertEquals(8_453.0, fuel.cost!!, 1e-9)
    }

    @Test
    fun `stationary track accumulates zero distance`() {
        val points = (0..5).map { i -> gpsPoint(i * 1_000L, 6.2442, -75.5812, 0f) }
        val motion = SessionAggregator.gpsFallbackMotion(points)
        assertEquals(0f, motion.maxSpeed, 0.0001f)
        assertEquals(0f, motion.distanceKm, 0.0001f)
    }
}
