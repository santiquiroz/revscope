package com.revscope.core.obd.mcp.escritura

import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Pide al dueño, por notificación, permiso para cada escritura; el bypass lo concede sin preguntar. */
interface PedidorConfirmacion {
    /** False si el sistema no deja mostrar la notificación (permiso de notificaciones negado). */
    fun pedir(id: Int, solicitud: SolicitudEscritura, esperaMs: Long): Boolean
    fun retirar(id: Int)
}

@Singleton
class AutorizadorEscritura @Inject constructor(
    private val bypass: BypassEscrituras,
    private val pendientes: ConfirmacionesPendientes,
    private val pedidor: PedidorConfirmacion,
) {

    internal var esperaMs: Long = ESPERA_MS

    suspend fun autorizar(solicitud: SolicitudEscritura): Autorizacion {
        if (bypass.activo.value) return Autorizacion.Concedida(OrigenAutorizacion.BYPASS)
        val (id, decision) = pendientes.abrir()
        return try {
            if (!pedidor.pedir(id, solicitud, esperaMs)) return Autorizacion.NotificacionesBloqueadas
            when (withTimeoutOrNull(esperaMs) { decision.await() }) {
                true -> Autorizacion.Concedida(OrigenAutorizacion.TOQUE)
                false -> Autorizacion.Rechazada
                null -> Autorizacion.SinRespuesta
            }
        } finally {
            pendientes.descartar(id)
            pedidor.retirar(id)
        }
    }

    companion object {
        // Por debajo del timeout típico de un cliente MCP (60 s) para que la respuesta alcance a llegar.
        const val ESPERA_MS = 45_000L
    }
}
