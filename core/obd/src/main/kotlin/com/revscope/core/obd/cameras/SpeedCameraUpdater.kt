package com.revscope.core.obd.cameras

import com.revscope.core.data.db.dao.SpeedCameraDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val RADIUS_M = 50_000

/**
 * Downloads speed cameras within 50 km of a location, combining OpenStreetMap
 * (community-mapped, see [OsmCameraSource]) and Colombia's official ANSV
 * registry (see [AnsvCameraSource]), deduplicates entries within 100 m of each
 * other via [CameraDedupe], and stores the merged result locally — alerts then
 * work fully offline.
 */
@Singleton
class SpeedCameraUpdater @Inject constructor(
    private val dao: SpeedCameraDao,
    private val alerter: SpeedCameraAlerter,
) {

    /** Returns the number of cameras stored, or throws if every source fails. */
    suspend fun downloadAround(latitude: Double, longitude: Double): Int =
        withContext(Dispatchers.IO) {
            val osmCameras = fetchSource("OSM") {
                OsmCameraSource.fetchWithinRadius(latitude, longitude, RADIUS_M)
            }
            val ansvCameras = fetchSource("ANSV") {
                AnsvCameraSource.fetchWithinRadius(latitude, longitude, RADIUS_M.toDouble())
            }
            if (osmCameras == null && ansvCameras == null) error("No se pudo descargar de ninguna fuente")

            val cameras = CameraDedupe.merge(osmCameras.orEmpty() + ansvCameras.orEmpty())
            dao.replaceAll(cameras)
            alerter.invalidateCache()
            Timber.i(
                "SpeedCameraUpdater: stored ${cameras.size} cameras " +
                    "(OSM=${osmCameras?.size ?: "failed"}, ANSV=${ansvCameras?.size ?: "failed"})",
            )
            cameras.size
        }

    /** Isolates one source's failure so the other can still populate the database. */
    private suspend fun <T> fetchSource(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "SpeedCameraUpdater: $label fetch failed")
        null
    }
}
