package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.TelemetryPointEntity

private const val MAX_INTEGRATION_GAP_MS = 5_000L
private const val MS_PER_HOUR = 3_600_000.0

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
}
