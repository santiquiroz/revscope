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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturaRapidaTest {

    private val registry = PidRegistry(TestPids.load()).apply { setSupportedPids(setOf("0C", "0D", "05", "11", "49", "4A")) }

    private class EnlaceFalso(
        private val bt: Transport?,
        private val scope: CoroutineScope,
        private val dpn: String = "6",
    ) : EnlaceCaptura {
        val pausas = mutableListOf<Boolean>()
        val publicadas = mutableListOf<ObdReading>()
        override fun transporte() = bt
        override fun scopeEnlace() = scope
        override fun pausarSondeo(pausado: Boolean) {
            pausas += pausado
        }
        override fun publicar(reading: ObdReading) {
            publicadas += reading
        }
        override fun info() = InfoAdaptador("Vlink", "ELM327 v2.2", dpn, esCan = true)
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
            return "/cache/exports/revscope-captura-test.csv"
        }
    }

    private fun TestScope.fake(): FakeElmTransport = FakeElmTransport(latenciaMs = { 20L }) { cmd ->
        when {
            cmd.startsWith("AT") -> "OK>"
            cmd.startsWith("01494A11") -> "41492E4A171180>"
            cmd.startsWith("0105") -> "410550>"
            cmd.startsWith("010D") -> "410D00>"
            cmd.startsWith("0111") -> "41110C>"
            cmd == "03" -> "4300>"
            else -> "NO DATA>"
        }
    }

    private class Montaje(
        val captura: CapturaRapida,
        val enlace: EnlaceFalso,
        val sumidero: SumideroEnMemoria,
        val gate: PollingGate,
    )

    private fun TestScope.montar(
        fake: FakeElmTransport?,
        dispositivo: LecturaDispositivo = LecturaDispositivo(80, cargando = false, termico = 0),
        dpn: String = "6",
    ): Montaje {
        val enlace = EnlaceFalso(fake, backgroundScope, dpn)
        val sumidero = SumideroEnMemoria()
        val gate = PollingGate()
        val captura = CapturaRapida(
            enlace = enlace,
            gate = gate,
            registry = registry,
            nuevoSumidero = { sumidero },
            dispositivo = { dispositivo },
            relojNanos = { testScheduler.currentTime * 1_000_000 },
            relojEpochMs = { 1_790_000_000_000L + testScheduler.currentTime },
            io = StandardTestDispatcher(testScheduler),
        )
        return Montaje(captura, enlace, sumidero, gate)
    }

    private val pedal = ConfigCaptura(listOf("49", "4A", "11"), duracionMaxMs = 60_000)

    @Test
    fun `pedal y mariposa salen en una peticion con sufijo y la tasa medida ronda 1000 sobre la latencia`() = runTest {
        val fake = fake()
        val m = montar(fake)

        val inicio = m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(3_100)
        runCurrent()
        val stats = m.captura.estadisticas.value
        m.captura.detener()

        assertEquals(listOf(listOf("49", "4A", "11")), inicio.lotes)
        assertTrue(fake.comandos.count { it == "01494A111" } > 100)
        assertNotNull(stats)
        assertEquals(50.0, stats!!.hzPorPid.getValue("49"), 5.0)
        assertTrue(TecnicaCaptura.DIRECCION_FISICA in inicio.tecnicas)
        assertTrue(TecnicaCaptura.SUFIJO_1 in inicio.tecnicas)
    }

    @Test
    fun `pausa el sondeo normal y al detener revierte el afinado y lo reanuda`() = runTest {
        val fake = fake()
        val m = montar(fake)

        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(500)
        val resumen = m.captura.detener()

        assertEquals(listOf(true, false), m.enlace.pausas)
        assertEquals(listOf("ATCRA", "ATSH7DF", "ATAT1"), fake.comandos.takeLast(3))
        assertEquals(listOf("ATSH7E0", "ATCRA7E8", "ATAT2"), fake.comandos.take(3))
        assertEquals(CapturaRapida.MOTIVO_USUARIO, resumen!!.motivoFin)
        assertEquals(setOf("49", "4A", "11"), resumen.porPid.map { it.pid }.toSet())
        assertEquals("/cache/exports/revscope-captura-test.csv", resumen.rutaCsv)
        assertTrue(m.sumidero.cerrado)
        assertEquals(EstadoCaptura.Inactiva, m.captura.estado.value)
    }

    @Test
    fun `las muestras llegan al flujo del enlace, al anillo y al csv`() = runTest {
        val m = montar(fake())

        val inicio = m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(210)
        m.captura.detener()
        val pagina = m.captura.pagina(inicio.id, 0, 1_000, pids = null)!!

        val lotes = pagina.muestras.map { it.lote }.distinct()
        assertTrue("al menos 8 lotes en 210 ms a 20 ms por petición: ${lotes.size}", lotes.size >= 8)
        assertEquals(3 * lotes.size, pagina.muestras.size)
        assertEquals(pagina.muestras.size, m.enlace.publicadas.count { it.pid in setOf("49", "4A", "11") })
        assertTrue("la guardia de refrigerante también se publica", m.enlace.publicadas.any { it.pid == "05" })
        assertEquals("epoch_ms,t_ms,pid,nombre,valor,unidad,lote,latencia_ms", m.sumidero.lineas[1])
        assertEquals(2 + pagina.muestras.size, m.sumidero.lineas.size)
    }

    @Test
    fun `una lectura de diagnostico durante la captura ve el header funcional`() = runTest {
        val fake = fake()
        val m = montar(fake)
        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(100)

        val lectura = DiagnosticLease(m.gate).run(fake, "mcp:get_dtc") { bt -> bt.exchange("03\r", 1_000) }
        advanceTimeBy(100)
        m.captura.detener()

        assertTrue(lectura.isSuccess)
        val i = fake.comandos.indexOf("03")
        assertEquals(listOf("ATCRA", "ATSH7DF", "03", "ATSH7E0", "ATCRA7E8"), fake.comandos.subList(i - 2, i + 3))
    }

    @Test
    fun `el tiempo maximo detiene la captura con su motivo`() = runTest {
        val m = montar(fake())

        m.captura.iniciar(pedal.copy(duracionMaxMs = 2_000)).getOrThrow()
        advanceTimeBy(3_000)
        runCurrent()

        assertFalse(m.captura.activa())
        assertEquals("tiempo máximo", m.captura.ultimoResumen.value!!.motivoFin)
        assertEquals(false, m.enlace.pausas.last())
    }

    @Test
    fun `bateria baja sin cargar detiene la captura`() = runTest {
        val m = montar(fake(), dispositivo = LecturaDispositivo(10, cargando = false, termico = 0))

        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(1_500)
        runCurrent()

        assertFalse(m.captura.activa())
        assertTrue(m.captura.ultimoResumen.value!!.motivoFin.contains("batería"))
    }

    @Test
    fun `calor moderado limita a 10 hz`() = runTest {
        val m = montar(fake(), dispositivo = LecturaDispositivo(80, cargando = false, termico = 2))

        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(1_100)
        val limitado = m.captura.estado.value
        val antes = m.captura.muestrasActuales().size
        advanceTimeBy(1_000)
        val porSegundo = (m.captura.muestrasActuales().size - antes) / 3
        m.captura.detener()

        assertEquals(10, (limitado as EstadoCaptura.Activa).limiteHz)
        assertEquals(10.0, porSegundo.toDouble(), 1.0)
    }

    @Test
    fun `pids no soportados se informan y sin ninguno soportado no arranca`() = runTest {
        val m = montar(fake())

        val inicio = m.captura.iniciar(ConfigCaptura(listOf("49", "4b", "ZZ"), 60_000)).getOrThrow()
        m.captura.detener()
        val ninguno = m.captura.iniciar(ConfigCaptura(listOf("4B"), 60_000))

        assertEquals(listOf("49"), inicio.pidsAceptados)
        assertEquals(listOf("4B", "ZZ"), inicio.pidsNoSoportados)
        assertTrue(ninguno.isFailure)
    }

    @Test
    fun `una sola captura a la vez y sin enlace no arranca`() = runTest {
        val m = montar(fake())
        m.captura.iniciar(pedal).getOrThrow()

        val segunda = m.captura.iniciar(pedal)
        m.captura.detener()
        val sinEnlace = montar(null).captura.iniciar(pedal)

        assertTrue(segunda.isFailure)
        assertTrue(sinEnlace.isFailure)
    }

    @Test
    fun `enlace muerto termina la captura, revierte y reanuda el sondeo`() = runTest {
        val fake = fake()
        val m = montar(fake)
        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(100)

        fake.fallarDespuesDe(0)
        advanceTimeBy(2_000)
        runCurrent()

        assertFalse(m.captura.activa())
        assertTrue(m.captura.ultimoResumen.value!!.motivoFin.startsWith(CapturaRapida.MOTIVO_ENLACE))
        assertEquals(listOf(true, false), m.enlace.pausas)
    }

    @Test
    fun `fuera de can 11 bits no usa direccion fisica`() = runTest {
        val fake = fake()
        val m = montar(fake, dpn = "7")

        val inicio = m.captura.iniciar(pedal).getOrThrow()
        m.captura.detener()

        assertFalse(TecnicaCaptura.DIRECCION_FISICA in inicio.tecnicas)
        assertFalse(fake.comandos.any { it.startsWith("ATSH") || it.startsWith("ATCRA") })
    }

    @Test
    fun `detener a mitad de un intercambio deja el ELM sin filtro aunque el primer comando se pierda`() = runTest {
        val fake = fake().apply { modelarInterrupcion = true }
        val m = montar(fake)

        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(510)
        m.captura.detener()

        assertTrue("STOPPED" in fake.log.first { it.comando == "ATE0" }.respuesta)
        assertEquals(listOf("ATCRA", "ATSH7DF", "ATAT1"), fake.ejecutados.takeLast(3))
    }

    @Test
    fun `si el filtro no se pudo quitar la siguiente lectura de diagnostico lo reintenta una vez`() = runTest {
        var intentosCra = 0
        val fake = FakeElmTransport(latenciaMs = { 20L }) { cmd ->
            when {
                cmd == "ATCRA" -> if (intentosCra++ == 0) "?>" else "OK>"
                cmd.startsWith("AT") -> "OK>"
                cmd.startsWith("01494A11") -> "41492E4A171180>"
                cmd == "03" -> "4300>"
                else -> "NO DATA>"
            }
        }
        val m = montar(fake)
        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(500)
        m.captura.detener()
        val alCerrar = fake.comandos.size

        m.gate.conceder("dtc") { fake.exchange("03\r", 1_000) }
        m.gate.conceder("dtc") { fake.exchange("03\r", 1_000) }

        assertEquals(listOf("ATE0", "ATCRA", "ATSH7DF", "03", "03"), fake.comandos.drop(alCerrar))
    }

    @Test
    fun `transcurrido cuenta desde el inicio con el reloj de las muestras y es null sin captura activa`() = runTest {
        val m = montar(fake())
        val antes = m.captura.transcurridoMs()

        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(1_500)
        val durante = m.captura.transcurridoMs()
        val ultimaMuestraMs = m.captura.muestrasActuales().maxOf { it.tMicros } / 1_000
        m.captura.detener()

        assertEquals(null, antes)
        assertEquals(1_500L, durante)
        assertTrue("la última muestra ($ultimaMuestraMs ms) no puede ir por delante", ultimaMuestraMs <= durante!!)
        assertEquals(null, m.captura.transcurridoMs())
    }

    @Test
    fun `los pids de vigilancia se sondean cada segundo, se publican y no entran al anillo`() = runTest {
        val m = montar(fake())

        m.captura.iniciar(ConfigCaptura(listOf("11"), 60_000, vigilar = listOf("0D", "ZZ"))).getOrThrow()
        advanceTimeBy(2_500)
        m.captura.detener()

        val velocidades = m.enlace.publicadas.count { it.pid == "0D" }
        assertTrue("entre 2 y 4 lecturas de velocidad en 2,5 s: $velocidades", velocidades in 2..4)
        assertEquals(setOf("11"), m.captura.muestrasActuales().map { it.pid }.toSet())
    }

    @Test
    fun `la captura de una prueba guiada queda marcada en su resumen`() = runTest {
        val m = montar(fake())

        m.captura.iniciar(ConfigCaptura(listOf("11"), 60_000, guiada = true)).getOrThrow()
        advanceTimeBy(200)
        val guiada = m.captura.detener()!!
        m.captura.iniciar(pedal).getOrThrow()
        advanceTimeBy(200)
        val manual = m.captura.detener()!!

        assertTrue(guiada.guiada)
        assertFalse(manual.guiada)
    }
}
