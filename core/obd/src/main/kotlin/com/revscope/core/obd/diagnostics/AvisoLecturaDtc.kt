package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.protocol.DtcServicio

object AvisoLecturaDtc {

    const val ACTIVOS_SIN_RESPUESTA =
        "La ECU no respondió a la lectura de códigos activos (03): esto no confirma que no haya fallas. " +
            "Vuelve a leer los códigos."
    const val ENLACE_PERDIDO_AL_FINAL = "Se perdió el enlace al final de la lectura: el freeze frame puede estar incompleto."

    fun de(scan: DtcScan): String? = when {
        !scan.activosConfirmados -> ACTIVOS_SIN_RESPUESTA
        scan.serviciosFallidos.isNotEmpty() -> serviciosSinRespuesta(scan.serviciosFallidos)
        scan.enlacePerdido -> ENLACE_PERDIDO_AL_FINAL
        else -> null
    }

    fun sinCodigos(scan: DtcScan): String? {
        if (scan.todos.isNotEmpty() || !scan.activosConfirmados) return null
        val leidos = DtcServicio.entries.filterNot { it in scan.serviciosFallidos }
        return "Sin códigos ${enumerar(leidos.map(::etiqueta), "ni")}"
    }

    fun etiqueta(servicio: DtcServicio): String = when (servicio) {
        DtcServicio.ACTIVOS -> "activos"
        DtcServicio.PENDIENTES -> "pendientes"
        DtcServicio.PERMANENTES -> "permanentes"
    }

    private fun serviciosSinRespuesta(servicios: Set<DtcServicio>): String {
        val lista = servicios.sortedBy { it.ordinal }.map { "${etiqueta(it)} (${it.comando})" }
        return "La ECU no respondió a los códigos ${enumerar(lista, "y")}. Algunas ECU no admiten esas lecturas; " +
            "los activos sí se leyeron."
    }

    private fun enumerar(partes: List<String>, conector: String): String =
        if (partes.size < 2) partes.joinToString() else partes.dropLast(1).joinToString(", ") + " $conector " + partes.last()
}
