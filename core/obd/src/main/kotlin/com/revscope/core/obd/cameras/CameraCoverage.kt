package com.revscope.core.obd.cameras

import com.revscope.core.obd.telemetry.TripStatsCalculator

// 70% del radio de descarga (50 km) — refresca antes de quedarse sin cobertura real
private const val COVERAGE_THRESHOLD_M = 35_000.0

/** Pure decision: does the stored camera-download center still cover this position? */
object CameraCoverage {

    fun needsRefresh(centerLat: Double?, centerLon: Double?, latitude: Double, longitude: Double): Boolean {
        if (centerLat == null || centerLon == null) return true
        val distance = TripStatsCalculator.haversineMeters(centerLat, centerLon, latitude, longitude)
        return distance > COVERAGE_THRESHOLD_M
    }
}
