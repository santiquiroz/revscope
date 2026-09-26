package com.revscope.core.obd.service

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.MotivoFin

/** Decisiones puras del servicio en primer plano sobre el ciclo enlace/viaje. */
object TripServicePolicy {

    enum class AccionViaje { FINALIZAR, INICIAR }

    /** Un fin pedido por el usuario o el MCP no es la huella de un choque. */
    fun entrarEnGraciaDeChoque(
        motivo: MotivoFin?,
        deteccionActiva: Boolean,
        huboMovimientoReciente: Boolean,
    ): Boolean = motivo?.esVoluntario != true && deteccionActiva && huboMovimientoReciente

    /** Con el adaptador aún conectado el servicio mantiene vivo el enlace: no se detiene. */
    fun detenerTrasGracia(enlaceVivo: Boolean): Boolean = !enlaceVivo

    fun titulo(state: ConnectionState, estado: EstadoViaje): String = when (state) {
        is ConnectionState.Connected ->
            if (estado == EstadoViaje.EnlaceSinViaje) "Conectado a ${state.deviceName} · sin viaje (diagnóstico)"
            else "Conectado a ${state.deviceName}"
        ConnectionState.Connecting -> "Conectando…"
        is ConnectionState.Error -> "Enlace perdido — reintentando"
        ConnectionState.Disconnected -> "Desconectado"
    }

    fun cuerpoSinLecturas(estado: EstadoViaje): String =
        if (estado == EstadoViaje.EnlaceSinViaje) "Sin grabar · lecturas en vivo" else "Grabando telemetría"

    fun accion(state: ConnectionState, estado: EstadoViaje): AccionViaje? = when {
        state !is ConnectionState.Connected -> null
        estado is EstadoViaje.Grabando -> AccionViaje.FINALIZAR
        estado == EstadoViaje.EnlaceSinViaje -> AccionViaje.INICIAR
        else -> null
    }
}
