package com.revscope.feature.map.navigation

import com.revscope.core.navigation.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val LAT_STEP_DEG = 0.0001 // ~11.1 m
private const val BASE_LAT = 6.2000
private const val BASE_LON = -75.5800

class NavCameraInputsTest {

    @Test
    fun `snapped manda sobre liveFix y ultimo punto del track`() {
        val snapped = LatLon(BASE_LAT, BASE_LON)
        val liveFix = LatLon(BASE_LAT + 1.0, BASE_LON)
        val routeLast = LatLon(BASE_LAT + 2.0, BASE_LON)

        val result = NavCameraInputs.resolve(
            snapped = snapped,
            liveFix = liveFix,
            routeLastPoint = routeLast,
            routePoints = northRoute(),
            fallbackBearingDeg = 123.0,
            fromIndex = 0,
        )

        assertEquals(snapped, result!!.target)
    }

    @Test
    fun `sin snapped cae al liveFix crudo`() {
        val liveFix = LatLon(BASE_LAT + 1.0, BASE_LON)
        val routeLast = LatLon(BASE_LAT + 2.0, BASE_LON)

        val result = NavCameraInputs.resolve(
            snapped = null,
            liveFix = liveFix,
            routeLastPoint = routeLast,
            routePoints = northRoute(),
            fallbackBearingDeg = 123.0,
            fromIndex = 0,
        )

        assertEquals(liveFix, result!!.target)
    }

    @Test
    fun `sin snapped ni liveFix cae al ultimo punto del track y sin ninguno da null`() {
        val routeLast = LatLon(BASE_LAT + 2.0, BASE_LON)

        val withRouteLast = NavCameraInputs.resolve(
            snapped = null,
            liveFix = null,
            routeLastPoint = routeLast,
            routePoints = northRoute(),
            fallbackBearingDeg = 123.0,
            fromIndex = 0,
        )
        val withNothing = NavCameraInputs.resolve(
            snapped = null,
            liveFix = null,
            routeLastPoint = null,
            routePoints = northRoute(),
            fallbackBearingDeg = 123.0,
            fromIndex = 0,
        )

        assertEquals(routeLast, withRouteLast!!.target)
        assertNull(withNothing)
    }

    @Test
    fun `con ruta planeada usa course-up y no el fallback`() {
        val route = northRoute()
        val target = route[3]

        val result = NavCameraInputs.resolve(
            snapped = target,
            liveFix = null,
            routeLastPoint = null,
            routePoints = route,
            fallbackBearingDeg = 270.0,
            fromIndex = 0,
        )

        assertEquals(0.0, result!!.bearingDeg, 1.0)
        assertEquals(3, result.nearestIndex)
    }

    @Test
    fun `sin ruta planeada usable usa el rumbo de fallback y conserva el hint`() {
        val target = LatLon(BASE_LAT, BASE_LON)

        val result = NavCameraInputs.resolve(
            snapped = target,
            liveFix = null,
            routeLastPoint = null,
            routePoints = emptyList(),
            fallbackBearingDeg = 270.0,
            fromIndex = 7,
        )

        assertEquals(270.0, result!!.bearingDeg, 0.001)
        assertEquals(7, result.nearestIndex)
    }

    @Test
    fun `encadenar el nearestIndex entre resolves sucesivos rastrea la posicion real en ruta auto-cruzada`() {
        // El mismo punto aparece en los indices 0 y 21 de una ruta que se aleja y vuelve. Si el
        // caller SIEMPRE encadena nearestIndex como el fromIndex del siguiente resolve (que es
        // lo que exige no congelar el hint aunque la cámara no anime), la ventana de búsqueda
        // avanza junto con la posición real y el segundo paso por el punto repetido elige el
        // índice 21, no vuelve a saltar al 0.
        val target = LatLon(BASE_LAT, BASE_LON)
        val route = selfCrossingRoute(target)

        val first = NavCameraInputs.resolve(
            snapped = target,
            liveFix = null,
            routeLastPoint = null,
            routePoints = route,
            fallbackBearingDeg = 0.0,
            fromIndex = 0,
        )
        val second = NavCameraInputs.resolve(
            snapped = route[15],
            liveFix = null,
            routeLastPoint = null,
            routePoints = route,
            fallbackBearingDeg = 0.0,
            fromIndex = first!!.nearestIndex,
        )
        val third = NavCameraInputs.resolve(
            snapped = target,
            liveFix = null,
            routeLastPoint = null,
            routePoints = route,
            fallbackBearingDeg = 0.0,
            fromIndex = second!!.nearestIndex,
        )

        assertEquals(0, first.nearestIndex)
        assertEquals(15, second.nearestIndex)
        assertEquals(21, third!!.nearestIndex)
    }

    /** Punto [target] repetido en los indices 0 y 21, separados por tramos lejanos entre si. */
    private fun selfCrossingRoute(target: LatLon): List<LatLon> =
        listOf(target) +
            (0 until 20).map { LatLon(10.0 + it * 0.01, 10.0) } +
            listOf(target) +
            (0 until 5).map { LatLon(20.0 + it * 0.01, 20.0) }

    private fun northRoute(): List<LatLon> =
        (0 until 10).map { LatLon(BASE_LAT + it * LAT_STEP_DEG, BASE_LON) }
}
