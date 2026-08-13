package com.revscope.core.navigation

/**
 * Un paso de la ruta, con la semántica de OSRM: la maniobra ocurre **al principio** del paso,
 * en [maneuverAt], y [distanceM] es lo que se recorre desde ahí hasta la maniobra siguiente.
 *
 * Consecuencia para la navegación: mientras uno circula por el paso `i`, la maniobra que hay
 * que anunciar es la del paso `i + 1`.
 */
data class RouteStep(
    val maneuver: Maneuver,
    val maneuverAt: LatLon,
    val distanceM: Double,
    val durationS: Double,
)

data class NavigationRoute(
    val points: List<LatLon>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
)
