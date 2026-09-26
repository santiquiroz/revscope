package com.revscope.core.obd.session

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoltagePollerTest {

    @Test
    fun `lee AT RV cada 10 s y lo publica como VBAT`() = runTest {
        val fake = FakeElmTransport { "12.6V>" }
        val lecturas = mutableListOf<ObdReading>()
        val poller = VoltagePoller()

        poller.start(backgroundScope, fake) { lecturas += it }
        advanceTimeBy(25_000)
        poller.stop()

        assertEquals(3, fake.comandos.count { it == "ATRV" })
        assertTrue(lecturas.all { it.pid == ObdSessionManager.VBAT_PID && it.value == 12.6 })
    }

    @Test
    fun `en pausa no pregunta y al reanudar vuelve a leer`() = runTest {
        val fake = FakeElmTransport { "12.6V>" }
        val poller = VoltagePoller()

        poller.start(backgroundScope, fake) {}
        advanceTimeBy(100)
        poller.pausado = true
        advanceTimeBy(30_000)
        val enPausa = fake.comandos.size
        poller.pausado = false
        advanceTimeBy(10_000)
        poller.stop()

        assertEquals(1, enPausa)
        assertEquals(2, fake.comandos.size)
    }
}
