package com.revscope.core.obd.service

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.service.TripServicePolicy.AccionViaje
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.MotivoFin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripServicePolicyTest {

    private val conectado = ConnectionState.Connected("Android-Vlink")
    private val grabando = EstadoViaje.Grabando(sessionId = 7L, inicioMs = 0L)

    @Test
    fun `fin USUARIO o MCP no entra en gracia de choque`() {
        assertFalse(TripServicePolicy.entrarEnGraciaDeChoque(MotivoFin.USUARIO, true, true))
        assertFalse(TripServicePolicy.entrarEnGraciaDeChoque(MotivoFin.MCP, true, true))
    }

    @Test
    fun `perdida real de enlace con movimiento reciente si entra en gracia`() {
        assertTrue(TripServicePolicy.entrarEnGraciaDeChoque(MotivoFin.ENLACE_PERDIDO, true, true))
        assertTrue(TripServicePolicy.entrarEnGraciaDeChoque(null, true, true))
    }

    @Test
    fun `sin deteccion o sin movimiento no hay gracia`() {
        assertFalse(TripServicePolicy.entrarEnGraciaDeChoque(MotivoFin.ENLACE_PERDIDO, false, true))
        assertFalse(TripServicePolicy.entrarEnGraciaDeChoque(MotivoFin.ENLACE_PERDIDO, true, false))
    }

    @Test
    fun `sin sesion pero con enlace vivo el servicio no se detiene`() {
        assertFalse(TripServicePolicy.detenerTrasGracia(enlaceVivo = true))
        assertTrue(TripServicePolicy.detenerTrasGracia(enlaceVivo = false))
    }

    @Test
    fun `titulo y cuerpo dicen sin viaje con el enlace vivo`() {
        assertEquals(
            "Conectado a Android-Vlink · sin viaje (diagnóstico)",
            TripServicePolicy.titulo(conectado, EstadoViaje.EnlaceSinViaje),
        )
        assertEquals("Conectado a Android-Vlink", TripServicePolicy.titulo(conectado, grabando))
        assertEquals("Sin grabar · lecturas en vivo", TripServicePolicy.cuerpoSinLecturas(EstadoViaje.EnlaceSinViaje))
        assertEquals("Grabando telemetría", TripServicePolicy.cuerpoSinLecturas(grabando))
    }

    @Test
    fun `la accion de la notificacion sigue al estado del viaje`() {
        assertEquals(AccionViaje.FINALIZAR, TripServicePolicy.accion(conectado, grabando))
        assertEquals(AccionViaje.INICIAR, TripServicePolicy.accion(conectado, EstadoViaje.EnlaceSinViaje))
        assertNull(TripServicePolicy.accion(ConnectionState.Error("x"), grabando))
        assertNull(TripServicePolicy.accion(conectado, EstadoViaje.SinEnlace))
    }
}
