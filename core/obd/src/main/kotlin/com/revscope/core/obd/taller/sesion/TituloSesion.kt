package com.revscope.core.obd.taller.sesion

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

object TituloSesion {

    private const val MAX_CARACTERES = 80

    fun de(solicitud: SolicitudSesion, resolver: StringResolver): String =
        listOfNotNull(
            solicitud.titulo,
            solicitud.sintomas.minByOrNull { it.ordinal }?.etiqueta(resolver),
            solicitud.sintomasTexto.lineSequence().firstOrNull(),
        ).map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(MAX_CARACTERES)
            ?: resolver.get(R.string.obd_session_default_title)
}
