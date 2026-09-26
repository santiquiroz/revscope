package com.revscope.core.obd.taller.sesion

object TituloSesion {

    const val POR_DEFECTO = "Diagnóstico"
    private const val MAX_CARACTERES = 80

    fun de(solicitud: SolicitudSesion): String =
        listOfNotNull(
            solicitud.titulo,
            solicitud.sintomas.minByOrNull { it.ordinal }?.etiqueta,
            solicitud.sintomasTexto.lineSequence().firstOrNull(),
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(MAX_CARACTERES) ?: POR_DEFECTO
}
