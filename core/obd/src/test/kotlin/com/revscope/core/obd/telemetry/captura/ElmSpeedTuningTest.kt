package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmSpeedTuningTest {

    private fun fake(rechazados: Set<String> = emptySet()) =
        FakeElmTransport { cmd -> if (cmd in rechazados) "?>" else "OK>" }

    private fun afinado(fake: FakeElmTransport) = ElmSpeedTuning { cmd -> fake.exchange(cmd, 1_000) }

    @Test
    fun `en can 11 bits aplica direccion fisica y timing agresivo`() = runTest {
        val fake = fake()

        val aplicadas = afinado(fake).aplicar(esCan11Bit = true)

        assertEquals(setOf(TecnicaCaptura.DIRECCION_FISICA, TecnicaCaptura.TIMING_AGRESIVO), aplicadas)
        assertEquals(listOf("ATSH7E0", "ATCRA7E8", "ATAT2"), fake.comandos)
    }

    @Test
    fun `fuera de can 11 bits no toca el direccionamiento`() = runTest {
        val fake = fake()

        val aplicadas = afinado(fake).aplicar(esCan11Bit = false)

        assertEquals(setOf(TecnicaCaptura.TIMING_AGRESIVO), aplicadas)
        assertEquals(listOf("ATAT2"), fake.comandos)
    }

    @Test
    fun `un AT no soportado se omite y deja el header funcional`() = runTest {
        val fake = fake(rechazados = setOf("ATCRA7E8", "ATAT2"))

        val aplicadas = afinado(fake).aplicar(esCan11Bit = true)

        assertEquals(emptySet<TecnicaCaptura>(), aplicadas)
        assertEquals(listOf("ATSH7E0", "ATCRA7E8", "ATSH7DF", "ATAT2"), fake.comandos)
    }

    @Test
    fun `revertir restaura filtro header y timing aunque la corrutina este cancelada`() = runTest {
        val fake = fake()
        val todo = setOf(TecnicaCaptura.DIRECCION_FISICA, TecnicaCaptura.TIMING_AGRESIVO)

        val job = launch {
            coroutineContext.job.cancel()
            afinado(fake).revertir(todo)
        }
        job.join()

        assertEquals(listOf("ATE0", "ATCRA", "ATSH7DF", "ATAT1"), fake.comandos)
    }

    private fun elmQueSigueOcupado() = FakeElmTransport(latenciaMs = { 50L }) { cmd ->
        if (cmd.startsWith("AT")) "OK\r\r>" else "41492E\r\r>"
    }.apply { modelarInterrupcion = true }

    private fun TestScope.cancelarAMitad(fake: FakeElmTransport) {
        val peticion = launch { fake.exchange("01 49 1\r", 1_000) }
        advanceTimeBy(20)
        peticion.cancel()
        runCurrent()
    }

    @Test
    fun `revertir tras cancelar a mitad de una peticion resincroniza y quita filtro header y timing`() = runTest {
        val fake = elmQueSigueOcupado()
        cancelarAMitad(fake)
        val todo = setOf(TecnicaCaptura.DIRECCION_FISICA, TecnicaCaptura.TIMING_AGRESIVO)

        val pendientes = afinado(fake).revertir(todo)

        assertEquals(emptySet<TecnicaCaptura>(), pendientes)
        assertEquals("el primer ATE0 se pierde con STOPPED", listOf("ATE0", "ATE0", "ATE0"), fake.comandos.take(3))
        assertEquals(listOf("ATCRA", "ATSH7DF", "ATAT1"), fake.ejecutados.takeLast(3))
    }

    @Test
    fun `un AT interrumpido con STOPPED se reintenta aunque luego llegue el signo de pregunta rezagado`() = runTest {
        val fake = elmQueSigueOcupado()
        cancelarAMitad(fake)

        val quitada = afinado(fake).quitarDireccionFisica()

        assertTrue(quitada)
        assertEquals(listOf("ATCRA", "ATCRA", "ATCRA", "ATSH7DF"), fake.comandos)
        assertTrue("ATCRA" in fake.ejecutados)
    }

    @Test
    fun `sin intercambio cortado revertir no reintenta`() = runTest {
        val fake = elmQueSigueOcupado()

        afinado(fake).revertir(setOf(TecnicaCaptura.DIRECCION_FISICA))

        assertEquals(listOf("ATE0", "ATCRA", "ATSH7DF"), fake.comandos)
    }
}
