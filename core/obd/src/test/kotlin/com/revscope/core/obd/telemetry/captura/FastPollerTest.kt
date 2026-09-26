package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.telemetry.PollingGate
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FastPollerTest {

    private val registry = PidRegistry(TestPids.load())

    private fun defs(vararg pids: String): List<PidDefinition> = pids.map { registry.getDefinition(it)!! }

    private fun TestScope.poller(fake: FakeElmTransport, esCan: Boolean? = true) = FastPoller(
        exchange = { cmd -> fake.exchange(cmd, 1_000) },
        registry = registry,
        relojNanos = { testScheduler.currentTime * 1_000_000 },
        relojEpochMs = { 1_790_000_000_000L + testScheduler.currentTime },
        esCan = esCan,
    )

    private fun TestScope.correrPor(poller: FastPoller, defs: List<PidDefinition>, ms: Long): List<LoteRapido> {
        val lotes = mutableListOf<LoteRapido>()
        val job = launch { poller.correr(defs, emptyList(), {}) { lotes += it } }
        advanceTimeBy(ms)
        job.cancel()
        return lotes
    }

    @Test
    fun `49 4A y 11 van en una sola peticion con sufijo 1`() = runTest {
        val fake = FakeElmTransport(latenciaMs = { 20L }) { "41492E4A171180>" }

        val lotes = correrPor(poller(fake), defs("49", "4A", "11"), 100)

        assertEquals("01494A111", fake.comandos.first())
        val primero = lotes.first()
        assertEquals(listOf("49", "4A", "11"), primero.lecturas.map { it.pid })
        assertEquals(18.04, primero.lecturas[0].value, 0.01)
        assertEquals(20_000L, primero.latenciaMicros)
        assertEquals(10_000L, primero.tMicros)
    }

    @Test
    fun `0C mas tres pids de un byte se reparten en dos lotes`() = runTest {
        val lotes = poller(FakeElmTransport { ">" }).lotesPara(defs("0C", "49", "4A", "11"))

        assertEquals(listOf(listOf("0C", "49"), listOf("4A", "11")), lotes.map { l -> l.map { it.pid } })
    }

    @Test
    fun `sufijo rechazado se desactiva y la captura sigue`() = runTest {
        val fake = FakeElmTransport { cmd -> if (cmd.length % 2 == 1) "?>" else "41492E>" }
        val poller = poller(fake)

        val lotes = correrPor(poller, defs("49"), 100)

        assertEquals(listOf("01491", "0149"), fake.comandos.take(2))
        assertFalse(TecnicaCaptura.SUFIJO_1 in poller.tecnicas())
        assertTrue(lotes.all { it.falla == null && it.lecturas.size == 1 })
    }

    @Test
    fun `adaptador que rechaza varios pids pasa a uno por uno`() = runTest {
        val fake = FakeElmTransport { cmd ->
            when (cmd) {
                "01494A1" -> "NO DATA>"
                "01491" -> "41492E>"
                "014A1" -> "414A17>"
                else -> "?>"
            }
        }
        val poller = poller(fake)
        val respuestas = mutableListOf<String>()

        correrPor(poller, defs("49", "4A"), 200)
        respuestas += fake.comandos

        assertEquals("01494A1", respuestas.first())
        assertTrue("sigue con peticiones sueltas: $respuestas", "01491" in respuestas && "014A1" in respuestas)
    }

    @Test
    fun `k-line no usa multi-pid`() = runTest {
        val fake = FakeElmTransport { cmd -> if (cmd.startsWith("0149")) "41492E>" else "414A17>" }
        val poller = poller(fake, esCan = false)

        correrPor(poller, defs("49", "4A"), 100)

        assertEquals(listOf("01491", "014A1"), fake.comandos.take(2))
        assertFalse(TecnicaCaptura.MULTI_PID in poller.tecnicas())
    }

    @Test
    fun `tres fallas seguidas terminan en IOException`() = runTest {
        val fake = FakeElmTransport { "41492E>" }.apply { fallarDespuesDe(0) }

        val resultado = runCatching { poller(fake).correr(defs("49"), emptyList(), {}) {} }

        assertTrue(resultado.exceptionOrNull() is IOException)
    }

    @Test
    fun `BUFFER FULL frena la captura`() = runTest {
        val sano = correrPor(poller(FakeElmTransport(latenciaMs = { 20L }) { "41492E>" }), defs("49"), 1_000)
        val lleno = correrPor(poller(FakeElmTransport(latenciaMs = { 20L }) { "BUFFER FULL>" }), defs("49"), 1_000)

        assertEquals("una petición cada 20 ms", 49, sano.size)
        assertTrue("con BUFFER FULL pide mucho menos: ${lleno.size}", lleno.size < sano.size / 3)
    }

    @Test
    fun `limite de hz espacia los ciclos`() = runTest {
        val poller = poller(FakeElmTransport(latenciaMs = { 20L }) { "41492E>" }).apply { maxHz = 10 }

        val lotes = correrPor(poller, defs("49"), 1_000)

        assertEquals(10, lotes.size)
    }

    @Test
    fun `la guardia de refrigerante sale cada 5 s`() = runTest {
        val fake = FakeElmTransport(latenciaMs = { 20L }) { cmd -> if (cmd == "01051") "410550>" else "41492E>" }
        val guardias = mutableListOf<Double>()
        val job = launch {
            poller(fake).correr(defs("49"), listOf(registry.getDefinition("05")!! to 5_000L), { guardias += it.value }) {}
        }
        advanceTimeBy(10_500)
        job.cancel()

        assertEquals(listOf(40.0, 40.0, 40.0), guardias)
    }

    @Test
    fun `una concesion de 6 s no desplaza la marca de tiempo ni infla la latencia`() = runTest {
        val gate = PollingGate()
        val fake = FakeElmTransport(
            latenciaMs = { cmd -> if (cmd == "03") 6_000L else 20L },
            reloj = { testScheduler.currentTime },
        ) { cmd -> if (cmd == "03") "4300>" else "41492E>" }
        val poller = FastPoller(
            exchange = { cmd -> fake.exchange(cmd, 1_000) },
            registry = registry,
            relojNanos = { testScheduler.currentTime * 1_000_000 },
            relojEpochMs = { 1_790_000_000_000L + testScheduler.currentTime },
            esCan = true,
            enCanal = { bloque -> gate.sondear { bloque() } },
        )
        val lotes = mutableListOf<LoteRapido>()
        val captura = launch { poller.correr(defs("49"), emptyList(), {}) { lotes += it } }
        advanceTimeBy(5)
        val dtc = launch { gate.conceder("dtc") { fake.exchange("03", 10_000) } }
        advanceTimeBy(6_200)
        captura.cancel()
        dtc.join()

        val reales = fake.log.filter { it.comando != "03" }
        val despuesDelDtc = lotes.zip(reales).first { (_, real) -> real.tInicioMs >= 6_000L }
        val (lote, real) = despuesDelDtc
        assertEquals("latencia = solo el intercambio", 20_000L, lote.latenciaMicros)
        assertEquals("marca en el punto medio del intercambio real", (real.tInicioMs + 10) * 1_000, lote.tMicros)
        assertEquals(1_790_000_000_000L + real.tInicioMs + 10, lote.lecturas.single().timestamp)
        assertTrue(lotes.zip(reales).all { (l, r) -> kotlin.math.abs(l.tMicros / 1_000 - (r.tInicioMs + 10)) <= 10 })
    }
}
