package com.revscope.core.obd.escucha

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class EscuchaCanTest {

    private fun escucha(ahora: () -> Long = { 5_000L }) = EscuchaCan(ahora)

    private fun opciones(
        etiqueta: String = "base",
        duracionMs: Long = 1_000L,
        filtro: String? = null,
    ) = OpcionesEscucha(etiqueta, duracionMs, filtro)

    @Test
    fun `protocolo no CAN falla sin mandar AT MA`() = runTest {
        val bt = TransporteFalso(dpn = "3")

        try {
            escucha().capturar(bt, opciones())
            fail("esperaba IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("el protocolo 3 no es CAN"))
        }
        assertFalse(bt.comandos.contains("ATMA"))
    }

    @Test
    fun `protocolo 6 cuenta tramas y errores`() = runTest {
        val bt = TransporteFalso(
            dpn = "6",
            lineas = listOf("7E8 03 41 0D 00", "CAN ERROR", "7E9 02 10 00"),
        )

        val captura = escucha().capturar(bt, opciones(etiqueta = "Base 1!"))

        assertEquals("5000-base_1_", captura.id)
        assertEquals("6", captura.protocolo)
        assertFalse(captura.bits29)
        assertTrue(captura.silencioso)
        assertEquals(2, captura.tramas.size)
        assertEquals(0, captura.desbordes)
        assertEquals(1, captura.errores)
        assertEquals(TramaCan(0L, 0x7E8, false, listOf(0x03, 0x41, 0x0D, 0x00)), captura.tramas[0])
        assertTrue(bt.comandos.contains("ATMA"))
        assertTrue(bt.comandos.contains("ATS0"))
    }

    @Test
    fun `buffer full relanza monitor y registra hueco`() = runTest {
        val bt = TransporteFalso(
            dpn = "6",
            lineasPorVuelta = listOf(
                listOf("BUFFER FULL"),
                listOf("7E803410D00"),
            ),
        )

        val captura = escucha().capturar(bt, opciones())

        assertEquals(2, bt.llamadasMonitor)
        assertEquals(1, captura.desbordes)
        assertEquals(1, captura.huecos.size)
        assertTrue(captura.huecos.single().desdeMs <= captura.huecos.single().hastaMs)
    }

    @Test
    fun `monitor limita los relanzamientos a cincuenta`() = runTest {
        var reloj = 5_000L
        val bt = TransporteFalso(
            dpn = "6",
            lineasPorVuelta = listOf(listOf("BUFFER FULL")),
            avanzarReloj = { reloj++ },
        )

        val captura = EscuchaCan { reloj }.capturar(bt, opciones(duracionMs = 1_000L))

        assertEquals(51, bt.llamadasMonitor)
        assertEquals(51, captura.desbordes)
        assertTrue(captura.huecos.all { it.desdeMs <= it.hastaMs })
    }

    @Test
    fun `protocolo A7 es 29 bits`() = runTest {
        val bt = TransporteFalso(dpn = "A7", lineas = listOf("18 DA F1 10 03 41 0D"))

        val captura = escucha().capturar(bt, opciones())

        assertEquals("7", captura.protocolo)
        assertTrue(captura.bits29)
        assertEquals(TramaCan(0L, 0x18DAF110, true, listOf(0x03, 0x41, 0x0D)), captura.tramas.single())
    }

    @Test
    fun `CSM con interrogante marca no silencioso`() = runTest {
        val bt = TransporteFalso(dpn = "6", csm = "?")

        val captura = escucha().capturar(bt, opciones())

        assertFalse(captura.silencioso)
    }

    @Test
    fun `filtro manda AT CRA`() = runTest {
        val bt = TransporteFalso(dpn = "6")

        val captura = escucha().capturar(bt, opciones(filtro = "7E8"))

        assertEquals("7E8", captura.filtro)
        assertTrue(bt.comandos.contains("ATCRA7E8"))
    }

    @Test
    fun `restaura los 4 comandos aunque monitorear lance`() = runTest {
        val bt = TransporteFalso(dpn = "6", errorMonitor = IOException("corte"))

        try {
            escucha().capturar(bt, opciones())
            fail("esperaba IOException")
        } catch (e: IOException) {
            assertEquals("corte", e.message)
        }
        assertTrue(bt.comandos.contains("ATCAF1"))
        assertTrue(bt.comandos.contains("ATAR"))
        assertTrue(bt.comandos.contains("ATS0"))
        assertTrue(bt.comandos.contains("ATH0"))
    }

    private class TransporteFalso(
        private val dpn: String = "6",
        private val csm: String = "OK",
        private val lineas: List<String> = emptyList(),
        private val errorMonitor: Throwable? = null,
        private val lineasPorVuelta: List<List<String>> = listOf(lineas),
        private val avanzarReloj: () -> Unit = {},
    ) : Transport {

        val comandos = mutableListOf<String>()
        var llamadasMonitor = 0

        override val isConnected: Boolean = true

        override suspend fun connect(): Result<Unit> = Result.success(Unit)

        override suspend fun disconnect() = Unit

        override suspend fun send(command: String) = Unit

        override suspend fun receive(timeoutMs: Long): String = ">"

        override suspend fun exchange(command: String, timeoutMs: Long): String {
            val normal = command.filterNot { it.isWhitespace() }.uppercase()
            comandos += normal
            return when (normal) {
                "ATDPN" -> "$dpn\r>"
                "ATCSM1" -> "$csm\r>"
                else -> "OK\r>"
            }
        }

        override suspend fun targetedExchange(requestHeader: String, request: String, timeoutMs: Long): String =
            exchange(request, timeoutMs)

        override suspend fun monitorear(comando: String, duracionMs: Long, alLinea: (String) -> Unit): String {
            comandos += comando.filterNot { it.isWhitespace() }.uppercase()
            errorMonitor?.let { throw it }
            lineasPorVuelta.getOrElse(llamadasMonitor) { lineasPorVuelta.lastOrNull().orEmpty() }.forEach(alLinea)
            llamadasMonitor++
            avanzarReloj()
            return "STOPPED\r>"
        }

        override fun observeConnectionState(): Flow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("Falso"))
    }
}
