package com.revscope.core.obd.road

import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator

private const val CLUSTER_RADIUS_M = 30.0

/** Decisión pura de dedupe: un golpe a <30 m de un hueco conocido refuerza ese hueco. */
object PotholeClustering {

    fun findNearby(
        latitude: Double,
        longitude: Double,
        known: List<PotholeEntity>,
    ): PotholeEntity? = known.firstOrNull {
        TripStatsCalculator.haversineMeters(latitude, longitude, it.latitude, it.longitude) <= CLUSTER_RADIUS_M
    }

    fun reinforced(existing: PotholeEntity, severityG: Float, nowMs: Long): PotholeEntity =
        existing.copy(
            severityG = maxOf(existing.severityG, severityG),
            hits = existing.hits + 1,
            lastHitAt = nowMs,
        )
}
