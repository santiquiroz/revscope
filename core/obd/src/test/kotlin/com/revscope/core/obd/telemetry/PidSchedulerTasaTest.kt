package com.revscope.core.obd.telemetry

import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.testing.FakeElmTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PidSchedulerTasaTest {

    private val soloP3 = """
        [ { "mode": "01", "pid": "0F", "name": "IAT", "nameEs": "Temp Aire", "bytes": 1,
            "formula": "A-40", "unit": "C", "min": -40, "max": 215, "priority": 3 } ]
    """.trimIndent()

    private fun TestScope.fake(respuesta: (Int) -> String = { "410F50>" }): FakeElmTransport {
        var n = 0
        return FakeElmTransport(latenciaMs = { 30L }, reloj = { testScheduler.currentTime }) { respuesta(n++) }
    }

    private fun TestScope.scheduler(fake: FakeElmTransport) =
        PidScheduler(fake, PidRegistry(soloP3), nowMs = { testScheduler.currentTime })

    private fun inicios(fake: FakeElmTransport): List<Long> = fake.log.map { it.tInicioMs }

    private fun periodos(fake: FakeElmTransport): List<Long> = inicios(fake).zipWithNext { a, b -> b - a }

    @Test
    fun `tasa fija no acumula deriva con latencia de 30ms`() = runTest {
        val fake = fake()
        val job = launch { scheduler(fake).observeReadings().collect {} }
        advanceTimeBy(8_100)
        job.cancel()

        assertEquals(listOf(0L, 2_000L, 4_000L, 6_000L, 8_000L), inicios(fake))
    }

    @Test
    fun `el multiplicador de BUFFER FULL decae tras 30s sin eventos`() = runTest {
        val fake = fake { n -> if (n == 0) "BUFFER FULL>" else "410F50>" }
        val job = launch { scheduler(fake).observeReadings().collect {} }
        advanceTimeBy(40_000)
        job.cancel()

        val gaps = periodos(fake)
        // El ciclo del BUFFER FULL ya tenía su intervalo; desde el siguiente va ×2 hasta decaer.
        assertTrue("antes de 30 s el intervalo va ×2: $gaps", gaps.drop(1).take(7).all { it == 4_000L })
        assertEquals("tras decaer vuelve a 2 s: $gaps", 2_000L, gaps.last())
    }

    @Test
    fun `con espectador remoto la pantalla apagada no estira intervalos`() = runTest {
        val fake = fake()
        val scheduler = scheduler(fake).apply {
            setIdleMode(true)
            setRemoteViewerActive(true)
        }
        val job = launch { scheduler.observeReadings().collect {} }
        advanceTimeBy(6_100)
        job.cancel()

        assertTrue(periodos(fake).all { it == 2_000L })
    }

    @Test
    fun `sin espectador la pantalla apagada estira p3 al doble`() = runTest {
        val fake = fake()
        val scheduler = scheduler(fake).apply { setIdleMode(true) }
        val job = launch { scheduler.observeReadings().collect {} }
        advanceTimeBy(8_100)
        job.cancel()

        assertTrue(periodos(fake).all { it == 4_000L })
    }

    @Test
    fun `preset 500ms lleva p3 a medio segundo`() = runTest {
        val fake = fake()
        val scheduler = scheduler(fake).apply { setPreset(SamplingPreset.MEDIO_SEGUNDO) }
        val job = launch { scheduler.observeReadings().collect {} }
        advanceTimeBy(2_100)
        job.cancel()

        assertEquals(listOf(0L, 500L, 1_000L, 1_500L, 2_000L), inicios(fake))
    }

    @Test
    fun `preset maximo encadena peticiones sin esperar`() = runTest {
        val fake = fake()
        val scheduler = scheduler(fake).apply { setPreset(SamplingPreset.MAXIMO) }
        val job = launch { scheduler.observeReadings().collect {} }
        advanceTimeBy(300)
        job.cancel()

        assertTrue(periodos(fake).all { it == 30L })
        assertEquals("300 ms / 30 ms por petición, la décima sigue en vuelo", 9, fake.log.size)
    }

    @Test
    fun `en pausa ningun grupo pide y al reanudar sigue`() = runTest {
        val fake = fake()
        val scheduler = scheduler(fake).apply { setPreset(SamplingPreset.MEDIO_SEGUNDO) }
        val job = launch { scheduler.observeReadings().collect {} }
        advanceTimeBy(600)
        scheduler.setPaused(true)
        val alPausar = fake.log.size
        advanceTimeBy(3_000)
        val enPausa = fake.log.size - alPausar
        scheduler.setPaused(false)
        advanceTimeBy(1_100)
        job.cancel()

        assertEquals(0, enPausa)
        assertTrue(fake.log.size > alPausar + 1)
    }
}
