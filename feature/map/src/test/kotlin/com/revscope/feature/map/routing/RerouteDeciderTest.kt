package com.revscope.feature.map.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteDeciderTest {

    @Test
    fun `en ruta nunca dispara`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 0L))
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 60_000L))
    }

    @Test
    fun `desvio breve no dispara — exige sostenido`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 0L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 2_000L))
    }

    @Test
    fun `desvio sostenido 3s dispara una vez`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 0L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 3_000L))
        // Inmediatamente después no repite: cooldown de 10 s.
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 3_500L))
    }

    @Test
    fun `sigue desviado tras cooldown — reintenta`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 3_000L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 12_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 13_100L))
    }

    @Test
    fun `volver a ruta resetea el sostenido`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 2_000L))
        // Nuevo desvío arranca la cuenta de cero.
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 2_500L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 4_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 5_500L))
    }

    @Test
    fun `reset limpia todo`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        decider.shouldReroute(offRoute = true, nowMs = 3_000L)
        decider.reset()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 20_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 23_000L))
    }
}
