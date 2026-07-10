package com.revscope.core.obd.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.entities.ImuPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val SAMPLING_PERIOD_US = 20_000        // 50 Hz
private const val STORE_INTERVAL_MS = 100L           // persist at 10 Hz
private const val FLUSH_INTERVAL_MS = 5_000L
private const val EMA_ALPHA = 0.25f                  // low-pass vs engine vibration
private const val G = 9.80665f
private const val STATIONARY_G_THRESHOLD = 0.06f     // ~0.6 m/s² of filtered movement
private const val CALIBRATION_HOLD_MS = 2_000L
private const val MAX_PLAUSIBLE_LEAN_DEG = 70f       // past this the phone was handled, not leaned
private const val RAW_PEAK_WINDOW_MS = 200L          // rolling peak window fed to CrashDetector

/**
 * Phone-IMU telemetry in vehicle frame:
 *
 * - G forces need no mount calibration: linear acceleration (gravity already
 *   removed by sensor fusion) is rotated device→world with the rotation vector,
 *   then projected onto the GPS travel bearing — longitudinal (+accel/−braking)
 *   and lateral (+right/−left) regardless of how the phone is mounted.
 * - Lean angle DOES need a mount baseline: auto-calibrated after the vehicle sits
 *   still for 2 s (reference gravity vector); lean = angle between the current and
 *   calibrated gravity directions in device frame. Field data showed the earlier
 *   rotation-matrix roll flipping ±180° with real mounts — the gravity-angle method
 *   is bounded and flip-free.
 */
class MotionSensorRecorder(
    private val context: Context,
    private val imuDao: ImuDao,
    private val hub: MotionMetricsHub,
) : SensorEventListener {

    private val buffer = mutableListOf<ImuPointEntity>()
    private var flushJob: Job? = null
    private var recorderScope: CoroutineScope? = null
    private var sessionId = 0L
    private var registered = false

    // NEW-1: lets the foreground service silence DB writes during crash-detection grace
    // (closed session already has its aggregates computed) while sensors keep running and
    // still feed MotionMetricsHub for CrashResponder.
    @Volatile private var persistenceEnabled = true

    // Sensor fusion state
    private val rotationMatrix = FloatArray(9)
    private var hasRotation = false
    private val filteredAccel = FloatArray(3)       // device frame, EMA-filtered
    private val filteredGravity = FloatArray(3)     // device frame, EMA-filtered
    private var hasGravity = false

    // Raw (pre-filter) impact envelope for CrashDetector — independent of rotation/gravity
    // fusion so it isn't blocked by their startup warm-up.
    private data class RawPeakSample(val timestampMs: Long, val magnitudeG: Float)
    private val rawPeakSamples = ArrayDeque<RawPeakSample>()

    // Vehicle frame inputs
    @Volatile private var gpsBearingDeg: Float? = null

    // Lean calibration — reference gravity direction with the vehicle upright
    private var baselineGravity: FloatArray? = null
    private var stationarySinceMs = -1L
    @Volatile private var lastStoreMs = 0L

    fun start(scope: CoroutineScope, newSessionId: Long) {
        if (registered) return
        sessionId = newSessionId
        recorderScope = scope
        persistenceEnabled = true
        hub.resetSession()
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return

        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        if (linear == null || rotation == null || gravity == null) {
            Timber.w("MotionSensorRecorder: required sensors missing — IMU disabled")
            return
        }
        sensorManager.registerListener(this, linear, SAMPLING_PERIOD_US)
        sensorManager.registerListener(this, rotation, SAMPLING_PERIOD_US)
        sensorManager.registerListener(this, gravity, SAMPLING_PERIOD_US)
        registered = true
        Timber.i("MotionSensorRecorder: recording IMU for session $newSessionId")

        flushJob = scope.launch {
            try {
                while (true) {
                    delay(FLUSH_INTERVAL_MS)
                    flush()
                }
            } finally {
                withContext(NonCancellable) { flush() }
            }
        }
    }

    fun stop() {
        if (registered) {
            (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
                ?.unregisterListener(this)
            registered = false
        }
        flushJob?.cancel()
        flushJob = null
        recorderScope = null
        persistenceEnabled = true
        baselineGravity = null
        stationarySinceMs = -1L
        rawPeakSamples.clear()
    }

    /** Fed from GPS fixes — defines the vehicle's forward axis in world frame. */
    fun updateGpsBearing(bearingDeg: Float) {
        gpsBearingDeg = bearingDeg
    }

    /**
     * NEW-1: sensors keep registering and keep feeding [hub] either way — only the buffer
     * append + DB flush path is gated. Flushes whatever is already buffered once before
     * turning persistence off so no in-flight points are silently dropped.
     */
    fun setPersistenceEnabled(enabled: Boolean) {
        if (persistenceEnabled == enabled) return
        persistenceEnabled = enabled
        if (!enabled) recorderScope?.launch { flush() }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotation = true
            }
            Sensor.TYPE_GRAVITY -> {
                for (i in 0..2) {
                    filteredGravity[i] += EMA_ALPHA * (event.values[i] - filteredGravity[i])
                }
                hasGravity = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                recordRawPeak(event.values)
                for (i in 0..2) {
                    filteredAccel[i] += EMA_ALPHA * (event.values[i] - filteredAccel[i])
                }
                if (hasRotation && hasGravity) processSample()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Full 3-axis magnitude of the RAW linear-acceleration sample, in G, before the EMA
     * filter runs — a real impact spike can be flattened below the crash threshold by the
     * filter's smoothing, and the UI-facing [processSample] signal is horizontal-only.
     */
    private fun recordRawPeak(rawValues: FloatArray) {
        val now = System.currentTimeMillis()
        val magnitudeG = sqrt(
            rawValues[0] * rawValues[0] + rawValues[1] * rawValues[1] + rawValues[2] * rawValues[2]
        ) / G
        rawPeakSamples.addLast(RawPeakSample(now, magnitudeG))
        while (rawPeakSamples.isNotEmpty() && now - rawPeakSamples.first().timestampMs > RAW_PEAK_WINDOW_MS) {
            rawPeakSamples.removeFirst()
        }
        hub.updateRawPeak(rawPeakSamples.maxOf { it.magnitudeG })
    }

    private fun processSample() {
        val now = System.currentTimeMillis()

        // Device → world (ENU): a_world = R · a_device
        val r = rotationMatrix
        val ax = filteredAccel[0]; val ay = filteredAccel[1]; val az = filteredAccel[2]
        val east = r[0] * ax + r[1] * ay + r[2] * az
        val north = r[3] * ax + r[4] * ay + r[5] * az
        // Horizontal magnitude before bearing projection — stays meaningful with
        // no GPS fix yet, which crash detection cannot afford to wait for.
        val horizontalG = sqrt(east * east + north * north) / G

        autoCalibrateLean(now, horizontalG)

        val bearing = gpsBearingDeg
        var gLat = 0f
        var gLong = 0f
        if (bearing != null) {
            val rad = Math.toRadians(bearing.toDouble())
            val fwdE = sin(rad).toFloat()
            val fwdN = cos(rad).toFloat()
            gLong = (east * fwdE + north * fwdN) / G
            gLat = (east * fwdN - north * fwdE) / G // + = right of travel
        }

        val lean = computeLeanDeg()
        hub.update(gLat, gLong, lean, magnitudeG = horizontalG, calibrated = baselineGravity != null)

        if (now - lastStoreMs >= STORE_INTERVAL_MS) {
            lastStoreMs = now
            if (persistenceEnabled) {
                val point = ImuPointEntity(
                    sessionId = sessionId,
                    timestamp = now,
                    gLat = gLat,
                    gLong = gLong,
                    leanDeg = lean,
                )
                synchronized(buffer) { buffer += point }
            }
        }
    }

    /** Captures the mount baseline after 2 s of near-zero filtered acceleration. */
    private fun autoCalibrateLean(now: Long, horizontalG: Float) {
        if (horizontalG < STATIONARY_G_THRESHOLD) {
            if (stationarySinceMs < 0) stationarySinceMs = now
            if (now - stationarySinceMs >= CALIBRATION_HOLD_MS) {
                baselineGravity = filteredGravity.copyOf()
                stationarySinceMs = now // keep refining while still
            }
        } else {
            stationarySinceMs = -1L
        }
    }

    /**
     * Unsigned tilt of the vehicle vs the calibrated upright pose: angle between
     * the current and baseline gravity directions in device frame. Bounded and
     * mount-independent; values past [MAX_PLAUSIBLE_LEAN_DEG] (phone handled,
     * pocket) are discarded as 0.
     */
    private fun computeLeanDeg(): Float {
        val base = baselineGravity ?: return 0f
        val g = filteredGravity
        val magBase = sqrt(base[0] * base[0] + base[1] * base[1] + base[2] * base[2])
        val magNow = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        if (magBase < 1f || magNow < 1f) return 0f
        val dot = (base[0] * g[0] + base[1] * g[1] + base[2] * g[2]) / (magBase * magNow)
        val angle = Math.toDegrees(acos(dot.coerceIn(-1f, 1f).toDouble())).toFloat()
        return if (angle <= MAX_PLAUSIBLE_LEAN_DEG) angle else 0f
    }

    private suspend fun flush() {
        val snapshot = synchronized(buffer) {
            if (buffer.isEmpty()) return
            buffer.toList().also { buffer.clear() }
        }
        runCatching { imuDao.insertAll(snapshot) }
            .onFailure { Timber.e(it, "MotionSensorRecorder: flush failed (${snapshot.size})") }
    }
}
