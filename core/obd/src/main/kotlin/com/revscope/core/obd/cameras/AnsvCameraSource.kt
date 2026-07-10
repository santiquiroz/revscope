package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val ANSV_URL = "https://fotodeteccion.ansv.gov.co/data/sast.json"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

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
        return AnsvCameraParser.parse(response, latitude, longitude, radiusM)
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
