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
import kotlin.math.atan2
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

/**
 * Phone-IMU telemetry in vehicle frame:
 *
 * - G forces need no mount calibration: linear acceleration (gravity already
 *   removed by sensor fusion) is rotated device→world with the rotation vector,
 *   then projected onto the GPS travel bearing — longitudinal (+accel/−braking)
 *   and lateral (+right/−left) regardless of how the phone is mounted.
 * - Lean angle DOES need a mount baseline: auto-calibrated after the vehicle sits
 *   still for 2 s (reference rotation matrix); lean = roll of the relative rotation.
 */
class MotionSensorRecorder(
    private val context: Context,
    private val imuDao: ImuDao,
    private val hub: MotionMetricsHub,
) : SensorEventListener {

    private val buffer = mutableListOf<ImuPointEntity>()
    private var flushJob: Job? = null
    private var sessionId = 0L
    private var registered = false

    // Sensor fusion state
    private val rotationMatrix = FloatArray(9)
    private var hasRotation = false
    private val filteredAccel = FloatArray(3)       // device frame, EMA-filtered

    // Vehicle frame inputs
    @Volatile private var gpsBearingDeg: Float? = null

    // Lean calibration
    private var baselineRotation: FloatArray? = null
    private var stationarySinceMs = -1L
    @Volatile private var lastStoreMs = 0L

    fun start(scope: CoroutineScope, newSessionId: Long) {
        if (registered) return
        sessionId = newSessionId
        hub.resetSession()
        val sensorManager =
            context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return

        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (linear == null || rotation == null) {
            Timber.w("MotionSensorRecorder: required sensors missing — IMU disabled")
            return
        }
        sensorManager.registerListener(this, linear, SAMPLING_PERIOD_US)
        sensorManager.registerListener(this, rotation, SAMPLING_PERIOD_US)
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
        baselineRotation = null
        stationarySinceMs = -1L
    }

    /** Fed from GPS fixes — defines the vehicle's forward axis in world frame. */
    fun updateGpsBearing(bearingDeg: Float) {
        gpsBearingDeg = bearingDeg
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                hasRotation = true
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                for (i in 0..2) {
                    filteredAccel[i] += EMA_ALPHA * (event.values[i] - filteredAccel[i])
                }
                if (hasRotation) processSample()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processSample() {
        val now = System.currentTimeMillis()

        // Device → world (ENU): a_world = R · a_device
        val r = rotationMatrix
        val ax = filteredAccel[0]; val ay = filteredAccel[1]; val az = filteredAccel[2]
        val east = r[0] * ax + r[1] * ay + r[2] * az
        val north = r[3] * ax + r[4] * ay + r[5] * az

        autoCalibrateLean(now, east, north)

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
        hub.update(gLat, gLong, lean, calibrated = baselineRotation != null)

        if (now - lastStoreMs >= STORE_INTERVAL_MS) {
            lastStoreMs = now
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

    /** Captures the mount baseline after 2 s of near-zero filtered acceleration. */
    private fun autoCalibrateLean(now: Long, east: Float, north: Float) {
        val horizontalG = sqrt(east * east + north * north) / G
        if (horizontalG < STATIONARY_G_THRESHOLD) {
            if (stationarySinceMs < 0) stationarySinceMs = now
            if (now - stationarySinceMs >= CALIBRATION_HOLD_MS) {
                baselineRotation = rotationMatrix.copyOf()
                stationarySinceMs = now // keep refining while still
            }
        } else {
            stationarySinceMs = -1L
        }
    }

    /**
     * Roll of the current orientation relative to the calibrated mount:
     * R_rel = R₀ᵀ·R, roll extracted from the relative matrix.
     */
    private fun computeLeanDeg(): Float {
        val base = baselineRotation ?: return 0f
        val r = rotationMatrix
        // rel = baseᵀ · current (row-major 3×3)
        val rel7 = base[1] * r[6] + base[4] * r[7] + base[7] * r[8]
        val rel8 = base[2] * r[6] + base[5] * r[7] + base[8] * r[8]
        val roll = atan2(rel7.toDouble(), rel8.toDouble())
        return Math.toDegrees(roll).toFloat()
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
