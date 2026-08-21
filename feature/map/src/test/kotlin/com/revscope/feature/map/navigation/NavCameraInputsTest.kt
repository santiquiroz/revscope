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

    private fun northRoute(): List<LatLon> =
        (0 until 10).map { LatLon(BASE_LAT + it * LAT_STEP_DEG, BASE_LON) }
}
