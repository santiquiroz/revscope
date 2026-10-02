package com.revscope.core.obd.mcp.escritura

/** Lo que una tool pide escribir: [resumen] es lo que el dueño lee en la notificación antes de permitir. */
data class SolicitudEscritura(
    val tool: String,
    val resumen: String,
    val header: String?,
    val pasos: List<String>,
    val requiereMotorApagado: Boolean = false,
)

enum class OrigenAutorizacion { TOQUE, BYPASS }

sealed interface Autorizacion {
    data class Concedida(val origen: OrigenAutorizacion) : Autorizacion
    data object Rechazada : Autorizacion
    data object SinRespuesta : Autorizacion
    data object NotificacionesBloqueadas : Autorizacion
}

sealed interface ResultadoEscritura {
    data class Ejecutada(val origen: OrigenAutorizacion, val respuestas: List<String>) : ResultadoEscritura
    data class NoAutorizada(val motivo: String) : ResultadoEscritura
    data class Fallida(val origen: OrigenAutorizacion, val error: String) : ResultadoEscritura
}
