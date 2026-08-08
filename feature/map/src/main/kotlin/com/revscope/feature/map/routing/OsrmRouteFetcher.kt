package com.revscope.feature.map.routing

import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Cliente mínimo del servidor público OSRM — una ruta en carro entre dos puntos.
 * Sin API key; el servidor demo pide tráfico moderado, y un mapa personal lo es.
 */
object OsrmRouteFetcher {

    data class Route(
        val points: List<PolylineDecoder.LatLon>,
        val distanceM: Double,
        val durationS: Double,
    )

    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    fun fetch(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Route? {
        val url = String.format(
            Locale.US,
            "%s/%f,%f;%f,%f?overview=full&geometries=polyline",
            BASE_URL, fromLon, fromLat, toLon, toLat,
        )
        return runCatching { parseRoute(httpGet(url)) }
            .onFailure { Timber.w(it, "OsrmRouteFetcher: route fetch failed") }
            .getOrNull()
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRoute(body: String): Route? {
        val json = JSONObject(body)
        if (json.optString("code") != "Ok") return null
        val route = json.getJSONArray("routes").optJSONObject(0) ?: return null
        val points = PolylineDecoder.decode(route.getString("geometry"))
        if (points.isEmpty()) return null
        return Route(
            points = points,
            distanceM = route.getDouble("distance"),
            durationS = route.getDouble("duration"),
        )
    }
}
