package com.revscope.core.obd.telemetry

import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PollingGateTest {

    @Test
    fun `el sondeo espera mientras hay una concesion y sigue al terminar`() = runTest {
        val gate = PollingGate()
        val fake = FakeElmTransport { "OK>" }
        val gated = GatedTransport(fake, gate)

        val concesion = launch { gate.conceder("ui:dtc") { delay(1_000); fake.exchange("03\r") } }
        runCurrent()
        val sondeo = async { gated.exchange("010C\r") }
        advanceTimeBy(500)
        runCurrent()

        assertTrue(fake.comandos.isEmpty())
        assertFalse(sondeo.isCompleted)

        concesion.join()
        sondeo.await()
        assertEquals(listOf("03", "010C"), fake.comandos)
    }

    @Test
    fun `el dueno de la concesion se publica y se limpia al terminar`() = runTest {
        val gate = PollingGate()
        var visto: String? = null

        gate.conceder("mcp:get_dtc") { visto = gate.duenoConcesion.value }

        assertEquals("mcp:get_dtc", visto)
        assertNull(gate.duenoConcesion.value)
    }

    @Test
    fun `una excepcion dentro de la concesion libera el canal`() = runTest {
        val gate = PollingGate()
        val fake = FakeElmTransport { "OK>" }

        runCatching { gate.conceder("ui:dtc") { error("boom") } }
        GatedTransport(fake, gate).exchange("010C\r")

        assertEquals(listOf("010C"), fake.comandos)
        assertNull(gate.duenoConcesion.value)
    }
}
