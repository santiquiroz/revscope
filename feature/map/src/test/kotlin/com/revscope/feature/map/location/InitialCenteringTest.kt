package com.revscope.feature.map.location

import com.revscope.core.obd.service.LiveRouteHolder.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialCenteringTest {

    private val medellin = RoutePoint(6.2442, -75.5812)
    private val envigado = RoutePoint(6.1759, -75.5915)

    @Test
    fun `lastKnown centra una vez con zoom lejano`() {
        val c = InitialCentering()
        assertEquals(
            CenterAction(medellin.lat, medellin.lon, InitialCentering.IDLE_ZOOM),
            c.onLastKnown(medellin),
        )
        assertNull(c.onLastKnown(medellin))
    }

    @Test
    fun `lastKnown null no consume el estado`() {
        val c = InitialCentering()
        assertNull(c.onLastKnown(null))
        val action = c.onLastKnown(medellin)
        assertEquals(
            CenterAction(medellin.lat, medellin.lon, InitialCentering.IDLE_ZOOM),
            action,
        )
    }

    @Test
    fun `primer fix vivo recentra una vez con zoom cercano`() {
        val c = InitialCentering()
        c.onLastKnown(medellin)
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            c.onLiveFix(envigado),
        )
        assertNull(c.onLiveFix(envigado))
    }

    @Test
    fun `fix vivo funciona aunque nunca hubo lastKnown`() {
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            InitialCentering().onLiveFix(envigado),
        )
    }

    @Test
    fun `tras el fix vivo el lastKnown tardio ya no centra`() {
        val c = InitialCentering()
        c.onLiveFix(envigado)
        assertNull(c.onLastKnown(medellin))
    }

    @Test
    fun `pan del usuario cancela todo centrado futuro`() {
        val c = InitialCentering()
        c.onUserPan()
        assertNull(c.onLastKnown(medellin))
        assertNull(c.onLiveFix(envigado))
    }

    @Test
    fun `fix null no consume el centrado`() {
        val c = InitialCentering()
        assertNull(c.onLiveFix(null))
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            c.onLiveFix(envigado),
        )
    }

    @Test
    fun `pan entre lastKnown y liveFix cancela el liveFix`() {
        val c = InitialCentering()
        c.onLastKnown(medellin)
        c.onUserPan()
        assertNull(c.onLiveFix(envigado))
    }
}
