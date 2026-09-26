package com.revscope.core.obd.mcp

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class McpActivityTrackerTest {

    @Test
    fun `una llamada activa al espectador durante 60 s y luego lo apaga`() = runTest {
        val tracker = McpActivityTracker { testScheduler.currentTime }
        val estados = mutableListOf<Boolean>()
        val job = launch { tracker.espectadorActivo.toList(estados) }
        runCurrent()

        tracker.registrarLlamada()
        advanceTimeBy(59_000)
        runCurrent()
        val aLos59s = estados.last()
        advanceTimeBy(2_000)
        runCurrent()
        job.cancel()

        assertEquals(true, aLos59s)
        assertEquals(listOf(false, true, false), estados)
    }

    @Test
    fun `llamadas seguidas extienden la ventana`() = runTest {
        val tracker = McpActivityTracker { testScheduler.currentTime }
        val estados = mutableListOf<Boolean>()
        val job = launch { tracker.espectadorActivo.toList(estados) }
        runCurrent()

        tracker.registrarLlamada()
        advanceTimeBy(50_000)
        tracker.registrarLlamada()
        advanceTimeBy(50_000)
        runCurrent()
        job.cancel()

        assertEquals(listOf(false, true), estados)
    }
}
