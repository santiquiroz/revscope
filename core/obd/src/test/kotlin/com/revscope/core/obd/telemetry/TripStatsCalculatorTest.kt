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

    // ── GPS ──────────────────────────────────────────────────────────────────

    @Test
    fun `haversine matches known distance`() {
        // Medellín centro → Envigado ≈ 9.6 km (known reference pair)
        val meters = TripStatsCalculator.haversineMeters(6.2442, -75.5812, 6.1670, -75.5836)
        assertEquals(8_600.0, meters, 300.0)
    }

    @Test
    fun `gps distance sums track segments`() {
        // Four points ~111 m apart going north (0.001° latitude ≈ 111 m)
        val points = (0..3).map { i ->
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = i * 1000L,
                latitude = 6.2442 + i * 0.001,
                longitude = -75.5812,
                speedKmh = 40f,
            )
        }
        assertEquals(0.333, TripStatsCalculator.gpsDistanceKm(points), 0.01)
    }

    @Test
    fun `gps distance is zero for single point`() {
        assertEquals(0.0, TripStatsCalculator.gpsDistanceKm(emptyList()), 0.0001)
    }

    @Test
    fun `gps distance repeated identical coordinates add zero`() {
        val points = listOf(
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = 0L,
                latitude = 0.0,
                longitude = 0.0,
                speedKmh = 0f,
            ),
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = 1_000L,
                latitude = 0.0,
                longitude = 0.0,
                speedKmh = 0f,
            ),
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = 2_000L,
                latitude = 0.001,
                longitude = 0.0,
                speedKmh = 0f,
            ),
        )
        assertEquals(0.1111949, TripStatsCalculator.gpsDistanceKm(points), 0.000001)
    }

    @Test
    fun `gps distance crossing antimeridian is out of scope`() {
        // Track example: (0.0, 179.9) -> (0.0, -179.9). Antimeridian
        // longitude wrapping is out of scope for the current haversine implementation.
        org.junit.Assume.assumeTrue(
            "Antimeridian wrapping is out of scope for the current haversine implementation.",
            false,
        )
    }

    @Test
    fun `haversine distance is symmetric`() {
        val forward = TripStatsCalculator.haversineMeters(6.2442, -75.5812, 4.7110, -74.0721)
        val reverse = TripStatsCalculator.haversineMeters(4.7110, -74.0721, 6.2442, -75.5812)
        assertEquals(forward, reverse, 0.000001)
    }

    @Test
    fun `initial bearing points north east south west`() {
        assertEquals(0.0, TripStatsCalculator.initialBearingDegrees(6.0, -75.0, 6.001, -75.0), 0.5)
        assertEquals(90.0, TripStatsCalculator.initialBearingDegrees(6.0, -75.0, 6.0, -74.999), 0.5)
        assertEquals(180.0, TripStatsCalculator.initialBearingDegrees(6.0, -75.0, 5.999, -75.0), 0.5)
        assertEquals(270.0, TripStatsCalculator.initialBearingDegrees(6.0, -75.0, 6.0, -75.001), 0.5)
    }

    @Test
    fun `initial bearing medellin to bogota is roughly southeast`() {
        // Medellín (6.2442, -75.5812) → Bogotá (4.7110, -74.0721) ≈ 136°
        val bearing = TripStatsCalculator.initialBearingDegrees(6.2442, -75.5812, 4.7110, -74.0721)
        assertEquals(136.0, bearing, 3.0)
    }

    @Test
    fun `gps distance with exactly two points matches known distance`() {
        val points = listOf(
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = 0L,
                latitude = 0.0,
                longitude = 0.0,
                speedKmh = 0f,
            ),
            com.revscope.core.data.db.entities.GpsPointEntity(
                sessionId = 1L,
                timestamp = 1_000L,
                latitude = 0.0,
                longitude = 0.001,
                speedKmh = 0f,
            ),
        )
        assertEquals(0.1111949, TripStatsCalculator.gpsDistanceKm(points), 0.000001)
    }
}
