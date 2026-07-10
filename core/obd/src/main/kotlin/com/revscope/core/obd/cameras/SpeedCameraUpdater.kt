package com.revscope.core.obd.cameras

import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val RADIUS_M = 50_000

/** Per-source breakdown of a camera download, or of the currently stored table. */
data class CameraDownloadResult(val total: Int, val osmCount: Int, val ansvCount: Int) {
    companion object {
        /** OSM ids are positive, ANSV ids are negated — see [AnsvCameraParser]. */
        fun from(cameras: List<SpeedCameraEntity>) = CameraDownloadResult(
            total = cameras.size,
            osmCount = cameras.count { it.osmId > 0 },
            ansvCount = cameras.count { it.osmId < 0 },
        )
    }
}

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
     * Returns the per-source counts of cameras stored, or throws if every source fails.
     *
     * The local table is only replaced when the merged result is trustworthy:
     * either both sources succeeded (a true empty area is a legitimate result),
     * or at least one source succeeded and produced cameras. A single source
     * failing while the other returns zero in-radius results does NOT wipe the
     * previously-downloaded table — the stale data stays until a download can
     * confirm the area is actually empty.
     */
    suspend fun downloadAround(latitude: Double, longitude: Double): CameraDownloadResult =
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
                return@withContext storedCounts()
            }

            dao.replaceAll(cameras)
            alerter.invalidateCache()
            Timber.i(
                "SpeedCameraUpdater: stored ${cameras.size} cameras " +
                    "(OSM=${osmCameras?.size ?: "failed"}, ANSV=${ansvCameras?.size ?: "failed"})",
            )
            CameraDownloadResult.from(cameras)
        }

    /** Per-source counts of whatever is currently persisted, without downloading anything. */
    suspend fun storedCounts(): CameraDownloadResult =
        CameraDownloadResult(total = dao.count(), osmCount = dao.countOsm(), ansvCount = dao.countAnsv())

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
