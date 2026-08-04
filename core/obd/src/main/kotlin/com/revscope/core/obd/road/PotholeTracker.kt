package com.revscope.core.obd.road

import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.obd.service.LiveRouteHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val SPIKE_MIN_G = 2.5f
private const val SPIKE_MAX_G = 6.0f          // por encima de esto es territorio de CrashDetector
private const val MIN_SPEED_KMH = 12f         // parado o maniobrando no cuenta como hueco
private const val MAX_SPEED_KMH = 130f
private const val SPIKE_THROTTLE_MS = 3_000L  // un hueco = una ráfaga de samples; registrar uno

/**
 * Construye el mapa personal de huecos: golpe vertical del IMU + posición GPS.
 * Un golpe a <30 m de un hueco conocido lo refuerza (hits+1) en vez de duplicarlo.
 */
@Singleton
class PotholeTracker @Inject constructor(
    private val dao: PotholeDao,
    private val routeHolder: LiveRouteHolder,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var known: List<PotholeEntity> = emptyList()
    @Volatile private var loaded = false
    @Volatile private var lastSpikeMs = 0L

    fun onVerticalSpike(magnitudeG: Float) {
        val now = System.currentTimeMillis()
        if (now - lastSpikeMs < SPIKE_THROTTLE_MS) return
        if (magnitudeG < SPIKE_MIN_G || magnitudeG > SPIKE_MAX_G) return
        val speed = routeHolder.lastSpeedKmh.value
        if (speed < MIN_SPEED_KMH || speed > MAX_SPEED_KMH) return
        val position = routeHolder.lastPoint.value ?: return
        lastSpikeMs = now
        scope.launch { record(position.lat, position.lon, magnitudeG, now) }
    }

    fun invalidateCache() {
        loaded = false
    }

    private suspend fun record(lat: Double, lon: Double, severityG: Float, nowMs: Long) {
        runCatching {
            if (!loaded) {
                // Solo clusterizamos contra los LOCALES: los remotos son de otros y su
                // dedupe vive en el servidor.
                known = dao.all().filter { it.source == PotholeEntity.SOURCE_LOCAL }
                loaded = true
            }
            val nearby = PotholeClustering.findNearby(lat, lon, known)
            if (nearby != null) {
                val updated = PotholeClustering.reinforced(nearby, severityG, nowMs)
                dao.update(updated)
                known = known.map { if (it.id == updated.id) updated else it }
                Timber.i("PotholeTracker: hueco reforzado id=${updated.id} hits=${updated.hits}")
            } else {
                val entity = PotholeEntity(
                    latitude = lat, longitude = lon,
                    severityG = severityG, hits = 1, lastHitAt = nowMs,
                )
                val id = dao.insert(entity)
                known = known + entity.copy(id = id)
                Timber.i("PotholeTracker: hueco nuevo ${"%.1f".format(severityG)}G en $lat,$lon")
            }
        }.onFailure { Timber.w(it, "PotholeTracker: no pude registrar el golpe") }
    }
}
