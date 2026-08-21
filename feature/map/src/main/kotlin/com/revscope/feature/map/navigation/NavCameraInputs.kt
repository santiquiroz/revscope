package com.revscope.feature.map.navigation

import com.revscope.core.navigation.LatLon

/**
 * Entradas resueltas para la cámara de navegación en un tick: a dónde apunta y con qué rumbo.
 * Función pura para que LiveMapScreen solo aplique el resultado — la cadena de fallback del
 * target y la elección de rumbo (course-up vs. rumbo del track crudo) se prueban acá.
 */
object NavCameraInputs {

    data class Resolved(val target: LatLon, val bearingDeg: Double, val nearestIndex: Int)

    /**
     * [snapped] (posición enganchada a la ruta) manda; si aún no hay snap (justo al arrancar
     * la navegación) cae al fix crudo del GPS, y solo si tampoco hay fix usa el último punto
     * del track vivo. Sin ninguno de los tres, no hay cámara que mover.
     */
    fun resolve(
        snapped: LatLon?,
        liveFix: LatLon?,
        routeLastPoint: LatLon?,
        routePoints: List<LatLon>,
        fallbackBearingDeg: Double,
        fromIndex: Int,
    ): Resolved? {
        val target = snapped ?: liveFix ?: routeLastPoint ?: return null
        val courseUp = NavBearing.courseUpBearing(routePoints, target, fromIndex = fromIndex)
        return Resolved(
            target = target,
            bearingDeg = courseUp?.bearingDeg ?: fallbackBearingDeg,
            nearestIndex = courseUp?.nearestIndex ?: fromIndex,
        )
    }
}
