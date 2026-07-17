package com.revscope.core.obd.cameras

import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val ALERT_DISTANCE_M = 400.0
private const val PER_CAMERA_COOLDOWN_MS = 120_000L

/**
 * Watches the live GPS stream and fires a spoken warning when approaching a
 * stored speed camera. Cameras are cached in memory (a city region is a few
 * hundred nodes); [invalidateCache] reloads after a download.
 */
@Singleton
class SpeedCameraAlerter @Inject constructor(
    private val dao: SpeedCameraDao,
    private val alertsEngine: AlertsEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var cameras: List<SpeedCameraEntity> = emptyList()
    private val loading = AtomicBoolean(false)
    private var loaded = false
    private val lastAlertedAt = mutableMapOf<Long, Long>()
    private val lastDistanceM = mutableMapOf<Long, Double>()

    fun invalidateCache() {
        loaded = false
        ensureLoaded()
    }

    fun onGpsFix(latitude: Double, longitude: Double, headingDeg: Float?) {
        if (!loaded) {
            ensureLoaded()
            return
        }
        val now = System.currentTimeMillis()
        for (camera in cameras) {
            val distance = TripStatsCalculator.haversineMeters(
                latitude, longitude, camera.latitude, camera.longitude,
            )
            if (distance > ALERT_DISTANCE_M) {
                lastDistanceM.remove(camera.osmId)
                continue
            }
            val previousDistance = lastDistanceM.put(camera.osmId, distance)
            val bearingToCamera = TripStatsCalculator.initialBearingDegrees(
                latitude, longitude, camera.latitude, camera.longitude,
            )
            if (!CameraApproachGate.shouldAlert(headingDeg, bearingToCamera, previousDistance, distance)) continue
            synchronized(lastAlertedAt) {
                if (now - (lastAlertedAt[camera.osmId] ?: 0L) < PER_CAMERA_COOLDOWN_MS) return@synchronized
                lastAlertedAt[camera.osmId] = now
                alertsEngine.announceSpeedCamera(distance.toInt(), camera.maxSpeedKmh)
            }
        }
    }

    private fun ensureLoaded() {
        if (!loading.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                cameras = dao.all()
                loaded = true
                Timber.i("SpeedCameraAlerter: ${cameras.size} cameras in memory")
            }
            loading.set(false)
        }
    }
}
