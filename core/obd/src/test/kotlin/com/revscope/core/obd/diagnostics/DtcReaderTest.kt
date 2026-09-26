package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.pid.TestPids
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcReaderTest {

    private val registry = PidRegistry(TestPids.load()).apply { setSupportedPids(setOf("0C", "0D", "05")) }

    private fun elm(extra: Map<String, String> = emptyMap()) = FakeElmTransport { comando ->
        extra[comando] ?: when (comando) {
            "0101" -> "4101810762A0>"
            "03" -> "43010133>"
            "07" -> "47010420>"
            "0A" -> "4A00>"
            "020200" -> "4202000133>"
            "020C00" -> "420C001AF0>"
            "020D00" -> "420D003C>"
            "020500" -> "4205005A>"
            else -> "NO DATA>"
        }
    }

    @Test
    fun `secuencia exacta de comandos con freeze frame de los pids soportados`() = runTest {
        val fake = elm()

        DtcReader(registry).leer(fake, esCan = true)

        assertEquals(listOf("0101", "03", "07", "0A", "020200", "020C00", "020D00", "020500"), fake.comandos)
    }

    @Test
    fun `lee activos pendientes permanentes mil y conteo`() = runTest {
        val scan = DtcReader(registry).leer(elm(), esCan = true)

        assertEquals(listOf("P0133"), scan.activos.map { it.code })
        assertTrue(scan.activos.all { it.mode == DtcMode.Active })
        assertEquals(listOf("P0420"), scan.pendientes.map { it.code })
        assertTrue(scan.pendientes.all { it.mode == DtcMode.Pending })
        assertTrue(scan.permanentes.isEmpty())
        assertEquals(true, scan.milEncendida)
        assertEquals(1, scan.conteoSegunEcu)
        assertTrue(scan.errores.isEmpty())
    }

    @Test
    fun `02 02 00 da el dtc causante y los valores congelados`() = runTest {
        val scan = DtcReader(registry).leer(elm(), esCan = true)

        val ff = scan.freezeFrame!!
        assertEquals("P0133", ff.dtcCausante)
        assertEquals(listOf("0C", "0D", "05"), ff.valores.map { it.pid })
        assertEquals(1724.0, ff.valores.first { it.pid == "0C" }.value, 0.01)
    }

    @Test
    fun `sin freeze frame guardado no pide los 02 xx`() = runTest {
        val fake = elm(mapOf("020200" to "4202000000>"))

        val scan = DtcReader(registry).leer(fake, esCan = true)

        assertNull(scan.freezeFrame)
        assertFalse(fake.comandos.any { it.startsWith("02") && it != "020200" })
    }

    @Test
    fun `solo los modos pedidos y sin freeze frame`() = runTest {
        val fake = elm()

        DtcReader(registry).leer(fake, DtcLectura(setOf(DtcServicio.PENDIENTES), freezeFrame = false), esCan = true)

        assertEquals(listOf("0101", "07"), fake.comandos)
    }

    @Test
    fun `una respuesta de error va a errores y no aborta`() = runTest {
        val scan = DtcReader(registry).leer(elm(mapOf("07" to "CAN ERROR>")), esCan = true)

        assertEquals(listOf("P0133"), scan.activos.map { it.code })
        assertTrue(scan.errores.single().startsWith("07"))
        assertTrue(scan.freezeFrame != null)
    }

    @Test
    fun `un enlace caido corta la secuencia sin esperar cada timeout`() = runTest {
        val fake = elm().apply { fallarDespuesDe(2) }

        val scan = DtcReader(registry).leer(fake, esCan = true)

        assertEquals(listOf("0101", "03"), fake.comandos)
        assertEquals(1, scan.errores.size)
        assertTrue(scan.pendientes.isEmpty())
    }

    @Test
    fun `el crudo queda disponible por comando`() = runTest {
        val scan = DtcReader(registry).leer(elm(), esCan = true)

        assertEquals("43010133", scan.crudo["03"])
    }

    @Test
    fun `borrar relee despues del 04 e informa el rechazo por condiciones`() = runTest {
        val fake = elm(mapOf("04" to "7F0422>"))

        val borrado = DtcReader(registry).borrar(fake, esCan = true)

        assertEquals(listOf("0101", "03", "04", "0101", "03"), fake.comandos)
        assertTrue(borrado.rechazadoPorCondiciones)
        assertEquals(listOf("P0133"), borrado.despues.activos.map { it.code })
    }
}
