package com.revscope.core.obd.track

import com.revscope.core.obd.telemetry.TripStatsCalculator

/**
 * Carrera contra fantasma, pura y sin I/O. El fantasma es la mejor vuelta previa
 * (puntos GPS con tiempo relativo al inicio de vuelta). El delta se compara POR
 * DISTANCIA recorrida, no por posición: en la misma distancia acumulada, ¿cuánto
 * tiempo llevaba el fantasma? delta = miTiempo − tiempoFantasma (+ = voy perdiendo).
 */
class GhostRaceEngine {

    data class GhostPoint(val lat: Double, val lon: Double, val tRelMs: Long)

    private var ghostCumDistM: DoubleArray = DoubleArray(0)
    private var ghostTimesMs: LongArray = LongArray(0)

    private var myCumDistM = 0.0
    private var lastLat: Double? = null
    private var lastLon: Double? = null

    val hasGhost: Boolean get() = ghostTimesMs.size >= 2

    fun setGhost(points: List<GhostPoint>) {
        if (points.size < 2) {
            ghostCumDistM = DoubleArray(0)
            ghostTimesMs = LongArray(0)
            return
        }
        val dist = DoubleArray(points.size)
        val times = LongArray(points.size)
        times[0] = points[0].tRelMs
        for (i in 1 until points.size) {
            dist[i] = dist[i - 1] + TripStatsCalculator.haversineMeters(
                points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon,
            )
            times[i] = points[i].tRelMs
        }
        ghostCumDistM = dist
        ghostTimesMs = times
    }

    fun clearGhost() = setGhost(emptyList())

    /** Reinicia el acumulador propio — llamar en cada cruce de meta / arme. */
    fun onLapStart() {
        myCumDistM = 0.0
        lastLat = null
        lastLon = null
    }

    /** Fix de mi vuelta en curso → delta en ms contra el fantasma, o null sin fantasma. */
    fun onFix(lat: Double, lon: Double, myElapsedMs: Long): Long? {
        val prevLat = lastLat
        val prevLon = lastLon
        lastLat = lat
        lastLon = lon
        if (prevLat != null && prevLon != null) {
            myCumDistM += TripStatsCalculator.haversineMeters(prevLat, prevLon, lat, lon)
        }
        if (!hasGhost) return null
        return myElapsedMs - ghostTimeAtDistance(myCumDistM)
    }

    /** Tiempo (interpolado) que llevaba el fantasma al alcanzar [distM]. */
    private fun ghostTimeAtDistance(distM: Double): Long {
        val dist = ghostCumDistM
        val times = ghostTimesMs
        if (distM <= 0.0) return times.first()
        if (distM >= dist.last()) return times.last()
        var lo = 0
        var hi = dist.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) / 2
            if (dist[mid] <= distM) lo = mid else hi = mid
        }
        val span = dist[hi] - dist[lo]
        val fraction = if (span <= 0.0) 0.0 else (distM - dist[lo]) / span
        return times[lo] + ((times[hi] - times[lo]) * fraction).toLong()
    }
}
