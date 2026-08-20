package com.revscope.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteScoringTest {

    private val origin = LatLon(6.2000, -75.5800)
    private val dest = LatLon(6.2010, -75.5800)

    // Mismo par de referencia que TripStatsCalculatorTest: 0.001° de latitud (misma longitud)
    // da una haversine ≈111.19 m — el caso puro de "línea recta" para probar la sinuosidad.
    private val straightLineM = 111.19

    @Test
    fun `sinuosidad de una ruta recta es aproximadamente 1`() {
        val sinuosity = RouteScoring.sinuosity(straightLineM, origin.lat, origin.lon, dest.lat, dest.lon)

        assertEquals(1.0, sinuosity, 0.01)
    }

    @Test
    fun `sinuosidad crece proporcional a la distancia recorrida de mas`() {
        val sinuosity = RouteScoring.sinuosity(straightLineM * 2, origin.lat, origin.lon, dest.lat, dest.lon)

        assertEquals(2.0, sinuosity, 0.01)
    }

    @Test
    fun `la ruta de menor duracion queda Rapida`() {
        val labels = RouteScoring.labelAlternatives(listOf(rapida(), curva(), alternativa()))

        assertEquals("Rápida", labels[0])
    }

    @Test
    fun `la mas sinuosa por encima del umbral queda Curvas`() {
        val labels = RouteScoring.labelAlternatives(listOf(rapida(), curva(), alternativa()))

        assertEquals("Curvas", labels[1])
    }

    @Test
    fun `la restante sin cruzar el umbral queda Alt`() {
        val labels = RouteScoring.labelAlternatives(listOf(rapida(), curva(), alternativa()))

        assertEquals("Alt", labels[2])
    }

    @Test
    fun `si la mas rapida tambien es la mas sinuosa se queda Rapida`() {
        // La más sinuosa de todas coincide con la de menor duración: Rápida manda, Curvas no
        // se reasigna a otra que no cruce el umbral.
        val muyRapidaYCurva = syntheticRoute(distanceM = straightLineM * 3, durationS = 200.0)
        val labels = RouteScoring.labelAlternatives(listOf(muyRapidaYCurva, alternativa()))

        assertEquals(listOf("Rápida", "Alt"), labels)
    }

    @Test
    fun `una sola ruta no tiene con que compararse y queda Rapida`() {
        val labels = RouteScoring.labelAlternatives(listOf(rapida()))

        assertEquals(listOf("Rápida"), labels)
    }

    @Test
    fun `sin rutas no hay nada que etiquetar`() {
        assertTrue(RouteScoring.labelAlternatives(emptyList()).isEmpty())
    }

    private fun rapida() = syntheticRoute(distanceM = straightLineM, durationS = 300.0)
    private fun curva() = syntheticRoute(distanceM = straightLineM * 1.8, durationS = 500.0)
    private fun alternativa() = syntheticRoute(distanceM = straightLineM * 1.08, durationS = 450.0)

    private fun syntheticRoute(distanceM: Double, durationS: Double) = NavigationRoute(
        points = listOf(origin, dest),
        distanceM = distanceM,
        durationS = durationS,
        steps = emptyList(),
        osrmRouteJson = "",
    )
}
