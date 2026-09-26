package com.revscope.core.obd.session

import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.telemetry.GatedTransport
import com.revscope.core.obd.telemetry.PidScheduler
import com.revscope.core.obd.telemetry.PollingGate
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DiagnosticLeaseTest {

    private val diagnostico = setOf("0101", "03", "07", "0A")

    private fun fakeElm() = FakeElmTransport(latenciaMs = { 30L }) { comando ->
        when {
            comando.startsWith("01") && comando.length > 4 -> "410C1AF00D3C>"
            comando == "010C" -> "410C1AF0>"
            comando == "010D" -> "410D3C>"
            comando == "03" -> "43010133>"
            else -> "NO DATA>"
        }
    }

    private fun registry() = PidRegistry(TestPids.load()).apply { setSupportedPids(setOf("0C", "0D", "11", "05")) }

    @Test
    fun `durante la concesion no hay comandos de sondeo entre los del diagnostico`() = runTest {
        val fake = fakeElm()
        val gate = PollingGate()
        val lease = DiagnosticLease(gate)
        val polled = GatedTransport(fake, gate)
        val polling = launch { PidScheduler(polled, registry()).observeReadings().collect {} }
        val insistentes = List(2) { launch { while (true) polled.exchange("010D\r") } }
        advanceTimeBy(1_000)

        val resultado = lease.run(fake, "mcp:get_dtc") { bt -> diagnostico.forEach { bt.exchange("$it\r") } }
        advanceTimeBy(1_000)
        polling.cancel()
        insistentes.forEach { it.cancel() }

        assertTrue(resultado.isSuccess)
        val indices = fake.comandos.withIndex().filter { it.value in diagnostico }.map { it.index }
        assertEquals((indices.first()..indices.last()).toList(), indices)
        assertEquals(1, fake.maxConcurrencia)
    }

    @Test
    fun `el sondeo se reanuda tras la concesion`() = runTest {
        val fake = fakeElm()
        val gate = PollingGate()
        val lease = DiagnosticLease(gate)
        val polling = launch { PidScheduler(GatedTransport(fake, gate), registry()).observeReadings().collect {} }
        advanceTimeBy(500)

        lease.run(fake, "ui:dtc") { bt -> bt.exchange("03\r") }
        val despues = fake.comandos.size
        advanceTimeBy(1_000)
        runCurrent()
        polling.cancel()

        assertTrue(fake.comandos.size > despues + 3)
    }

    @Test
    fun `timeout del bloque devuelve failure y libera el canal`() = runTest {
        val fake = fakeElm()
        val gate = PollingGate()
        val lease = DiagnosticLease(gate)

        val resultado = lease.run(fake, "ui:dtc", timeoutMs = 1_000) { delay(5_000) }
        GatedTransport(fake, gate).exchange("010C\r")

        assertTrue(resultado.isFailure)
        assertEquals(listOf("010C"), fake.comandos)
    }

    @Test
    fun `cancelar el llamador reanuda el sondeo`() = runTest {
        val fake = fakeElm()
        val gate = PollingGate()
        val lease = DiagnosticLease(gate)

        val llamador = launch { lease.run(fake, "mcp:get_dtc") { delay(10_000) } }
        runCurrent()
        llamador.cancel()
        GatedTransport(fake, gate).exchange("010C\r")

        assertEquals(listOf("010C"), fake.comandos)
    }

    @Test
    fun `sin transporte devuelve failure sin tocar el canal`() = runTest {
        val resultado = DiagnosticLease(PollingGate()).run<Unit>(null, "ui:dtc") { error("no debe correr") }

        assertTrue(resultado.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `una IOException del enlace sale como failure`() = runTest {
        val fake = fakeElm().apply { fallarDespuesDe(0) }

        val resultado = DiagnosticLease(PollingGate()).run(fake, "ui:dtc") { bt -> bt.exchange("03\r") }

        assertTrue(resultado.exceptionOrNull() is IOException)
        assertTrue(resultado.exceptionOrNull() !is CancellationException)
    }
}
