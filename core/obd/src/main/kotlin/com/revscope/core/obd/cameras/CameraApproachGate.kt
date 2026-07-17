package com.revscope.core.obd.cameras

import kotlin.math.abs

private const val HEADING_TOLERANCE_DEG = 60.0

/**
 * Pure decision logic for directional speed-camera alerts: alert only when the
 * vehicle is heading toward the camera (±60° cone) AND getting closer since the
 * previous GPS fix. No heading (stationary) or no previous distance → no alert.
 */
object CameraApproachGate {

    fun shouldAlert(
        headingDeg: Float?,
        bearingToCameraDeg: Double,
        previousDistanceM: Double?,
        distanceM: Double,
    ): Boolean {
        if (headingDeg == null) return false
        if (angularDifferenceDeg(headingDeg.toDouble(), bearingToCameraDeg) > HEADING_TOLERANCE_DEG) return false
        if (previousDistanceM == null) return false
        return distanceM < previousDistanceM
    }

    fun angularDifferenceDeg(a: Double, b: Double): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
