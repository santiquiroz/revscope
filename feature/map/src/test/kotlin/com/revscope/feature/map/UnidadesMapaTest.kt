package com.revscope.feature.map

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.obd.social.RoomClient
import com.revscope.feature.map.navigation.navDistanceText
import com.revscope.feature.map.social.formatRemaining
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UnidadesMapaTest {

    private val metric = UnitFormatter(UnitSystem.METRIC, Locale("es"))
    private val us = UnitFormatter(UnitSystem.US, Locale.US)
    private val uk = UnitFormatter(UnitSystem.UK, Locale.UK)

    private fun route(distanceM: Double, durationS: Double) =
        NavigationRoute(points = emptyList(), distanceM = distanceM, durationS = durationS, steps = emptyList(), osrmRouteJson = "")

    @Test
    fun `en metrico el banner de navegacion conserva el redondeo a cincuenta metros`() {
        assertEquals("200 m", navDistanceText(237, metric))
        assertEquals("1,2 km", navDistanceText(1240, metric))
    }

    @Test
    fun `en US el banner de navegacion va en pies y millas`() {
        assertEquals("490 ft", navDistanceText(150, us))
        assertEquals("1.9 mi", navDistanceText(3000, us))
    }

    @Test
    fun `en US la distancia al radar va en pies o millas`() {
        assertEquals("390 ft", cameraDistanceText(120, us))
        assertEquals("0.2 mi", cameraDistanceText(300, us))
    }

    @Test
    fun `el limite del radar se convierte a un numero entero de mph`() {
        assertEquals(50, wholeSpeed(80.0, us))
        assertEquals(80, wholeSpeed(80.0, metric))
    }

    @Test
    fun `el resumen de ruta va en millas en US y UK`() {
        assertEquals("12 mi · 30 min", formatRouteSummary(route(20_000.0, 1_800.0), us))
        assertEquals("3.1 mi · 10 min", formatRouteSummary(route(5_000.0, 600.0), uk))
        assertEquals("5,0 km · 10 min", formatRouteSummary(route(5_000.0, 600.0), metric))
    }

    @Test
    fun `la etiqueta del peer en el mapa va en mph en US`() {
        val peer = RoomClient.Peer("Nico", 6.24, -75.58, speedKmh = 100.0, headingDeg = null, seenAtMs = 0L)
        assertEquals("Nico\n62 mph", peerLabel(peer, us))
    }

    @Test
    fun `lo que falta en el ranking va en millas en US y UK`() {
        assertEquals("3.1 mi", formatRemaining(5_000.0, us))
        assertEquals("3.1 mi", formatRemaining(5_000.0, uk))
        assertEquals("5,0 km", formatRemaining(5_000.0, metric))
    }
}
