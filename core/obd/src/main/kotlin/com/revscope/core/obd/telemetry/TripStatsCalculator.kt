package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val MAX_INTEGRATION_GAP_MS = 5_000L
private const val MS_PER_HOUR = 3_600_000.0
private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Pure trip math over recorded telemetry points — no I/O, fully unit-testable.
 */
object TripStatsCalculator {

    /**
     * Trapezoidal integration of speed (km/h) over time → distance in km.
     * Gaps longer than [MAX_INTEGRATION_GAP_MS] (link loss, app in background)
     * are skipped instead of counting phantom kilometres.
     */
    fun distanceKm(speedPoints: List<TelemetryPointEntity>): Double {
        if (speedPoints.size < 2) return 0.0
        var distance = 0.0
        for (i in 1 until speedPoints.size) {
            val prev = speedPoints[i - 1]
            val curr = speedPoints[i]
            val dtMs = curr.timestamp - prev.timestamp
            if (dtMs <= 0 || dtMs > MAX_INTEGRATION_GAP_MS) continue
            val avgSpeedKmh = (prev.value + curr.value) / 2.0
            distance += avgSpeedKmh * (dtMs / MS_PER_HOUR)
        }
        return distance
    }

    /** Sum of haversine segment distances over a GPS track → km. */
    fun gpsDistanceKm(points: List<GpsPointEntity>): Double {
        if (points.size < 2) return 0.0
        var meters = 0.0
        for (i in 1 until points.size) {
            meters += haversineMeters(
                points[i - 1].latitude, points[i - 1].longitude,
                points[i].latitude, points[i].longitude,
            )
        }
        return meters / 1_000.0
    }

    /** Initial great-circle bearing from point 1 to point 2, degrees 0–360 (0 = north). */
    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
