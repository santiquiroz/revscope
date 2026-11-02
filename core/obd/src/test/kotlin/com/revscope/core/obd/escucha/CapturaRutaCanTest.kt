package com.revscope.core.obd.escucha

import com.revscope.core.obd.mcp.leaseFixture
import com.revscope.core.obd.session.EstadoViaje
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CapturaRutaCanTest {

    @get:Rule
    val temporal = TemporaryFolder()

    private var reloj = 1_000L
    private val escucha = mockk<EscuchaCan>()
    private val gps = mockk<MuestreadorGpsCaptura>()

    private fun TestScope.controlador(
        conectado: Boolean = true,
        viaje: EstadoViaje = EstadoViaje.EnlaceSinViaje,
        scope: CoroutineScope = backgroundScope,
    ): Pair<CapturaRutaCan, CapturasCanRepositorio> {
        val fixture = leaseFixture(conectado)
        every { fixture.manager.estadoViaje } returns MutableStateFlow(viaje)
        val repositorio = CapturasCanRepositorio(temporal.root)
        val servicio = ServicioEscuchaCan(fixture.manager, escucha, repositorio)
        every { gps.iniciar(any()) } returns true
        every { gps.detener() } returns listOf(MuestraGps(500L, 42.5f, 3f, 180f))
        every { gps.velocidadActualKmh } returns 42.5f
        coEvery { escucha.capturar(any(), any()) } coAnswers {
            val opciones = secondArg<OpcionesEscucha>()
            opciones.alProgreso(3, 1)
            while (!opciones.detener()) delay(100)
            captura(inicio = 1_200L, etiqueta = opciones.etiqueta)
        }
        return CapturaRutaCan(fixture.manager, servicio, repositorio, gps, { reloj }, scope) to repositorio
    }

    @Test
    fun `sin conexion rechaza sin arrancar el gps`() = runTest {
        val (captura, _) = controlador(conectado = false)

        assertTrue(captura.iniciar("ruta").isFailure)

        assertEquals(EstadoCapturaRuta.Fallo(CapturaRutaCan.SIN_CONEXION), captura.estado.value)
        verify(exactly = 0) { gps.iniciar(any()) }
    }

    @Test
    fun `con viaje grabando rechaza con el mensaje del modo escucha`() = runTest {
        val (captura, _) = controlador(viaje = EstadoViaje.Grabando(sessionId = 1L, inicioMs = 0L))

        assertTrue(captura.iniciar("ruta").isFailure)

        assertEquals(EstadoCapturaRuta.Fallo(ServicioEscuchaCan.VIAJE_GRABANDO), captura.estado.value)
        coVerify(exactly = 0) { escucha.capturar(any(), any()) }
    }

    @Test
    fun `doble inicio se rechaza con Fallo y la captura en curso sigue`() = runTest {
        val (captura, _) = controlador()

        assertTrue(captura.iniciar("ruta").isSuccess)
        runCurrent()
        assertTrue(captura.iniciar("otra").isFailure)

        assertEquals(EstadoCapturaRuta.Fallo(CapturaRutaCan.YA_CAPTURANDO), captura.estado.value)
        assertTrue(captura.iniciar("tercera").isFailure)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue(captura.estado.value is EstadoCapturaRuta.Capturando)
        coVerify(exactly = 1) { escucha.capturar(any(), any()) }
        verify(exactly = 1) { gps.iniciar(any()) }
        captura.detener()
    }

    @Test
    fun `el estado reporta tramas desbordes y velocidad gps`() = runTest {
        val (captura, _) = controlador()

        captura.iniciar("ruta")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(EstadoCapturaRuta.Capturando("ruta", 1_000L, 3, 1, 42.5f), captura.estado.value)
        captura.detener()
    }

    @Test
    fun `detener deja Terminada con el id guardado y el gps rebasado`() = runTest {
        val (captura, repositorio) = controlador()

        captura.iniciar("ruta")
        advanceTimeBy(1_000)
        captura.detener()
        advanceTimeBy(200)
        runCurrent()

        assertEquals(EstadoCapturaRuta.Terminada("1200-ruta", 2, 1, 4_000L), captura.estado.value)
        val guardada = repositorio.listar().single()
        assertEquals(CapturaCan.ORIGEN_APP, guardada.origen)
        assertEquals(1, guardada.muestrasGps)
        assertEquals(listOf(MuestraGps(300L, 42.5f, 3f, 180f)), repositorio.leerGps("1200-ruta"))
        verify(exactly = 1) { gps.detener() }
    }

    @Test
    fun `se detiene sola al llegar al tope`() = runTest {
        val (captura, _) = controlador()

        captura.iniciar("ruta")
        advanceTimeBy(1_000)
        assertTrue(captura.estado.value is EstadoCapturaRuta.Capturando)
        reloj += CapturaRutaCan.MAX_RUTA_MS
        advanceTimeBy(200)
        runCurrent()

        assertTrue(captura.estado.value is EstadoCapturaRuta.Terminada)
    }

    @Test
    fun `etiqueta vacia usa ruta`() = runTest {
        val (captura, _) = controlador()

        captura.iniciar("  ")

        assertEquals("ruta", (captura.estado.value as EstadoCapturaRuta.Capturando).etiqueta)
        captura.detener()
    }

    private fun captura(inicio: Long, etiqueta: String) = CapturaCan(
        id = "$inicio-${EscuchaCan.normalizarEtiqueta(etiqueta)}",
        etiqueta = etiqueta,
        inicioEpochMs = inicio,
        duracionMs = 4_000L,
        protocolo = "6",
        bits29 = false,
        filtro = null,
        silencioso = true,
        desbordes = 1,
        errores = 0,
        tramas = listOf(
            TramaCan(0L, 0x7E8, false, listOf(0x01)),
            TramaCan(100L, 0x7E8, false, listOf(0x02)),
        ),
    )
}
