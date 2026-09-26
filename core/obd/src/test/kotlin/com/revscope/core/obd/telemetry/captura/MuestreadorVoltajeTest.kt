package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.session.DiagnosticLease
import com.revscope.core.obd.telemetry.PollingGate
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MuestreadorVoltajeTest {

    private class EnlaceFalso(private val bt: Transport?, private val scope: CoroutineScope) : EnlaceCaptura {
        val pausas = mutableListOf<Boolean>()
        val pausasVoltaje = mutableListOf<Boolean>()
        val publicadas = mutableListOf<ObdReading>()
        override fun transporte() = bt
        override fun scopeEnlace() = scope
        override fun pausarSondeo(pausado: Boolean) {
            pausas += pausado
        }
        override fun pausarVoltaje(pausado: Boolean) {
            pausasVoltaje += pausado
        }
        override fun publicar(reading: ObdReading) {
            publicadas += reading
        }
        override fun info() = InfoAdaptador("vLinker", "ELM327 v2.2", "5", esCan = false)
    }

    private class SumideroEnMemoria : SumideroCaptura {
        val lineas = mutableListOf<String>()
        var cerrado = false
        override fun abrir(lineasIniciales: List<String>) = escribir(lineasIniciales)
        override fun escribir(lineas: List<String>) {
            this.lineas += lineas
        }
        override fun volcar() = Unit
        override fun cerrar(): String? {
            cerrado = true
            return "/cache/exports/revscope-captura-rv.csv"
        }
    }

    // Un socket que se cierra: el transporte deja de estar conectado y cada intercambio falla.
    private class TransporteQueSeCae(private val inner: FakeElmTransport) : Transport by inner {
        @Volatile var caido = false
        override val isConnected: Boolean get() = !caido
        override suspend fun exchange(command: String, timeoutMs: Long): String {
            if (caido) throw IOException("socket cerrado")
            return inner.exchange(command, timeoutMs)
        }
    }

    private class Montaje(
        val muestreador: MuestreadorVoltaje,
        val captura: CapturaRapida,
        val enlace: EnlaceFalso,
        val sumidero: SumideroEnMemoria,
        val gate: PollingGate,
    )

    private val registry = PidRegistry(TestPids.load()).apply { setSupportedPids(setOf("0C", "0D", "05", "11")) }

    private fun TestScope.fake(atRv: () -> String = { "12.4V\r\r>" }): FakeElmTransport = FakeElmTransport(latenciaMs = { 20L }) { cmd ->
        when {
            cmd == "ATRV" -> atRv()
            cmd.startsWith("AT") -> "OK>"
            cmd.startsWith("010C") -> "410C0FA0>"
            cmd.startsWith("0105") -> "410550>"
            cmd == "03" -> "4300>"
            else -> "NO DATA>"
        }
    }

    private fun TestScope.montar(bt: Transport): Montaje {
        val enlace = EnlaceFalso(bt, backgroundScope)
        val sumidero = SumideroEnMemoria()
        val gate = PollingGate()
        val turno = TurnoCaptura()
        val relojNanos = { testScheduler.currentTime * 1_000_000 }
        val relojEpochMs = { 1_790_000_000_000L + testScheduler.currentTime }
        val io = StandardTestDispatcher(testScheduler)
        val muestreador = MuestreadorVoltaje(enlace, gate, turno, { sumidero }, relojNanos, relojEpochMs, io)
        val captura = CapturaRapida(
            enlace = enlace,
            gate = gate,
            registry = registry,
            nuevoSumidero = { SumideroEnMemoria() },
            dispositivo = { LecturaDispositivo(80, cargando = false, termico = 0) },
            relojNanos = relojNanos,
            relojEpochMs = relojEpochMs,
            io = io,
            turno = turno,
        )
        return Montaje(muestreador, captura, enlace, sumidero, gate)
    }

    private val rafaga = ConfigCaptura(listOf(MuestreadorVoltaje.PID), duracionMaxMs = 60_000)
    private val rpm = ConfigCaptura(listOf("0C"), duracionMaxMs = 60_000)

    @Test
    fun `repite AT RV sin pausa, publica VBAT y pausa el sondeo y el VoltagePoller hasta terminar`() = runTest {
        val fake = fake()
        val m = montar(fake)

        val inicio = m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(1_000)
        val durante = m.enlace.pausas.toList() to m.enlace.pausasVoltaje.toList()
        val resumen = m.muestreador.detener()!!
        val muestras = m.muestreador.muestrasActuales()

        assertEquals(listOf(true) to listOf(true), durante)
        assertEquals(listOf(true, false), m.enlace.pausas)
        assertEquals(listOf(true, false), m.enlace.pausasVoltaje)
        assertEquals(listOf(MuestreadorVoltaje.PID), inicio.pidsAceptados)
        assertTrue("unas 50 lecturas en 1 s a 20 ms cada una: ${muestras.size}", muestras.size in 45..52)
        assertTrue(fake.comandos.all { it == "ATRV" })
        assertTrue(muestras.all { it.pid == "VBAT" && it.valor == 12.4 })
        assertEquals(muestras.size, m.enlace.publicadas.count { it.pid == "VBAT" && it.unit == "V" })
        assertEquals(CapturaRapida.MOTIVO_USUARIO, resumen.motivoFin)
        assertEquals(12.4, resumen.porPid.single().media, 1e-9)
        assertEquals(2 + muestras.size, m.sumidero.lineas.size)
        assertTrue(m.sumidero.cerrado)
        assertEquals("/cache/exports/revscope-captura-rv.csv", resumen.rutaCsv)
        assertFalse(m.muestreador.activa())
    }

    @Test
    fun `el tiempo de cada muestra sale del reloj de la ráfaga, igual que transcurridoMs`() = runTest {
        val m = montar(fake())

        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(500)
        val transcurrido = m.muestreador.transcurridoMs()!!
        val ultima = m.muestreador.muestrasActuales().last()
        m.muestreador.detener()

        assertTrue("la última muestra (${ultima.tMicros / 1_000} ms) no pasa de $transcurrido ms", ultima.tMicros / 1_000 <= transcurrido)
        assertTrue(transcurrido - ultima.tMicros / 1_000 <= 40)
        assertNull(m.muestreador.transcurridoMs())
    }

    @Test
    fun `con la captura rápida activa la ráfaga no arranca, y arranca cuando la captura termina`() = runTest {
        val m = montar(fake())

        m.captura.iniciar(rpm).getOrThrow()
        advanceTimeBy(100)
        val rechazo = m.muestreador.iniciar(rafaga)
        m.captura.detener()
        val despues = m.muestreador.iniciar(rafaga)
        advanceTimeBy(100)
        m.muestreador.detener()

        assertTrue(rechazo.isFailure)
        assertTrue(rechazo.exceptionOrNull()!!.message!!, rechazo.exceptionOrNull()!!.message!!.contains("captura rápida"))
        assertTrue(despues.isSuccess)
        assertEquals(listOf(true, false, true, false), m.enlace.pausas)
    }

    @Test
    fun `con la ráfaga activa la captura rápida no arranca, y arranca cuando la ráfaga termina`() = runTest {
        val m = montar(fake())

        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(100)
        val rechazo = m.captura.iniciar(rpm)
        m.muestreador.detener()
        val despues = m.captura.iniciar(rpm)
        advanceTimeBy(100)
        m.captura.detener()

        assertTrue(rechazo.isFailure)
        assertTrue(rechazo.exceptionOrNull()!!.message!!, rechazo.exceptionOrNull()!!.message!!.contains("ráfaga de voltaje"))
        assertTrue(despues.isSuccess)
        assertEquals(false, m.enlace.pausas.last())
    }

    @Test
    fun `si el adaptador no responde a AT RV no arranca, reanuda el sondeo y suelta el turno`() = runTest {
        val m = montar(fake(atRv = { "?\r\r>" }))

        val r = m.muestreador.iniciar(rafaga)
        val captura = m.captura.iniciar(rpm)
        m.captura.detener()

        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!, r.exceptionOrNull()!!.message!!.contains("AT RV"))
        assertEquals(listOf(true, false), m.enlace.pausasVoltaje)
        assertTrue(captura.isSuccess)
        assertFalse(m.muestreador.activa())
    }

    @Test
    fun `diez AT RV seguidos sin respuesta terminan la ráfaga como enlace perdido`() = runTest {
        var respuestas = 0
        val m = montar(fake(atRv = { if (++respuestas <= 5) "12.4V>" else throw IOException("timeout") }))

        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(1_000)
        runCurrent()

        assertFalse(m.muestreador.activa())
        val resumen = m.muestreador.ultimoResumen.value!!
        assertEquals(MuestreadorVoltaje.MOTIVO_SIN_RESPUESTA, resumen.motivoFin)
        assertTrue(resumen.motivoFin.startsWith(CapturaRapida.MOTIVO_ENLACE))
        assertEquals(5, m.muestreador.muestrasActuales().size)
        assertEquals(listOf(true, false), m.enlace.pausas)
        assertEquals(listOf(true, false), m.enlace.pausasVoltaje)
    }

    @Test
    fun `unas pocas lecturas perdidas no cortan la ráfaga`() = runTest {
        var respuestas = 0
        val m = montar(fake(atRv = { if (++respuestas % 4 == 0) throw IOException("timeout") else "12.1V>" }))

        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(1_000)

        assertTrue(m.muestreador.activa())
        m.muestreador.detener()
    }

    @Test
    fun `un socket cerrado corta la ráfaga en seco como enlace perdido`() = runTest {
        val bt = TransporteQueSeCae(fake())
        val m = montar(bt)

        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(200)
        bt.caido = true
        advanceTimeBy(100)
        runCurrent()

        assertFalse(m.muestreador.activa())
        assertTrue(m.muestreador.ultimoResumen.value!!.motivoFin.startsWith(CapturaRapida.MOTIVO_ENLACE))
        assertEquals(false, m.enlace.pausas.last())
    }

    @Test
    fun `la duración máxima detiene la ráfaga con su motivo`() = runTest {
        val m = montar(fake())

        m.muestreador.iniciar(rafaga.copy(duracionMaxMs = 2_000)).getOrThrow()
        advanceTimeBy(3_000)
        runCurrent()

        assertFalse(m.muestreador.activa())
        assertEquals(MuestreadorVoltaje.MOTIVO_DURACION, m.muestreador.ultimoResumen.value!!.motivoFin)
    }

    @Test
    fun `una lectura de diagnóstico entra entre dos AT RV`() = runTest {
        val fake = fake()
        val m = montar(fake)
        m.muestreador.iniciar(rafaga).getOrThrow()
        advanceTimeBy(100)

        val lectura = DiagnosticLease(m.gate).run(fake, "mcp:get_dtc") { bt -> bt.exchange("03\r", 1_000) }
        advanceTimeBy(100)
        m.muestreador.detener()

        assertTrue(lectura.isSuccess)
        val i = fake.comandos.indexOf("03")
        assertEquals(listOf("ATRV", "03", "ATRV"), fake.comandos.subList(i - 1, i + 2))
    }
}
