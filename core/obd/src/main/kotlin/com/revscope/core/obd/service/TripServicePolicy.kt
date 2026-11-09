package com.revscope.core.obd.service

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.MotivoFin
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

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

    fun titulo(state: ConnectionState, estado: EstadoViaje, resolver: StringResolver): String = when (state) {
        is ConnectionState.Connected ->
            if (estado == EstadoViaje.EnlaceSinViaje) {
                resolver.get(R.string.obd_service_connected_diagnosis, state.deviceName)
            } else {
                resolver.get(R.string.obd_service_connected, state.deviceName)
            }
        ConnectionState.Connecting -> resolver.get(R.string.obd_service_connecting)
        is ConnectionState.Error -> resolver.get(R.string.obd_service_reconnecting)
        ConnectionState.Disconnected -> resolver.get(R.string.obd_service_disconnected)
    }

    fun cuerpoSinLecturas(estado: EstadoViaje, resolver: StringResolver): String =
        if (estado == EstadoViaje.EnlaceSinViaje) {
            resolver.get(R.string.obd_service_not_recording)
        } else {
            resolver.get(R.string.obd_service_recording)
        }

    fun accion(state: ConnectionState, estado: EstadoViaje): AccionViaje? = when {
        state !is ConnectionState.Connected -> null
        estado is EstadoViaje.Grabando -> AccionViaje.FINALIZAR
        estado == EstadoViaje.EnlaceSinViaje -> AccionViaje.INICIAR
        else -> null
    }
}
