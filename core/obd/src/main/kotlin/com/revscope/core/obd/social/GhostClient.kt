package com.revscope.core.obd.social

import com.revscope.core.obd.track.GhostRaceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fantasmas remotos: sube tu mejor vuelta y baja las de otros para correr contra
 * ellas en Modo Pista. OFFLINE-FIRST: sin server/red todo retorna null/failure
 * silencioso — el ghost self (mejor vuelta de la sesión) sigue funcionando igual.
 */
@Singleton
class GhostClient @Inject constructor(
    private val client: ServerClient,
) {

    data class GhostSummary(val id: Long, val rider: String, val timeMs: Long)

    /** Sube una vuelta. [points] con tRel relativo al cruce de meta. Devuelve el id o null. */
    suspend fun upload(
        finishLat: Double,
        finishLon: Double,
        timeMs: Long,
        points: List<GhostRaceEngine.GhostPoint>,
    ): Long? {
        val pointsJson = JSONArray()
        points.forEach { p ->
            pointsJson.put(JSONArray().put(p.lat).put(p.lon).put(p.tRelMs))
        }
        val body = JSONObject()
            .put("finish_lat", finishLat)
            .put("finish_lon", finishLon)
            .put("time_ms", timeMs)
            .put("points", pointsJson)
        return client.postJson("/v1/ghosts", body).getOrNull()
            ?.optLong("id")?.takeIf { it > 0 }
    }

    /** Lista las vueltas registradas para la pista de esta línea de meta (mejores primero). */
    suspend fun near(finishLat: Double, finishLon: Double): List<GhostSummary> {
        val near = "%.6f,%.6f".format(java.util.Locale.US, finishLat, finishLon)
        val arr = client.getJsonArray("/v1/ghosts?near=$near").getOrNull() ?: return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(GhostSummary(o.getLong("id"), o.getString("rider"), o.getLong("time_ms")))
            }
        }
    }

    /** Baja los puntos completos de un fantasma para cargarlo en el motor de carrera. */
    suspend fun download(ghostId: Long): List<GhostRaceEngine.GhostPoint> = withContext(Dispatchers.IO) {
        val o = client.getJson("/v1/ghosts/$ghostId").getOrNull() ?: return@withContext emptyList()
        val pts = o.optJSONArray("points") ?: return@withContext emptyList()
        buildList {
            for (i in 0 until pts.length()) {
                val p = pts.getJSONArray(i)
                add(GhostRaceEngine.GhostPoint(p.getDouble(0), p.getDouble(1), p.getLong(2)))
            }
        }.also { Timber.i("GhostClient: fantasma $ghostId con ${it.size} puntos") }
    }
}
