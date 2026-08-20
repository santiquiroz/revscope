package com.revscope.core.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val CURVY_SINUOSITY_THRESHOLD = 1.15
private const val EARTH_RADIUS_M = 6_371_000.0
private const val LABEL_FAST = "Rápida"
private const val LABEL_CURVY = "Curvas"
private const val LABEL_ALT = "Alt"

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
     * La de menor duración es "Rápida". Entre las demás, la más sinuosa por encima del umbral
     * es "Curvas" — el resto queda "Alt". Con 0 o 1 rutas no hay nada que comparar.
     */
    fun labelAlternatives(routes: List<NavigationRoute>): List<String> {
        if (routes.size <= 1) return routes.map { LABEL_FAST }
        val fastestIndex = routes.indices.minByOrNull { routes[it].durationS } ?: 0
        val sinuosities = routes.map(::routeSinuosity)
        val curvyIndex = curviestBeyondThreshold(routes.indices, fastestIndex, sinuosities)
        return routes.indices.map { index -> labelFor(index, fastestIndex, curvyIndex) }
    }

    private fun curviestBeyondThreshold(indices: IntRange, fastestIndex: Int, sinuosities: List<Double>): Int? =
        indices
            .filter { it != fastestIndex && sinuosities[it] > CURVY_SINUOSITY_THRESHOLD }
            .maxByOrNull { sinuosities[it] }

    private fun labelFor(index: Int, fastestIndex: Int, curvyIndex: Int?): String {
        if (index == fastestIndex) return LABEL_FAST
        if (index == curvyIndex) return LABEL_CURVY
        return LABEL_ALT
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
