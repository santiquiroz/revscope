package com.revscope.feature.map.routing

import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.navigation.OsrmRouteParser
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Cliente mínimo del servidor público OSRM — una ruta en carro entre dos puntos.
 * Sin API key; el servidor demo pide tráfico moderado, y un mapa personal lo es.
 */
object OsrmRouteFetcher {

    private const val BASE_URL = "https://router.project-osrm.org/route/v1/driving"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    // El demo de OSRM devuelve 403 al UA "Dalvik/..." que Android manda por defecto;
    // identificarse con app+contacto es además la política de uso del servidor.
    private const val USER_AGENT = "RevScope (+https://github.com/santiquiroz/revscope)"

    /** `geometries=polyline` implica precisión 5. Las dos cosas van juntas o la ruta se desplaza. */
    private const val GEOMETRY_FORMAT = "polyline"
    private const val GEOMETRY_PRECISION = 5

    fun fetch(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): NavigationRoute? {
        val url = String.format(
            Locale.US,
            "%s/%f,%f;%f,%f?overview=full&geometries=%s&steps=true",
            BASE_URL, fromLon, fromLat, toLon, toLat, GEOMETRY_FORMAT,
        )
        return runCatching { OsrmRouteParser.parse(httpGet(url), GEOMETRY_PRECISION) }
            .onFailure { Timber.w(it, "OsrmRouteFetcher: route fetch failed") }
            .getOrNull()
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
