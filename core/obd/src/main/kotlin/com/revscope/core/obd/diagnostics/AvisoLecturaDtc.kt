package com.revscope.core.obd.diagnostics

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.protocol.DtcServicio

object AvisoLecturaDtc {

    fun activosSinRespuesta(resolver: StringResolver): String =
        resolver.get(R.string.obd_diagnostico_activos_sin_respuesta)

    fun enlacePerdidoAlFinal(resolver: StringResolver): String =
        resolver.get(R.string.obd_diagnostico_enlace_perdido_al_final)

    fun de(scan: DtcScan, resolver: StringResolver): String? = when {
        !scan.activosConfirmados -> activosSinRespuesta(resolver)
        scan.serviciosFallidos.isNotEmpty() -> serviciosSinRespuesta(scan.serviciosFallidos, resolver)
        scan.enlacePerdido -> enlacePerdidoAlFinal(resolver)
        else -> null
    }

    fun sinCodigos(scan: DtcScan, resolver: StringResolver): String? {
        if (scan.todos.isNotEmpty() || !scan.activosConfirmados) return null
        val leidos = DtcServicio.entries.filterNot { it in scan.serviciosFallidos }.map { etiqueta(it, resolver) }
        return resolver.get(R.string.obd_diagnostico_sin_codigos, enumerar(leidos, resolver.get(R.string.obd_diagnostico_conector_ni)))
    }

    fun etiqueta(servicio: DtcServicio, resolver: StringResolver): String = resolver.get(
        when (servicio) {
            DtcServicio.ACTIVOS -> R.string.obd_diagnostico_servicio_activos
            DtcServicio.PENDIENTES -> R.string.obd_diagnostico_servicio_pendientes
            DtcServicio.PERMANENTES -> R.string.obd_diagnostico_servicio_permanentes
        },
    )

    private fun serviciosSinRespuesta(servicios: Set<DtcServicio>, resolver: StringResolver): String {
        val lista = servicios.sortedBy { it.ordinal }.map { "${etiqueta(it, resolver)} (${it.comando})" }
        val conector = resolver.get(R.string.obd_diagnostico_conector_y)
        return resolver.get(R.string.obd_diagnostico_servicios_sin_respuesta, enumerar(lista, conector))
    }

    private fun enumerar(partes: List<String>, conector: String): String =
        if (partes.size < 2) partes.joinToString() else partes.dropLast(1).joinToString(", ") + " $conector " + partes.last()
}
