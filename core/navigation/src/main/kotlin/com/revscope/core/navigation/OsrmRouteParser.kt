package com.revscope.core.navigation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Traduce la respuesta de la Route API de OSRM al modelo de navegación. Puro: recibe el cuerpo
 * ya descargado, así se puede probar con respuestas reales guardadas sin tocar la red.
 *
 * [precision] debe ser la que corresponde al parámetro `geometries` que se pidió — ver
 * [PolylineDecoder].
 */
object OsrmRouteParser {

    fun parse(body: String, precision: Int): NavigationRoute? {
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        if (json.optString("code") != "Ok") return null
        val route = json.optJSONArray("routes")?.optJSONObject(0) ?: return null
        val points = PolylineDecoder.decode(route.optString("geometry"), precision)
        if (points.isEmpty()) return null
        return NavigationRoute(
            points = points,
            distanceM = route.optDouble("distance", 0.0),
            durationS = route.optDouble("duration", 0.0),
            steps = parseSteps(route.optJSONArray("legs"), precision),
        )
    }

    /** Los pasos vienen repartidos por tramos; para navegar son una sola secuencia. */
    private fun parseSteps(legs: JSONArray?, precision: Int): List<RouteStep> {
        if (legs == null) return emptyList()
        val steps = mutableListOf<RouteStep>()
        for (legIndex in 0 until legs.length()) {
            val legSteps = legs.optJSONObject(legIndex)?.optJSONArray("steps") ?: continue
            for (stepIndex in 0 until legSteps.length()) {
                val step = legSteps.optJSONObject(stepIndex) ?: continue
                parseStep(step, precision)?.let(steps::add)
            }
        }
        return steps
    }

    private fun parseStep(step: JSONObject, precision: Int): RouteStep? {
        val maneuverJson = step.optJSONObject("maneuver") ?: return null
        val type = maneuverJson.optString("type").takeIf { it.isNotEmpty() } ?: return null
        val at = parseLocation(maneuverJson.optJSONArray("location"))
            ?: firstGeometryPoint(step, precision)
            ?: return null
        return RouteStep(
            maneuver = Maneuver(
                type = type,
                modifier = maneuverJson.optString("modifier").takeIf { it.isNotEmpty() },
                streetName = step.optString("name").takeIf { it.isNotEmpty() },
            ),
            maneuverAt = at,
            distanceM = step.optDouble("distance", 0.0),
            durationS = step.optDouble("duration", 0.0),
        )
    }

    /** OSRM entrega coordenadas como `[lon, lat]`, al revés de como se leen. */
    private fun parseLocation(location: JSONArray?): LatLon? {
        if (location == null || location.length() < 2) return null
        return LatLon(lat = location.optDouble(1), lon = location.optDouble(0))
    }

    private fun firstGeometryPoint(step: JSONObject, precision: Int): LatLon? {
        val geometry = step.optString("geometry").takeIf { it.isNotEmpty() } ?: return null
        return PolylineDecoder.decode(geometry, precision).firstOrNull()
    }
}
