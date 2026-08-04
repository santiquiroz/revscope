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

    // Acumulado entre emisiones: los máximos se actualizan en cada muestra (50 Hz) pero el
    // StateFlow solo emite a 10 Hz — emitir por muestra despachaba ~100 eventos/s a cada
    // colector (dashboard, track mode, CrashResponder) durante todo el viaje.
    @Volatile private var pending = MotionSnapshot()
    @Volatile private var lastEmitMs = 0L

    fun update(gLat: Float, gLong: Float, leanDeg: Float, magnitudeG: Float, calibrated: Boolean) {
        val current = pending
        pending = current.copy(
            gLat = gLat,
            gLong = gLong,
            leanDeg = leanDeg,
            maxAbsGLat = maxOf(current.maxAbsGLat, abs(gLat)),
            maxBrakingG = maxOf(current.maxBrakingG, -gLong),
            maxAbsLean = maxOf(current.maxAbsLean, abs(leanDeg)),
            calibrated = calibrated,
            magnitudeG = magnitudeG,
        )
        maybeEmit(force = false)
    }

    /** Registered on every raw accel sample, independent of [update]'s rotation/gravity gating. */
    fun updateRawPeak(rawPeakG: Float) {
        // Un spike de impacto no puede esperar la ventana de throttle: CrashDetector dispara
        // a 6 G y la ventana rolling del pico es de solo 200 ms.
        val spike = rawPeakG >= SPIKE_EMIT_G && rawPeakG > pending.rawPeakG
        pending = pending.copy(rawPeakG = rawPeakG)
        maybeEmit(force = spike)
    }

    private fun maybeEmit(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastEmitMs < EMIT_INTERVAL_MS) return
        lastEmitMs = now
        _snapshot.value = pending
    }

    fun resetSession() {
        pending = MotionSnapshot()
        lastEmitMs = 0L
        _snapshot.value = pending
    }

    private companion object {
        const val EMIT_INTERVAL_MS = 100L
        const val SPIKE_EMIT_G = 2.5f
    }
}
