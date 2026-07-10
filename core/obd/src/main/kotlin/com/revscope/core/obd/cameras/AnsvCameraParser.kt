package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import timber.log.Timber

/** Only sites the registry marks as actually operating are worth alerting on. */
private const val OPERATIONAL_STATUS = "Operando"

/** Larger than any two points on Earth can be apart — effectively "no radius filter". */
private const val NATIONWIDE_RADIUS_M = 20_040_000.0

/**
 * Pure parsing of the ANSV `sast.json` registry feed into [SpeedCameraEntity]
 * rows, filtered to operational sites within range of a center point — no I/O,
 * fully unit-testable.
 */
object AnsvCameraParser {

    /**
     * Nationwide count of operational, coordinate-valid entries, ignoring any
     * particular search radius — used as a format-regression guard: distinguishes
     * "no cameras near this point" (legitimate) from "the feed's schema changed
     * and nothing parses anymore" (a bug), which a radius-scoped count can't tell apart.
     */
    fun countAllOperational(json: String): Int = parse(json, 0.0, 0.0, NATIONWIDE_RADIUS_M).size

    fun parse(json: String, centerLat: Double, centerLon: Double, radiusM: Double): List<SpeedCameraEntity> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return buildList {
            for (i in 0 until results.length()) {
                val ubicaciones = results.getJSONObject(i).optJSONArray("ubicaciones") ?: continue
                for (j in 0 until ubicaciones.length()) {
                    parseUbicacionSafely(ubicaciones.getJSONObject(j), centerLat, centerLon, radiusM)?.let(::add)
                }
            }
        }
    }

    /** Isolates one malformed record so it can't abort parsing of the rest of the feed. */
    private fun parseUbicacionSafely(
        ubicacion: JSONObject,
        centerLat: Double,
        centerLon: Double,
        radiusM: Double,
    ): SpeedCameraEntity? = try {
        parseUbicacion(ubicacion, centerLat, centerLon, radiusM)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "AnsvCameraParser: skipping malformed record")
        null
    }

    private fun parseUbicacion(
        ubicacion: JSONObject,
        centerLat: Double,
        centerLon: Double,
        radiusM: Double,
    ): SpeedCameraEntity? {
        if (ubicacion.optString("estado_operacion") != OPERATIONAL_STATUS) return null
        if (!ubicacion.has("latitud") || !ubicacion.has("longitud")) return null
        val lat = ubicacion.getDouble("latitud")
        val lon = ubicacion.getDouble("longitud")
        if (TripStatsCalculator.haversineMeters(centerLat, centerLon, lat, lon) > radiusM) return null
        return SpeedCameraEntity(
            // ANSV ids are small positive ints — negate to keep them out of the
            // positive id space used by OSM elements (see OverpassCameraParser).
            osmId = -ubicacion.getLong("id"),
            latitude = lat,
            longitude = lon,
            maxSpeedKmh = parseMaxSpeed(ubicacion),
        )
    }

    /** Field is a string, and "not set" is serialized as the literal text "None". */
    private fun parseMaxSpeed(ubicacion: JSONObject): Int? =
        ubicacion.optString("velocidad_maxima_permitida").toIntOrNull()
}
