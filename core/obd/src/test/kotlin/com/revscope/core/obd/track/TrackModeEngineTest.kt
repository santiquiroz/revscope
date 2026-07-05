package com.revscope.core.obd.track

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates a vehicle driving north across a finish line armed at the origin.
 * 0.00001° latitude ≈ 1.105 m.
 */
class TrackModeEngineTest {

    private fun degNorth(meters: Double) = meters / 110_540.0

    /** Feeds two northbound fixes so the engine has a heading, then arms the line. */
    private fun TrackModeEngine.armAtOriginHeadingNorth(startMs: Long): Long {
        onGpsFix(0.0 - degNorth(10.0), 0.0, startMs)
        onGpsFix(0.0, 0.0, startMs + 1_000)
        assertTrue(armFinishLine())
        return startMs + 1_000
    }

    private fun TrackModeEngine.driveNorthMeters(fromLat: Double, atMs: Long, meters: Double): Double {
        val newLat = fromLat + degNorth(meters)
        onGpsFix(newLat, 0.0, atMs)
        return newLat
    }

    @Test
    fun `two crossings produce one lap with interpolated time`() {
        val engine = TrackModeEngine()
        var t = engine.armAtOriginHeadingNorth(0L)

        // Drive away north, loop back south of the line, cross north again
        var lat = 0.0
        lat = engine.driveNorthMeters(lat, t + 1_000, 50.0)      // first crossing = lap start
        engine.onGpsFix(0.0 - degNorth(20.0), 0.0, t + 60_000)   // teleport south (no north crossing)
        engine.onGpsFix(0.0 + degNorth(20.0), 0.0, t + 61_000)   // crosses north → lap!

        val state = engine.state.value
        assertEquals(1, state.laps.size)
        // Lap start interpolated inside segment t+0..t+1000 (line at origin, moving 0→50m north
        // from lat=0 → crossing at t itself); lap end inside t+60000..t+61000 at the midpoint.
        val lap = state.laps.first()
        assertTrue("lap time ${lap.timeMs} out of range", lap.timeMs in 59_000..61_500)
        assertEquals(lap.timeMs, state.bestLapMs)
    }

    @Test
    fun `southbound crossing does not count`() {
        val engine = TrackModeEngine()
        val t = engine.armAtOriginHeadingNorth(0L)

        engine.driveNorthMeters(0.0, t + 1_000, 50.0)            // lap start (north)
        engine.onGpsFix(0.0 + degNorth(20.0), 0.0, t + 30_000)
        engine.onGpsFix(0.0 - degNorth(20.0), 0.0, t + 31_000)   // crosses SOUTH — ignored

        assertEquals(0, engine.state.value.laps.size)
        assertTrue(engine.state.value.lapInProgress)
    }

    @Test
    fun `passing beside the line does not count`() {
        val engine = TrackModeEngine()
        val t = engine.armAtOriginHeadingNorth(0L)
        engine.driveNorthMeters(0.0, t + 1_000, 50.0)            // lap start

        // Cross the line's latitude but 50 m to the east — outside the 15 m half-width
        val eastOffset = 50.0 / (111_320.0)                       // ≈50 m of longitude at equator
        engine.onGpsFix(0.0 - degNorth(20.0), eastOffset, t + 30_000)
        engine.onGpsFix(0.0 + degNorth(20.0), eastOffset, t + 31_000)

        assertEquals(0, engine.state.value.laps.size)
    }

    @Test
    fun `cannot arm without movement heading`() {
        val engine = TrackModeEngine()
        engine.onGpsFix(0.0, 0.0, 0L)
        assertFalse(engine.armFinishLine())
    }

    @Test
    fun `clear resets laps and line`() {
        val engine = TrackModeEngine()
        val t = engine.armAtOriginHeadingNorth(0L)
        engine.driveNorthMeters(0.0, t + 1_000, 50.0)
        engine.clear()
        val state = engine.state.value
        assertFalse(state.finishLineSet)
        assertTrue(state.laps.isEmpty())
    }
}
