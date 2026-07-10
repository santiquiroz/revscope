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

    /**
     * Returns the number of cameras stored, or throws if every source fails.
     *
     * The local table is only replaced when the merged result is trustworthy:
     * either both sources succeeded (a true empty area is a legitimate result),
     * or at least one source succeeded and produced cameras. A single source
     * failing while the other returns zero in-radius results does NOT wipe the
     * previously-downloaded table — the stale data stays until a download can
     * confirm the area is actually empty.
     */
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
            val bothSourcesSucceeded = osmCameras != null && ansvCameras != null
            if (!bothSourcesSucceeded && cameras.isEmpty()) {
                Timber.w(
                    "SpeedCameraUpdater: partial failure with no cameras from the surviving source " +
                        "(OSM=${osmCameras?.size ?: "failed"}, ANSV=${ansvCameras?.size ?: "failed"}) " +
                        "— keeping existing table untouched",
                )
                return@withContext dao.count()
            }

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
