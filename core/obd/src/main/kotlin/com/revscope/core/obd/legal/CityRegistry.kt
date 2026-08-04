package com.revscope.core.obd.legal

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ciudades soportadas por pico y placa, con su centro geográfico para detección por GPS.
 * `rules == null` significa que la ciudad está en el registro pero su rotación aún no está
 * confirmada (ej. Cali) — el consumidor debe pedir al usuario que la configure manualmente.
 */
object CityRegistry {

    private const val EARTH_RADIUS_KM = 6371.0088

    data class City(
        val id: String,
        val nombre: String,
        val lat: Double,
        val lon: Double,
        val radiusKm: Double,
        val rules: PicoYPlacaEngine.CityRules?,
    )

    val CITIES: List<City> = listOf(
        City(
            id = "medellin",
            nombre = "Medellín",
            lat = 6.2442,
            lon = -75.5812,
            radiusKm = 18.0,
            rules = PicoYPlacaEngine.MEDELLIN_2026_S2,
        ),
        City(
            id = "bogota",
            nombre = "Bogotá",
            lat = 4.7110,
            lon = -74.0721,
            radiusKm = 22.0,
            rules = PicoYPlacaEngine.BOGOTA_2026,
        ),
        City(
            id = "cali",
            nombre = "Cali",
            lat = 3.4516,
            lon = -76.5320,
            radiusKm = 14.0,
            rules = null,
        ),
    )

    /** La ciudad más cercana a [lat]/[lon] dentro de su propio radio, o null si ninguna aplica. */
    fun nearest(lat: Double, lon: Double): City? =
        CITIES
            .map { it to haversineKm(lat, lon, it.lat, it.lon) }
            .filter { (city, distanceKm) -> distanceKm <= city.radiusKm }
            .minByOrNull { (_, distanceKm) -> distanceKm }
            ?.first

    /**
     * Resuelve las reglas vigentes de [cityId]. [overrideRules] es la edición manual del
     * usuario (Ajustes → JSON) y solo aplica si edita esa misma ciudad; de lo contrario se
     * usan las reglas registradas en [CITIES] (null si aún no están confirmadas, ej. Cali).
     */
    fun resolveRules(cityId: String, overrideRules: PicoYPlacaEngine.CityRules?): PicoYPlacaEngine.CityRules? {
        if (overrideRules != null && overrideRules.cityId == cityId) return overrideRules
        return CITIES.firstOrNull { it.id == cityId }?.rules
    }

    /** Visibility bumped to internal so [LocalityDetector]'s throttle can reuse it. */
    internal fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }
}
