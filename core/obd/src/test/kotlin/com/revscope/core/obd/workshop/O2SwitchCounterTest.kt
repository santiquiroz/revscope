package com.revscope.core.obd.workshop

import org.junit.Assert.assertEquals
import org.junit.Test

class O2SwitchCounterTest {

    @Test
    fun `returns zero with fewer than two samples`() {
        assertEquals(0.0, O2SwitchCounter.perMinute(listOf(0L to 0.5)), 0.001)
        assertEquals(0.0, O2SwitchCounter.perMinute(emptyList()), 0.001)
    }

    @Test
    fun `returns zero when voltage never crosses the threshold`() {
        val samples = (0..10).map { i -> (i * 1000L) to 0.8 }
        assertEquals(0.0, O2SwitchCounter.perMinute(samples), 0.001)
    }

    @Test
    fun `counts crossings and normalizes to per minute`() {
        // Oscillates above/below 0.45V every second across a 10 s window -> 10 crossings / 10s = 60/min
        val samples = (0..10).map { i -> (i * 1000L) to if (i % 2 == 0) 0.8 else 0.1 }
        assertEquals(60.0, O2SwitchCounter.perMinute(samples), 0.5)
    }

    @Test
    fun `healthy switching sensor exceeds the 8 crossings per minute rule of thumb`() {
        val samples = (0..20).map { i -> (i * 1000L) to if (i % 2 == 0) 0.75 else 0.15 }
        assertEquals(true, O2SwitchCounter.perMinute(samples) > 8.0)
    }
}
