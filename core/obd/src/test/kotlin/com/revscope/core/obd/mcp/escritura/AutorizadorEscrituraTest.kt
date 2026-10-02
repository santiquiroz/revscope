package com.revscope.core.obd.mcp.escritura

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutorizadorEscrituraTest {

    @Test
    fun `bypass concede sin pedir confirmacion`() = runTest {
        val fixture = fixture(bypassActivo = true)

        assertEquals(
            Autorizacion.Concedida(OrigenAutorizacion.BYPASS),
            fixture.autorizador.autorizar(solicitud),
        )
        assertTrue(fixture.pedidor.idsPedidos.isEmpty())
        assertTrue(fixture.pedidor.idsRetirados.isEmpty())
    }

    @Test
    fun `permiso del dueño concede por toque y limpia la confirmacion`() = runTest {
        val fixture = fixture()
        fixture.pedidor.alPedir = { id -> assertTrue(fixture.pendientes.resolver(id, true)) }

        assertEquals(
            Autorizacion.Concedida(OrigenAutorizacion.TOQUE),
            fixture.autorizador.autorizar(solicitud),
        )

        assertEquals(fixture.pedidor.idsPedidos, fixture.pedidor.idsRetirados)
        assertFalse(fixture.pendientes.resolver(fixture.pedidor.idsPedidos.single(), true))
    }

    @Test
    fun `rechazo del dueño se reporta y limpia la confirmacion`() = runTest {
        val fixture = fixture()
        fixture.pedidor.alPedir = { id -> assertTrue(fixture.pendientes.resolver(id, false)) }

        assertEquals(Autorizacion.Rechazada, fixture.autorizador.autorizar(solicitud))
        assertEquals(fixture.pedidor.idsPedidos, fixture.pedidor.idsRetirados)
        assertFalse(fixture.pendientes.resolver(fixture.pedidor.idsPedidos.single(), true))
    }

    @Test
    fun `sin respuesta vence la espera y limpia la confirmacion`() = runTest {
        val fixture = fixture().also { it.autorizador.esperaMs = 50 }

        assertEquals(Autorizacion.SinRespuesta, fixture.autorizador.autorizar(solicitud))
        assertEquals(fixture.pedidor.idsPedidos, fixture.pedidor.idsRetirados)
        assertFalse(fixture.pendientes.resolver(fixture.pedidor.idsPedidos.single(), true))
    }

    @Test
    fun `notificaciones bloqueadas aun retira y limpia la confirmacion`() = runTest {
        val fixture = fixture().also { it.pedidor.puedeNotificar = false }

        assertEquals(Autorizacion.NotificacionesBloqueadas, fixture.autorizador.autorizar(solicitud))
        assertEquals(fixture.pedidor.idsPedidos, fixture.pedidor.idsRetirados)
        assertFalse(fixture.pendientes.resolver(fixture.pedidor.idsPedidos.single(), true))
    }

    private fun fixture(bypassActivo: Boolean = false): Fixture {
        val pendientes = ConfirmacionesPendientes()
        val bypass = BypassEscrituras().apply { if (bypassActivo) encender() }
        val pedidor = PedidorFalso()
        return Fixture(pendientes, pedidor, AutorizadorEscritura(bypass, pendientes, pedidor))
    }

    private data class Fixture(
        val pendientes: ConfirmacionesPendientes,
        val pedidor: PedidorFalso,
        val autorizador: AutorizadorEscritura,
    )

    private class PedidorFalso : PedidorConfirmacion {
        val idsPedidos = mutableListOf<Int>()
        val idsRetirados = mutableListOf<Int>()
        var puedeNotificar = true
        var alPedir: ((Int) -> Unit)? = null

        override fun pedir(id: Int, solicitud: SolicitudEscritura, esperaMs: Long): Boolean {
            idsPedidos += id
            alPedir?.invoke(id)
            return puedeNotificar
        }

        override fun retirar(id: Int) {
            idsRetirados += id
        }
    }

    private companion object {
        val solicitud = SolicitudEscritura("prueba", "prueba", null, listOf("11 03"))
    }
}
