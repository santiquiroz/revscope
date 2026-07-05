package com.revscope.core.obd.telemetry

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTimerEngineTest {

    private fun speed(kmh: Double, atMs: Long) =
        ObdReading(pid = "0D", value = kmh, unit = "km/h", timestamp = atMs)

    private fun LaunchTimerEngine.feedStandstill(fromMs: Long): Long {
        process(speed(0.0, fromMs))
        process(speed(0.0, fromMs + 500))
        process(speed(0.0, fromMs + 1_000)) // >700 ms standstill → armed
        return fromMs + 1_000
    }

    @Test
    fun `full run reports 0-60 and 0-100`() = runTest {
        val engine = LaunchTimerEngine()
        var result: LaunchTimerEngine.LaunchResult? = null
        val job = launch { result = engine.results.first() }
        yield() // let the collector subscribe — SharedFlow(replay=0) drops otherwise

        var t = engine.feedStandstill(0L)
        // Constant acceleration: +20 km/h every 500 ms → 100 km/h at t+2500
        listOf(20.0, 40.0, 60.0, 80.0, 100.0).forEach { v ->
            t += 500
            engine.process(speed(v, t))
        }
        job.join()

        assertNotNull(result)
        // Launch clock starts at the last standstill sample (t=1000)
        assertEquals(2_500L, result!!.to100Ms!!, 60L.toDouble().toLong())
        assertNotNull(result!!.to60Ms)
        assertTrue(result!!.to60Ms!! < result!!.to100Ms!!)
    }

    @Test
    fun `aborted run before 60 reports nothing and re-arms`() = runTest {
        val engine = LaunchTimerEngine()
        var t = engine.feedStandstill(0L)
        engine.process(speed(30.0, t + 500))
        engine.process(speed(0.0, t + 1_000)) // stopped before 60
        assertEquals(LaunchTimerEngine.State.IDLE, engine.state.value)
    }

    @Test
    fun `aborted run after 60 reports partial result`() = runTest {
        val engine = LaunchTimerEngine()
        var result: LaunchTimerEngine.LaunchResult? = null
        val job = launch { result = engine.results.first() }
        yield() // let the collector subscribe — SharedFlow(replay=0) drops otherwise

        var t = engine.feedStandstill(0L)
        listOf(30.0, 65.0).forEach { v -> t += 500; engine.process(speed(v, t)) }
        engine.process(speed(0.0, t + 500)) // stopped before 100
        job.join()

        assertNotNull(result!!.to60Ms)
        assertNull(result!!.to100Ms)
    }

    @Test
    fun `does not arm while moving`() {
        val engine = LaunchTimerEngine()
        engine.process(speed(50.0, 0))
        engine.process(speed(60.0, 500))
        assertEquals(LaunchTimerEngine.State.IDLE, engine.state.value)
    }

    private fun assertEquals(expected: Long, actual: Long, tolerance: Long) {
        assertTrue(
            "expected $expected ±$tolerance but was $actual",
            kotlin.math.abs(expected - actual) <= tolerance,
        )
    }
}
