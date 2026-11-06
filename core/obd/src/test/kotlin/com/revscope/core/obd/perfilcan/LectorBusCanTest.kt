package com.revscope.core.obd.perfilcan

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.telemetry.PollingGate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LectorBusCanTest {

    private val resolver = com.revscope.core.uitesting.XmlStringResolver.es(
        com.revscope.core.obd.R::class.java,
        resDirs = listOf("src/main/res", "../common/src/main/res"),
    )

    private val pata = senalDePrueba(
        clave = ClavesSenal.PATA_LATERAL,
        bitInicio = 46,
        largo = 1,
        tipo = TipoSenal.BANDERA,
        invalidos = emptySet(),
    )

    private fun TestScope.resolutor(perfil: PerfilCan, modo: ModoFuente = ModoFuente.COMBINADO) =
        ResolutorFuentes(perfil, modo, emptyMap(), esCan = true, LectorBusCan.intervaloVentanaMs(modo)) {
            testScheduler.currentTime
        }

    private fun TestScope.lector(
        bt: Transport,
        perfil: PerfilCan = perfilDePrueba(),
        modo: ModoFuente = ModoFuente.COMBINADO,
        resolutor: ResolutorFuentes = resolutor(perfil, modo),
    ) = LectorBusCan(bt, PollingGate(), perfil, modo, resolutor) { testScheduler.currentTime }

    private fun TestScope.recolectar(lector: LectorBusCan): MutableList<ObdReading> {
        val recibidas = mutableListOf<ObdReading>()
        backgroundScope.launch { lector.lecturas().collect { recibidas += it } }
        runCurrent()
        return recibidas
    }

    @Test
    fun `una ventana manda setup, AT MA y restauracion en ese orden`() = runTest {
        val bt = TransporteFalso()

        recolectar(lector(bt))

        assertEquals(
            listOf("ATH1", "ATS0", "ATCAF0", "ATCSM1", "ATMA", "ATCAF1", "ATAR", "ATS0", "ATH0"),
            bt.comandos,
        )
    }

    @Test
    fun `restaura aunque monitorear lance IOException`() = runTest {
        val bt = TransporteFalso(errorMonitor = IOException("corte"))

        try {
            lector(bt).lecturas().toList()
            fail("esperaba IOException")
        } catch (e: IOException) {
            assertEquals("corte", e.message)
        }
        assertEquals(listOf("ATCAF1", "ATAR", "ATS0", "ATH0"), bt.comandos.takeLast(4))
    }

    @Test
    fun `restaura aunque cancelen la ventana`() = runTest {
        val bt = TransporteFalso(lineas = listOf("500 00 00 44 00 03 00 4B 04"), colgarMonitor = true)
        val recibidas = mutableListOf<ObdReading>()
        val job = backgroundScope.launch { lector(bt).lecturas().collect { recibidas += it } }
        runCurrent()
        assertEquals(1, recibidas.size)

        job.cancel()
        runCurrent()

        assertEquals(listOf("ATCAF1", "ATAR", "ATS0", "ATH0"), bt.comandos.takeLast(4))
    }

    @Test
    fun `emite GEAR 3 desde la trama 500 de tercera`() = runTest {
        val bt = TransporteFalso(lineas = listOf("7E8 03 41 0D 00", "500 00 00 44 00 03 00 4B 04"))
        val perfil = perfilDePrueba()
        val resolutor = resolutor(perfil)

        val lectura = lector(bt, perfil, resolutor = resolutor).lecturas().first()

        assertEquals(ClavesSenal.GEAR, lectura.pid)
        assertEquals(3.0, lectura.value, 0.0)
        assertTrue(resolutor.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `no emite señales sin verificar`() = runTest {
        val bt = TransporteFalso(lineas = listOf("500 00 00 45 00 03 40 46 04"))
        val perfil = perfilDePrueba(senales = listOf(senalDePrueba(estado = EstadoSenal.SIN_VERIFICAR), pata))

        val recibidas = recolectar(lector(bt, perfil))

        assertEquals(listOf(ClavesSenal.PATA_LATERAL), recibidas.map { it.pid })
        assertEquals(1.0, recibidas.single().value, 0.0)
    }

    @Test
    fun `sin filtro de estado emite el texto de una señal sin verificar`() = runTest {
        val bt = TransporteFalso(lineas = listOf("500 00 00 44 00 03 00 4B 04"))
        val perfil = perfilDePrueba(senales = listOf(senalDePrueba(estado = EstadoSenal.SIN_VERIFICAR)))
        val lector = LectorBusCan(
            bt, PollingGate(), perfil, ModoFuente.COMBINADO, resolutor(perfil), soloVerificadas = false,
        ) { testScheduler.currentTime }

        assertEquals("3", lector.textos(resolver).first())
    }

    @Test
    fun `marcha 07 no emite ni registra frescura`() = runTest {
        val bt = TransporteFalso(lineas = listOf("500 00 00 45 00 07 00 46 04"))
        val perfil = perfilDePrueba()
        val resolutor = resolutor(perfil)

        val recibidas = recolectar(lector(bt, perfil, resolutor = resolutor))

        assertTrue(recibidas.isEmpty())
        assertFalse(resolutor.usaBus(ClavesSenal.GEAR))
    }

    @Test
    fun `adaptador sin AT MA marca sinMonitor y termina sin reintentar`() = runTest {
        val bt = TransporteFalso(lineas = listOf("?"), finalMonitor = "?\r>")
        val lector = lector(bt)

        assertTrue(lector.lecturas().toList().isEmpty())

        assertTrue(lector.sinMonitor.value)
        assertEquals(1, bt.comandos.count { it == "ATMA" })
        assertEquals("ATH0", bt.comandos.last())
    }

    @Test
    fun `transporte sin modo monitor marca sinMonitor`() = runTest {
        val bt = TransporteFalso(errorMonitor = UnsupportedOperationException("sin monitor"))
        val lector = lector(bt)

        assertTrue(lector.lecturas().toList().isEmpty())

        assertTrue(lector.sinMonitor.value)
        assertEquals(1, bt.comandos.count { it == "ATMA" })
    }

    @Test
    fun `combinado espera INTERVALO entre ventanas`() = runTest {
        val bt = TransporteFalso()

        recolectar(lector(bt))
        assertEquals(1, bt.comandos.count { it == "ATMA" })

        advanceTimeBy(LectorBusCan.INTERVALO_COMBINADO_MS - 1)
        runCurrent()
        assertEquals(1, bt.comandos.count { it == "ATMA" })

        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, bt.comandos.count { it == "ATMA" })
    }

    @Test
    fun `solo bus usa ventanas largas con pausa corta`() = runTest {
        val bt = TransporteFalso()

        recolectar(lector(bt, modo = ModoFuente.SOLO_BUS))
        assertEquals(listOf(LectorBusCan.VENTANA_SOLO_BUS_MS), bt.duraciones)

        advanceTimeBy(LectorBusCan.PAUSA_SOLO_BUS_MS)
        runCurrent()
        assertEquals(2, bt.duraciones.size)
    }

    @Test
    fun `ventana combinada es 1,5 periodos con tope de 200 ms`() {
        assertEquals(150L, LectorBusCan.ventanaCombinadoMs(listOf(senalDePrueba(periodoMs = 100))))
        assertEquals(200L, LectorBusCan.ventanaCombinadoMs(listOf(senalDePrueba(periodoMs = 1_000))))
    }

    private class TransporteFalso(
        private val lineas: List<String> = emptyList(),
        private val errorMonitor: Throwable? = null,
        private val finalMonitor: String = "STOPPED\r>",
        private val colgarMonitor: Boolean = false,
    ) : Transport {

        val comandos = mutableListOf<String>()
        val duraciones = mutableListOf<Long>()

        override val isConnected: Boolean = true

        override suspend fun connect(): Result<Unit> = Result.success(Unit)

        override suspend fun disconnect() = Unit

        override suspend fun send(command: String) = Unit

        override suspend fun receive(timeoutMs: Long): String = ">"

        override suspend fun exchange(command: String, timeoutMs: Long): String {
            comandos += command.filterNot { it.isWhitespace() }.uppercase()
            return "OK\r>"
        }

        override suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String =
            exchange(request, timeoutMs)

        override suspend fun monitorear(
            comando: String,
            duracionMs: Long,
            detener: () -> Boolean,
            alLinea: (String) -> Unit,
        ): String {
            comandos += comando.filterNot { it.isWhitespace() }.uppercase()
            duraciones += duracionMs
            errorMonitor?.let { throw it }
            lineas.forEach(alLinea)
            if (colgarMonitor) awaitCancellation()
            return finalMonitor
        }

        override fun observeConnectionState(): Flow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("Falso"))
    }
}
