package com.revscope.core.obd.road

import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.cameras.CameraApproachGate
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val ALERT_DISTANCE_M = 200.0
private const val PER_POTHOLE_COOLDOWN_MS = 120_000L
private const val MIN_HITS_TO_ALERT = 1

/**
 * Avisa al acercarse a un hueco del mapa personal — mismo patrón direccional que
 * los radares (cono de rumbo + acercándose) para no avisar huecos de otra calle.
 */
@Singleton
class PotholeAlerter @Inject constructor(
    private val dao: PotholeDao,
    private val alertsEngine: AlertsEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var potholes: List<PotholeEntity> = emptyList()
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
        for (pothole in potholes) {
            if (pothole.hits < MIN_HITS_TO_ALERT) continue
            val distance = TripStatsCalculator.haversineMeters(
                latitude, longitude, pothole.latitude, pothole.longitude,
            )
            if (distance > ALERT_DISTANCE_M) {
                lastDistanceM.remove(pothole.id)
                continue
            }
            val previousDistance = lastDistanceM.put(pothole.id, distance)
            val bearing = TripStatsCalculator.initialBearingDegrees(
                latitude, longitude, pothole.latitude, pothole.longitude,
            )
            if (!CameraApproachGate.shouldAlert(headingDeg, bearing, previousDistance, distance)) continue
            synchronized(lastAlertedAt) {
                if (now - (lastAlertedAt[pothole.id] ?: 0L) < PER_POTHOLE_COOLDOWN_MS) return@synchronized
                lastAlertedAt[pothole.id] = now
                alertsEngine.announcePothole(distance.toInt())
            }
        }
    }

    private fun ensureLoaded() {
        if (!loading.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                potholes = dao.all()
                loaded = true
                Timber.i("PotholeAlerter: ${potholes.size} huecos en memoria")
            }
            loading.set(false)
        }
    }
}
