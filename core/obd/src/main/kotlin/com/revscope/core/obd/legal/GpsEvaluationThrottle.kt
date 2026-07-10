package com.revscope.core.obd.legal

private const val THROTTLE_MS = 120_000L
private const val MIN_DISTANCE_KM = 3.0

/**
 * Throttle puro para limitar evaluaciones costosas de un fix GPS (ej. un lookup de
 * Geocoder): como máximo una vez cada 120s y solo si el fix se movió más de 3km
 * respecto al último evaluado. Sin dependencias de Android — testable directamente.
 * Usado por [LocalityDetector].
 */
class GpsEvaluationThrottle {

    @Volatile private var lastEvaluatedAt = 0L
    @Volatile private var lastLat: Double? = null
    @Volatile private var lastLon: Double? = null

    fun shouldEvaluate(latitude: Double, longitude: Double, nowMs: Long): Boolean {
        if (nowMs - lastEvaluatedAt < THROTTLE_MS) return false
        val lat = lastLat
        val lon = lastLon
        if (lat != null && lon != null && CityRegistry.haversineKm(lat, lon, latitude, longitude) < MIN_DISTANCE_KM) {
            return false
        }
        return true
    }

    /** Debe llamarse una vez por fix aceptado por [shouldEvaluate] para que el throttle avance. */
    fun recordEvaluation(latitude: Double, longitude: Double, nowMs: Long) {
        lastEvaluatedAt = nowMs
        lastLat = latitude
        lastLon = longitude
    }
}
