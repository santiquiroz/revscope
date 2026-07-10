package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

/**
 * Fetches speed cameras mapped in OpenStreetMap within a radius of a point:
 * point cameras (`highway=speed_camera` nodes/ways) plus enforcement zones
 * (`type=enforcement`+`enforcement=maxspeed` relations, e.g. section control).
 * Coverage depends on what the OSM community has mapped.
 */
object OsmCameraSource {

    suspend fun fetchWithinRadius(latitude: Double, longitude: Double, radiusM: Int): List<SpeedCameraEntity> {
        val response = fetchOverpassJson(buildQuery(latitude, longitude, radiusM))
        return OverpassCameraParser.parse(response)
    }

    private fun buildQuery(latitude: Double, longitude: Double, radiusM: Int): String {
        val around = "around:$radiusM,$latitude,$longitude"
        return "[out:json][timeout:50];(" +
            "node[\"highway\"=\"speed_camera\"]($around);" +
            "way[\"highway\"=\"speed_camera\"]($around);" +
            "relation[\"type\"=\"enforcement\"][\"enforcement\"=\"maxspeed\"]($around);" +
            ");out center;"
    }

    private fun fetchOverpassJson(query: String): String {
        val body = "data=" + URLEncoder.encode(query, "UTF-8")
        val connection = (URL(OVERPASS_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
        }
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
