package com.revscope.core.navigation

/** Una lectura del GPS, sin tipos de Android, para que el motor se pueda probar. */
data class LocationFix(
    val lat: Double,
    val lon: Double,
    val accuracyM: Double,
    val bearingDeg: Int?,
    val speedMps: Double?,
    val timestampMs: Long,
)

data class NavigationState(
    val maneuver: Maneuver?,
    val distanceToManeuverM: Int,
    val distanceRemainingM: Int,
    val durationRemainingS: Int,
    /** La posición enganchada a la ruta: es la que se dibuja, no la cruda del GPS. */
    val snapped: LatLon?,
    val offRoute: Boolean,
    val arrived: Boolean,
) {
    companion object {
        val IDLE = NavigationState(
            maneuver = null,
            distanceToManeuverM = 0,
            distanceRemainingM = 0,
            durationRemainingS = 0,
            snapped = null,
            offRoute = false,
            arrived = false,
        )
    }
}
