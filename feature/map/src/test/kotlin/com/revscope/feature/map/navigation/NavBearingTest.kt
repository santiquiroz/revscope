package com.revscope.feature.map.navigation

import com.revscope.core.navigation.LatLon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val LAT_STEP_DEG = 0.0001 // ~11.1 m
private const val LON_STEP_DEG = 0.0001 // ~11.0 m a latitud ~6.2°
private const val BASE_LAT = 6.2000
private const val BASE_LON = -75.5800

class NavBearingTest {

    @Test
    fun `ruta recta sur a norte da rumbo norte desde cualquier punto`() {
        val route = northRoute(count = 10)

        listOf(0, 3, 8).forEach { index ->
            val result = NavBearing.courseUpBearing(route, route[index])
            assertEquals("desde el punto $index", 0.0, result!!.bearingDeg, 1.0)
        }
    }

    @Test
    fun `ruta recta oeste a este da rumbo este`() {
        val route = eastRoute(count = 10)

        val result = NavBearing.courseUpBearing(route, route[3])

        assertEquals(90.0, result!!.bearingDeg, 1.0)
    }

    @Test
    fun `en una curva de 90 grados el rumbo queda suavizado entre los dos tramos`() {
        // Parado 2 puntos antes del vertice (indice 20), con un lookahead de 40m que cruza
        // al tramo este: el rumbo del micro-segmento seria 0 (todavia yendo al norte), pero el
        // suavizado debe quedar estrictamente entre 0 (norte) y 90 (este).
        val vertexIndex = 20
        val route = lShapedRoute(vertexIndex)
        val position = route[vertexIndex - 2]

        val result = NavBearing.courseUpBearing(route, position, lookaheadM = 40.0)

        assertTrue("esperaba > 5 grados, fue ${result!!.bearingDeg}", result!!.bearingDeg > 5.0)
        assertTrue("esperaba < 85 grados, fue ${result.bearingDeg}", result.bearingDeg < 85.0)
    }

    @Test
    fun `posicion fuera de la ruta usa igual el punto mas cercano`() {
        val route = northRoute(count = 10)
        val offRoutePosition = route[5].copy(lon = route[5].lon + 0.0005)

        val result = NavBearing.courseUpBearing(route, offRoutePosition)

        assertEquals(5, result!!.nearestIndex)
        assertEquals(0.0, result.bearingDeg, 1.0)
    }

    @Test
    fun `con hint adelantado en ruta que se auto-cruza elige el match adelante, no el de atras`() {
        // El mismo punto aparece dos veces en la ruta (indices 0 y 21), separadas por tramos
        // lejanos. Sin hint, el escaneo lineal encuentra primero el de indice 0. Con un hint
        // avanzado, debe encontrar el de indice 21 en vez de saltar hacia atras.
        val target = LatLon(BASE_LAT, BASE_LON)
        val route = selfCrossingRoute(target)

        val withoutHint = NavBearing.courseUpBearing(route, target)
        val withHint = NavBearing.courseUpBearing(route, target, fromIndex = 15)

        assertEquals(0, withoutHint!!.nearestIndex)
        assertEquals(21, withHint!!.nearestIndex)
    }

    @Test
    fun `menos de 2 puntos da null`() {
        assertNull(NavBearing.courseUpBearing(emptyList(), LatLon(BASE_LAT, BASE_LON)))
        assertNull(NavBearing.courseUpBearing(listOf(LatLon(BASE_LAT, BASE_LON)), LatLon(BASE_LAT, BASE_LON)))
    }

    private fun northRoute(count: Int): List<LatLon> =
        (0 until count).map { LatLon(BASE_LAT + it * LAT_STEP_DEG, BASE_LON) }

    private fun eastRoute(count: Int): List<LatLon> =
        (0 until count).map { LatLon(BASE_LAT, BASE_LON + it * LON_STEP_DEG) }

    private fun lShapedRoute(vertexIndex: Int): List<LatLon> {
        val northLeg = (0..vertexIndex).map { LatLon(BASE_LAT + it * LAT_STEP_DEG, BASE_LON) }
        val vertexLat = northLeg.last().lat
        val eastLeg = (1..vertexIndex).map { LatLon(vertexLat, BASE_LON + it * LON_STEP_DEG) }
        return northLeg + eastLeg
    }

    /** Punto [target] repetido en los indices 0 y 21, separados por tramos lejanos entre si. */
    private fun selfCrossingRoute(target: LatLon): List<LatLon> =
        listOf(target) +
            (0 until 20).map { LatLon(10.0 + it * 0.01, 10.0) } +
            listOf(target) +
            (0 until 5).map { LatLon(20.0 + it * 0.01, 20.0) }
}
