package com.revscope.core.obd.session

import kotlinx.coroutines.Job

/** Viaje OBD separado del enlace: el adaptador puede seguir conectado sin grabar. */
sealed interface EstadoViaje {
    data object SinEnlace : EstadoViaje
    data object EnlaceSinViaje : EstadoViaje
    data class Grabando(val sessionId: Long, val inicioMs: Long) : EstadoViaje
}

enum class MotivoFin {
    USUARIO,
    MCP,
    ENLACE_PERDIDO,
    RECONEXION,
    ;

    /** Fin pedido por alguien: no es la huella de un choque (no abre la gracia de detección). */
    val esVoluntario: Boolean get() = this == USUARIO || this == MCP
}

fun interface SessionStore {
    suspend fun create(adapterName: String): Long
}

fun interface SessionCloser {
    suspend fun close(sessionId: Long)
}

fun interface RecordingLauncher {
    fun launch(sessionId: Long): Job
}

class ViajeNoDisponibleException(message: String) : IllegalStateException(message)
