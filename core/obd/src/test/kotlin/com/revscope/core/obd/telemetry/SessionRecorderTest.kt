package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.obd.model.ObdReading
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRecorderTest {

    private val guardados = mutableListOf<TelemetryPointEntity>()
    private val dao = mockk<TelemetryDao>().also { dao ->
        coEvery { dao.insertAll(any()) } answers { guardados += firstArg<List<TelemetryPointEntity>>() }
    }

    private fun lecturas(pid: String, cadaMs: Long, n: Int, valor: (Int) -> Double) =
        (0 until n).map { i -> ObdReading(pid, valor(i), "%", timestamp = 1_000_000L + i * cadaMs) }

    @Test
    fun `a 25 hz guarda como mucho 10 filas por segundo por pid`() = runTest {
        SessionRecorder(dao).record(1L, lecturas("49", cadaMs = 40, n = 25) { it.toDouble() }.asFlow())

        assertTrue("guardó ${guardados.size}", guardados.size in 8..10)
    }

    @Test
    fun `a 10 hz con valores distintos guarda todas`() = runTest {
        SessionRecorder(dao).record(1L, lecturas("0C", cadaMs = 100, n = 20) { 800.0 + it }.asFlow())

        assertEquals(20, guardados.size)
    }

    @Test
    fun `valor constante guarda el latido cada 4 s`() = runTest {
        SessionRecorder(dao).record(1L, lecturas("05", cadaMs = 500, n = 21) { 90.0 }.asFlow())

        assertEquals(listOf(0L, 4_000L, 8_000L), guardados.map { it.timestamp - 1_000_000L })
    }
}
