package com.revscope.feature.map.navigation

import com.revscope.core.navigation.LatLon
import com.revscope.core.obd.telemetry.TripStatsCalculator

private const val DEFAULT_LOOKAHEAD_M = 30.0
private const val NEAREST_SEARCH_BACKTRACK = 5

/**
 * Rumbo "course-up" sobre la geometría de la ruta planeada (no el track GPS crudo, que tiene
 * lag y jitter con pocos puntos): ubica el punto de la ruta más cercano a la posición actual y
 * apunta hacia donde vas ~30m más adelante siguiendo la polyline, suavizando curvas en vez de
 * saltar al rumbo del micro-segmento siguiente.
 */
object NavBearing {

    /** [nearestIndex] se devuelve para que el caller lo reuse como [courseUpBearing.fromIndex] en el próximo tick. */
    data class CourseUp(val bearingDeg: Double, val nearestIndex: Int)

    fun courseUpBearing(
        routePoints: List<LatLon>,
        position: LatLon,
        lookaheadM: Double = DEFAULT_LOOKAHEAD_M,
        fromIndex: Int = 0,
    ): CourseUp? {
        if (routePoints.size < 2) return null
        val nearestIndex = nearestPointIndex(routePoints, position, fromIndex)
        if (nearestIndex == routePoints.lastIndex) return incomingSegmentBearing(routePoints, nearestIndex)
        val target = pointAheadOnRoute(routePoints, nearestIndex, lookaheadM)
        val nearest = routePoints[nearestIndex]
        val bearing = TripStatsCalculator.initialBearingDegrees(nearest.lat, nearest.lon, target.lat, target.lon)
        return CourseUp(bearing, nearestIndex)
    }

    /**
     * En el último punto no hay "adelante" que mirar — [pointAheadOnRoute] degeneraría al mismo
     * punto y daría `initialBearingDegrees(p, p)` = atan2(0,0) = 0.0, o sea un salto fantasma a
     * norte en la recta final. En vez de eso devolvemos el rumbo del tramo con el que se llega
     * (el anterior siempre existe: el guard de <2 puntos ya descartó rutas sin él). Nunca es
     * null a propósito — el caller (LiveMapScreen) cae a `currentBearingDegrees(route)` del
     * track GPS crudo cuando esto es null, que es justo el jitter que NavBearing existe para
     * evitar; el segmento entrante sigue siendo geometría de ruta, no track crudo.
     */
    private fun incomingSegmentBearing(routePoints: List<LatLon>, lastIndex: Int): CourseUp {
        val previous = routePoints[lastIndex - 1]
        val last = routePoints[lastIndex]
        val bearing = TripStatsCalculator.initialBearingDegrees(previous.lat, previous.lon, last.lat, last.lon)
        return CourseUp(bearing, lastIndex)
    }

    /**
     * Escanea linealmente desde [fromIndex] - [NEAREST_SEARCH_BACKTRACK] hacia adelante — nunca
     * antes de eso. Así una ruta que se cruza a sí misma (vuelve cerca de un punto ya recorrido)
     * no salta hacia atrás al match viejo cuando ya avanzamos más allá con el hint.
     */
    private fun nearestPointIndex(routePoints: List<LatLon>, position: LatLon, fromIndex: Int): Int {
        val searchStart = (fromIndex - NEAREST_SEARCH_BACKTRACK).coerceIn(0, routePoints.lastIndex)
        var bestIndex = searchStart
        var bestDistanceM = Double.MAX_VALUE
        for (index in searchStart until routePoints.size) {
            val point = routePoints[index]
            val distanceM = TripStatsCalculator.haversineMeters(position.lat, position.lon, point.lat, point.lon)
            if (distanceM < bestDistanceM) {
                bestDistanceM = distanceM
                bestIndex = index
            }
        }
        return bestIndex
    }

    /** Camina la polyline desde [startIndex] acumulando distancia hasta [lookaheadM]; si la ruta se acaba antes, devuelve el último punto. */
    private fun pointAheadOnRoute(routePoints: List<LatLon>, startIndex: Int, lookaheadM: Double): LatLon {
        var accumulatedM = 0.0
        var previous = routePoints[startIndex]
        for (index in startIndex + 1 until routePoints.size) {
            val current = routePoints[index]
            val segmentM = TripStatsCalculator.haversineMeters(previous.lat, previous.lon, current.lat, current.lon)
            if (accumulatedM + segmentM >= lookaheadM) {
                return interpolate(previous, current, lookaheadM - accumulatedM, segmentM)
            }
            accumulatedM += segmentM
            previous = current
        }
        return previous
    }

    private fun interpolate(from: LatLon, to: LatLon, distanceM: Double, segmentM: Double): LatLon {
        if (segmentM <= 0.0) return to
        val fraction = (distanceM / segmentM).coerceIn(0.0, 1.0)
        return LatLon(
            lat = from.lat + (to.lat - from.lat) * fraction,
            lon = from.lon + (to.lon - from.lon) * fraction,
        )
    }
}
