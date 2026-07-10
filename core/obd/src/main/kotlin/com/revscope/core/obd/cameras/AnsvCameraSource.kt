package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import timber.log.Timber

private const val ANSV_URL = "https://fotodeteccion.ansv.gov.co/data/sast.json"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

/** Below this size a payload is trivially small (e.g. an error page) — not worth the format-regression check. */
private const val NONTRIVIAL_PAYLOAD_BYTES = 100_000

/**
 * Fetches Colombia's official ANSV authorized speed-camera registry (Agencia
 * Nacional de Seguridad Vial) and filters it to cameras marked "Operando"
 * within range of a center point.
 *
 * The registry's public page (https://fotodeteccion.ansv.gov.co/ubicaciones-aprobadas.html)
 * has no documented API — it embeds an iframe (map/ubicaciones-aprobadas.html)
 * whose script fetches this same JSON URL to feed a DevExtreme grid/map. The
 * payload is a stable, versioned data feed (carries its own `timestamp`), not
 * a page we scrape: we parse the JSON body directly, never HTML.
 */
object AnsvCameraSource {

    suspend fun fetchWithinRadius(latitude: Double, longitude: Double, radiusM: Double): List<SpeedCameraEntity> {
        val response = fetchJson()
        checkFormatNotRegressed(response)
        return AnsvCameraParser.parse(response, latitude, longitude, radiusM)
    }

    /**
     * A non-trivial payload that parses to zero records nationwide means the feed's
     * schema changed, not that the registry is genuinely empty — fail loud instead
     * of silently treating it as an empty-but-successful source.
     */
    private fun checkFormatNotRegressed(response: String) {
        val isNonTrivialPayload = response.toByteArray(Charsets.UTF_8).size > NONTRIVIAL_PAYLOAD_BYTES
        if (!isNonTrivialPayload || AnsvCameraParser.countAllOperational(response) > 0) return
        Timber.e("AnsvCameraParser: payload no vacío con 0 registros — posible cambio de formato")
        error("AnsvCameraSource: posible cambio de formato en el payload de ANSV")
    }

    private fun fetchJson(): String {
        val connection = (URL(ANSV_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
