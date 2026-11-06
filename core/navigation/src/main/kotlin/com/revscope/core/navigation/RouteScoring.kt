package com.revscope.core.navigation

import com.revscope.core.common.i18n.StringResolver
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val CURVY_SINUOSITY_THRESHOLD = 1.15
private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Etiqueta de una ruta alternativa. La UI decide por el valor, no por el texto: el texto
 * cambia con el idioma.
 */
enum class RouteLabel(val textRes: Int) {
    FAST(R.string.nav_route_fastest),
    CURVY(R.string.nav_route_curvy),
    ALT(R.string.nav_route_alt),
}

/**
 * Puntaje puro de rutas alternativas de OSRM: cuánto se desvía cada una de la línea recta
 * origen-destino, para distinguir la más directa de la más "paseadera" en moto (spec F6).
 */
object RouteScoring {

    /** routeDistanceM sobre la distancia en línea recta. 1.0 = perfectamente recta, crece con las curvas. */
    fun sinuosity(
        routeDistanceM: Double,
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
    ): Double {
        val straightLineM = haversineMeters(originLat, originLon, destLat, destLon)
        if (straightLineM <= 0.0) return 1.0
        return routeDistanceM / straightLineM
    }

    /**
     * La de menor duración es [RouteLabel.FAST]. Entre las demás, la más sinuosa por encima del
     * umbral es [RouteLabel.CURVY] — el resto queda [RouteLabel.ALT]. Con 0 o 1 rutas no hay
     * nada que comparar.
     */
    fun classifyAlternatives(routes: List<NavigationRoute>): List<RouteLabel> {
        if (routes.size <= 1) return routes.map { RouteLabel.FAST }
        val fastestIndex = routes.indices.minByOrNull { routes[it].durationS } ?: 0
        val sinuosities = routes.map(::routeSinuosity)
        val curvyIndex = curviestBeyondThreshold(routes.indices, fastestIndex, sinuosities)
        return routes.indices.map { index -> labelFor(index, fastestIndex, curvyIndex) }
    }

    /** [classifyAlternatives] con el texto de cada etiqueta en el idioma de [resolver]. */
    fun labelAlternatives(routes: List<NavigationRoute>, resolver: StringResolver): List<String> =
        classifyAlternatives(routes).map { resolver.get(it.textRes) }

    private fun curviestBeyondThreshold(indices: IntRange, fastestIndex: Int, sinuosities: List<Double>): Int? =
        indices
            .filter { it != fastestIndex && sinuosities[it] > CURVY_SINUOSITY_THRESHOLD }
            .maxByOrNull { sinuosities[it] }

    private fun labelFor(index: Int, fastestIndex: Int, curvyIndex: Int?): RouteLabel {
        if (index == fastestIndex) return RouteLabel.FAST
        if (index == curvyIndex) return RouteLabel.CURVY
        return RouteLabel.ALT
    }

    private fun routeSinuosity(route: NavigationRoute): Double {
        val origin = route.points.firstOrNull() ?: return 1.0
        val dest = route.points.lastOrNull() ?: return 1.0
        return sinuosity(route.distanceM, origin.lat, origin.lon, dest.lat, dest.lon)
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return EARTH_RADIUS_M * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
