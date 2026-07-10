package com.revscope.core.obd.motion

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * App-wide live motion snapshot — written by MotionSensorRecorder (foreground
 * service), read by any UI (track mode, dashboard) without binding the service.
 */
@Singleton
class MotionMetricsHub @Inject constructor() {

    data class MotionSnapshot(
        val gLat: Float = 0f,
        val gLong: Float = 0f,
        val leanDeg: Float = 0f,
        val maxAbsGLat: Float = 0f,
        val maxBrakingG: Float = 0f,   // most negative gLong, stored as positive magnitude
        val maxAbsLean: Float = 0f,
        val calibrated: Boolean = false,
        // Horizontal acceleration magnitude in G, independent of GPS bearing —
        // used for the existing UI metrics (EMA-filtered, so unsuitable for crash detection).
        val magnitudeG: Float = 0f,
        // Full 3-axis magnitude of the RAW (pre-filter) accel sample, 200ms rolling peak.
        // Feeds CrashDetector directly — EMA smoothing can flatten a genuine impact spike.
        val rawPeakG: Float = 0f,
    )

    private val _snapshot = MutableStateFlow(MotionSnapshot())
    val snapshot: StateFlow<MotionSnapshot> = _snapshot.asStateFlow()

    fun update(gLat: Float, gLong: Float, leanDeg: Float, magnitudeG: Float, calibrated: Boolean) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            gLat = gLat,
            gLong = gLong,
            leanDeg = leanDeg,
            maxAbsGLat = maxOf(current.maxAbsGLat, abs(gLat)),
            maxBrakingG = maxOf(current.maxBrakingG, -gLong),
            maxAbsLean = maxOf(current.maxAbsLean, abs(leanDeg)),
            calibrated = calibrated,
            magnitudeG = magnitudeG,
        )
    }

    /** Published on every raw accel sample, independent of [update]'s rotation/gravity gating. */
    fun updateRawPeak(rawPeakG: Float) {
        _snapshot.value = _snapshot.value.copy(rawPeakG = rawPeakG)
    }

    fun resetSession() {
        _snapshot.value = MotionSnapshot()
    }
}
