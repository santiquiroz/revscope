package com.revscope.core.obd.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GhostRaceEngineTest {

    // Recta hacia el norte: ~111 m por 0.001° de latitud; fantasma a 10 m/s
    private fun straightGhost(): List<GhostRaceEngine.GhostPoint> = (0..10).map { i ->
        GhostRaceEngine.GhostPoint(lat = 6.0 + i * 0.001, lon = -75.0, tRelMs = i * 11_100L)
    }

    @Test
    fun `sin fantasma el delta es null`() {
        val engine = GhostRaceEngine()
        engine.onLapStart()
        assertNull(engine.onFix(6.0, -75.0, 1_000L))
    }

    @Test
    fun `misma velocidad que el fantasma da delta cercano a cero`() {
        val engine = GhostRaceEngine()
        engine.setGhost(straightGhost())
        engine.onLapStart()
        var delta: Long? = null
        // recorro los mismos puntos con los mismos tiempos
        straightGhost().forEach { p -> delta = engine.onFix(p.lat, p.lon, p.tRelMs) }
        assertTrue("delta=$delta", abs(delta!!) < 500L)
    }

    @Test
    fun `ir mas lento que el fantasma da delta positivo`() {
        val engine = GhostRaceEngine()
        engine.setGhost(straightGhost())
        engine.onLapStart()
        var delta: Long? = null
        // mismos puntos pero tardando el doble
        straightGhost().forEach { p -> delta = engine.onFix(p.lat, p.lon, p.tRelMs * 2) }
        assertTrue("delta=$delta", delta!! > 50_000L)
    }

    @Test
    fun `onLapStart reinicia el acumulado de distancia`() {
        val engine = GhostRaceEngine()
        engine.setGhost(straightGhost())
        engine.onLapStart()
        engine.onFix(6.0, -75.0, 0L)
        engine.onFix(6.005, -75.0, 55_500L)
        engine.onLapStart()
        // tras el reinicio, el primer fix parte de distancia 0 → tiempo fantasma 0
        val delta = engine.onFix(6.0, -75.0, 0L)
        assertEquals(0L, delta)
    }
}
