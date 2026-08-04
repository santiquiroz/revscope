package com.revscope.core.obd.cameras

import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val ALERT_DISTANCE_M = 400.0
// Rango visual del mapa: más amplio que el de audio para anticipar el radar objetivo
// sin disparar la voz todavía.
private const val VISUAL_RANGE_M = 1_000.0
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

    /** Radar hacia el que el vehículo se dirige AHORA (cono ±60° del rumbo, <1 km). */
    data class ApproachingCamera(
        val osmId: Long,
        val latitude: Double,
        val longitude: Double,
        val distanceM: Int,
        val maxSpeedKmh: Int?,
    )

    @Volatile private var cameras: List<SpeedCameraEntity> = emptyList()
    private val loading = AtomicBoolean(false)
    private var loaded = false
    private val lastAlertedAt = mutableMapOf<Long, Long>()
    private val lastDistanceM = mutableMapOf<Long, Double>()

    // El mapa en vivo lo consume para resaltar SOLO el radar relevante — los radares
    // de otras calles/sentidos se atenúan en vez de gritar todos a la vez.
    private val _approaching = MutableStateFlow<ApproachingCamera?>(null)
    val approaching: StateFlow<ApproachingCamera?> = _approaching.asStateFlow()

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
        var target: ApproachingCamera? = null
        for (camera in cameras) {
            val distance = TripStatsCalculator.haversineMeters(
                latitude, longitude, camera.latitude, camera.longitude,
            )
            if (distance > VISUAL_RANGE_M) {
                lastDistanceM.remove(camera.osmId)
                continue
            }
            val bearingToCamera = TripStatsCalculator.initialBearingDegrees(
                latitude, longitude, camera.latitude, camera.longitude,
            )
            val inCone = headingDeg != null &&
                CameraApproachGate.angularDifferenceDeg(headingDeg.toDouble(), bearingToCamera) <= 60.0
            if (inCone && (target == null || distance < target.distanceM)) {
                target = ApproachingCamera(
                    osmId = camera.osmId,
                    latitude = camera.latitude,
                    longitude = camera.longitude,
                    distanceM = distance.toInt(),
                    maxSpeedKmh = camera.maxSpeedKmh,
                )
            }

            if (distance > ALERT_DISTANCE_M) {
                lastDistanceM.remove(camera.osmId)
                continue
            }
            val previousDistance = lastDistanceM.put(camera.osmId, distance)
            if (!CameraApproachGate.shouldAlert(headingDeg, bearingToCamera, previousDistance, distance)) continue
            synchronized(lastAlertedAt) {
                if (now - (lastAlertedAt[camera.osmId] ?: 0L) < PER_CAMERA_COOLDOWN_MS) return@synchronized
                lastAlertedAt[camera.osmId] = now
                alertsEngine.announceSpeedCamera(distance.toInt(), camera.maxSpeedKmh)
            }
        }
        _approaching.value = target
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
