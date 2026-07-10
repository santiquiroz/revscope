package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator

private const val DEDUPE_RADIUS_M = 100.0

/**
 * Merges camera lists from multiple sources (OSM, ANSV) into one, collapsing
 * entries within [DEDUPE_RADIUS_M] of each other since the same physical
 * camera is often mapped independently by both. Pure — no I/O, unit-testable.
 */
object CameraDedupe {

    fun merge(cameras: List<SpeedCameraEntity>): List<SpeedCameraEntity> {
        val kept = mutableListOf<SpeedCameraEntity>()
        for (camera in cameras) {
            val duplicateIndex = kept.indexOfFirst { isSameCamera(it, camera) }
            when {
                duplicateIndex == -1 -> kept.add(camera)
                shouldReplace(kept[duplicateIndex], camera) -> kept[duplicateIndex] = camera
            }
        }
        return kept
    }

    private fun isSameCamera(a: SpeedCameraEntity, b: SpeedCameraEntity): Boolean =
        TripStatsCalculator.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude) <= DEDUPE_RADIUS_M

    /** Prefer whichever duplicate carries a maxspeed tag; keep the first-seen otherwise. */
    private fun shouldReplace(existing: SpeedCameraEntity, candidate: SpeedCameraEntity): Boolean =
        existing.maxSpeedKmh == null && candidate.maxSpeedKmh != null
}
