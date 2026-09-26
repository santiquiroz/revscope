package com.revscope.core.obd.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripControllerTest {

    private class Harness(scope: CoroutineScope) {
        val eventos = mutableListOf<String>()
        var siguienteId = 1L
        val publicadas = mutableListOf<Long?>()
        lateinit var controller: TripController

        init {
            controller = TripController(
                store = { nombre -> (siguienteId++).also { eventos += "create:$nombre:$it" } },
                closer = { id -> eventos += "close:$id" },
                launcher = { id ->
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        eventos += "record:$id"
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) { delay(50); eventos += "flush:$id" }
                        }
                    }
                },
                publicarSesion = { id ->
                    publicadas += id
                    eventos += "publish:$id:motivo=${controller.ultimoMotivoFin.value}"
                },
                clock = { 1_000L },
            )
        }
    }

    private fun TestScope.harness() = Harness(backgroundScope)

    @Test
    fun `conectar arranca viaje como en v1_19`() = runTest {
        val h = harness()

        val id = h.controller.onEnlaceListo("Vlink")

        assertEquals(EstadoViaje.Grabando(id, 1_000L), h.controller.estado.value)
        assertEquals(listOf<Long?>(id), h.publicadas)
    }

    @Test
    fun `finalizar cierra la sesion y deja EnlaceSinViaje`() = runTest {
        val h = harness()
        val id = h.controller.onEnlaceListo("Vlink")

        val cerrado = h.controller.finalizar(MotivoFin.USUARIO)

        assertEquals(id, cerrado)
        assertEquals(EstadoViaje.EnlaceSinViaje, h.controller.estado.value)
        assertEquals(listOf(id, null), h.publicadas)
        val flush = h.eventos.indexOf("flush:$id")
        assertTrue(flush >= 0 && flush < h.eventos.indexOf("close:$id"))
    }

    @Test
    fun `finalizar es idempotente`() = runTest {
        val h = harness()
        h.controller.onEnlaceListo("Vlink")
        h.controller.finalizar(MotivoFin.MCP)

        val segundo = h.controller.finalizar(MotivoFin.MCP)

        assertNull(segundo)
        assertEquals(1, h.eventos.count { it.startsWith("close:") })
    }

    @Test
    fun `sin enlace finalizar no hace nada e iniciar falla`() = runTest {
        val h = harness()

        assertNull(h.controller.finalizar(MotivoFin.USUARIO))
        val inicio = h.controller.iniciar("Vlink")

        assertTrue(inicio.exceptionOrNull() is ViajeNoDisponibleException)
        assertTrue(h.eventos.isEmpty())
    }

    @Test
    fun `iniciar sobre el enlace crea sesion nueva`() = runTest {
        val h = harness()
        val primero = h.controller.onEnlaceListo("Vlink")
        h.controller.finalizar(MotivoFin.USUARIO)

        val segundo = h.controller.iniciar("Vlink").getOrThrow()

        assertTrue(segundo != primero)
        assertEquals(EstadoViaje.Grabando(segundo, 1_000L), h.controller.estado.value)
        assertTrue("record:$segundo" in h.eventos)
    }

    @Test
    fun `iniciar con un viaje en curso falla sin crear otra sesion`() = runTest {
        val h = harness()
        h.controller.onEnlaceListo("Vlink")

        val resultado = h.controller.iniciar("Vlink")

        assertTrue(resultado.isFailure)
        assertEquals(1, h.eventos.count { it.startsWith("create:") })
    }

    @Test
    fun `finalizar e iniciar concurrentes se serializan`() = runTest {
        val h = harness()
        h.controller.onEnlaceListo("Vlink")

        val fin = async { h.controller.finalizar(MotivoFin.USUARIO) }
        val inicio = async { h.controller.iniciar("Vlink") }
        fin.await()
        inicio.await()

        assertEquals(listOf("close:1", "create:Vlink:2"), h.eventos.filter { it.startsWith("close") || it.startsWith("create:Vlink:2") })
        assertTrue(h.controller.estado.value is EstadoViaje.Grabando)
    }

    @Test
    fun `perdida de enlace en EnlaceSinViaje no cierra nada`() = runTest {
        val h = harness()
        h.controller.onEnlaceListo("Vlink")
        h.controller.finalizar(MotivoFin.USUARIO)

        val cerrado = h.controller.onEnlacePerdido(MotivoFin.ENLACE_PERDIDO)

        assertNull(cerrado)
        assertEquals(EstadoViaje.SinEnlace, h.controller.estado.value)
        assertEquals(1, h.eventos.count { it.startsWith("close:") })
    }

    @Test
    fun `perdida de enlace grabando cierra y deja SinEnlace`() = runTest {
        val h = harness()
        val id = h.controller.onEnlaceListo("Vlink")

        val cerrado = h.controller.onEnlacePerdido(MotivoFin.ENLACE_PERDIDO)

        assertEquals(id, cerrado)
        assertEquals(EstadoViaje.SinEnlace, h.controller.estado.value)
    }

    @Test
    fun `el motivo se publica antes que el null`() = runTest {
        val h = harness()
        h.controller.onEnlaceListo("Vlink")

        h.controller.finalizar(MotivoFin.MCP)

        assertTrue("publish:null:motivo=MCP" in h.eventos)
    }

    @Test
    fun `solo usuario y mcp son fines voluntarios`() {
        assertEquals(setOf(MotivoFin.USUARIO, MotivoFin.MCP), MotivoFin.entries.filter { it.esVoluntario }.toSet())
    }

    @Test
    fun `un enlace nuevo con viaje abierto cierra el anterior por reconexion`() = runTest {
        val h = harness()
        val primero = h.controller.onEnlaceListo("Vlink")

        val segundo = h.controller.onEnlaceListo("Vlink")

        assertTrue("close:$primero" in h.eventos)
        assertEquals(MotivoFin.RECONEXION, h.controller.ultimoMotivoFin.value)
        assertEquals(EstadoViaje.Grabando(segundo, 1_000L), h.controller.estado.value)
    }
}
