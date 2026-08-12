package com.revscope.core.maps

import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/** Vértices del círculo. osmdroid usaba 60; se mantiene para que el borde se vea igual. */
private const val CIRCLE_STEPS = 60

/**
 * Círculo geodésico de radio en METROS. `circle-radius` de MapLibre es en píxeles de
 * pantalla, así que un CircleLayer cambiaría de tamaño físico con el zoom y mostraría
 * un radio de alerta que no es el real.
 */
fun geodesicCircle(lat: Double, lon: Double, radiusMeters: Double): Polygon =
    TurfTransformation.circle(
        Point.fromLngLat(lon, lat),
        radiusMeters,
        CIRCLE_STEPS,
        TurfConstants.UNIT_METRES,
    )

/** `[minLat, minLon, maxLat, maxLon]`, o null si no hay puntos. */
fun boundsOf(points: List<Pair<Double, Double>>): DoubleArray? {
    if (points.isEmpty()) return null
    var minLat = Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    for ((lat, lon) in points) {
        if (lat < minLat) minLat = lat
        if (lon < minLon) minLon = lon
        if (lat > maxLat) maxLat = lat
        if (lon > maxLon) maxLon = lon
    }
    return doubleArrayOf(minLat, minLon, maxLat, maxLon)
}
