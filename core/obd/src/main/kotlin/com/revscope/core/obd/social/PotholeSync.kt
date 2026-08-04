package com.revscope.core.obd.social

import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.obd.road.PotholeAlerter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val PULL_BBOX_HALF_DEG = 0.45 // ~50 km alrededor de la posición

/**
 * Sync oportunista de huecos: sube los locales pendientes y baja los de la región.
 * OFFLINE-FIRST: si no hay server/red, no pasa NADA visible — la fuente de verdad
 * local nunca depende del servidor.
 */
@Singleton
class PotholeSync @Inject constructor(
    private val client: ServerClient,
    private val dao: PotholeDao,
    private val potholeAlerter: PotholeAlerter,
) {

    data class SyncStatus(val lastSyncAt: Long?, val uploaded: Int, val downloaded: Int)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)

    private val _status = MutableStateFlow(SyncStatus(null, 0, 0))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    @Volatile private var lastAutoSyncMs = 0L

    /** Auto-sync oportunista desde el stream GPS — máximo 1 vez por hora. */
    fun onGpsFix(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastAutoSyncMs < 3_600_000L) return
        lastAutoSyncMs = now
        syncAround(lat, lon)
    }

    /** Dispara un sync en background alrededor de [lat],[lon]. No bloquea, no falla visible. */
    fun syncAround(lat: Double, lon: Double) {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            runCatching { doSync(lat, lon) }
                .onFailure { Timber.i("PotholeSync: sin sync (${it.message}) — seguimos offline") }
            running.set(false)
        }
    }

    private suspend fun doSync(lat: Double, lon: Double) {
        if (client.config() == null) return

        // Subir pendientes — el server dedupa por 30 m, así que cada local se sube UNA vez
        var uploaded = 0
        val pending = dao.unsynced()
        val uploadedIds = mutableListOf<Long>()
        for (pothole in pending) {
            val body = JSONObject()
                .put("lat", pothole.latitude)
                .put("lon", pothole.longitude)
                .put("severity_g", pothole.severityG.toDouble().coerceIn(1.0, 10.0))
            if (client.postJson("/v1/potholes", body).isSuccess) {
                uploadedIds += pothole.id
                uploaded++
            }
        }
        if (uploadedIds.isNotEmpty()) dao.markSynced(uploadedIds, System.currentTimeMillis())

        // Bajar región y reemplazar el cache remoto completo
        val bbox = "%.4f,%.4f,%.4f,%.4f".format(
            java.util.Locale.US,
            lon - PULL_BBOX_HALF_DEG, lat - PULL_BBOX_HALF_DEG,
            lon + PULL_BBOX_HALF_DEG, lat + PULL_BBOX_HALF_DEG,
        )
        val remote = client.getJsonArray("/v1/potholes?bbox=$bbox").getOrThrow()
        val entities = buildList {
            for (i in 0 until remote.length()) {
                val o = remote.getJSONObject(i)
                add(
                    PotholeEntity(
                        latitude = o.getDouble("lat"),
                        longitude = o.getDouble("lon"),
                        severityG = o.getDouble("severity_g").toFloat(),
                        hits = o.getInt("reports"),
                        lastHitAt = System.currentTimeMillis(),
                        source = PotholeEntity.SOURCE_REMOTE,
                        syncedAt = System.currentTimeMillis(),
                    )
                )
            }
        }
        dao.deleteRemote()
        if (entities.isNotEmpty()) dao.insertAllRemote(entities)
        potholeAlerter.invalidateCache()

        _status.value = SyncStatus(System.currentTimeMillis(), uploaded, entities.size)
        Timber.i("PotholeSync: ↑$uploaded ↓${entities.size}")
    }
}
