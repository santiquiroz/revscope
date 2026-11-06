package com.revscope.core.obd.taller.referencia

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

enum class OrigenBanda { TIPICO, FUENTE, USUARIO }

enum class PosicionEnBanda { DENTRO, BAJO, ALTO }

data class BandaReferencia(
    val clave: String,
    val min: Double?,
    val max: Double?,
    val unidad: String,
    val origen: OrigenBanda,
    val fuente: String = "",
) {
    init {
        require(min != null || max != null) { "La banda $clave necesita al menos un límite" } // i18n-ignore
        require(min == null || max == null || min <= max) { "La banda $clave tiene el mínimo mayor que el máximo" } // i18n-ignore
        require(origen != OrigenBanda.FUENTE || fuente.isNotBlank()) { "La banda $clave con origen FUENTE necesita citar la fuente" } // i18n-ignore
    }

    val etiquetaOrigen: String get() = when (origen) {
        OrigenBanda.TIPICO -> "Típico (editable)" // i18n-ignore
        OrigenBanda.FUENTE -> "Fuente: $fuente" // i18n-ignore
        OrigenBanda.USUARIO -> "Editado por ti" // i18n-ignore
    }

    fun etiquetaOrigen(resolver: StringResolver): String = when (origen) {
        OrigenBanda.TIPICO -> resolver.get(R.string.obd_band_origin_typical)
        OrigenBanda.FUENTE -> resolver.get(R.string.obd_band_origin_source, fuente)
        OrigenBanda.USUARIO -> resolver.get(R.string.obd_band_origin_user)
    }

    fun clasificar(valor: Double): PosicionEnBanda = when {
        min != null && valor < min -> PosicionEnBanda.BAJO
        max != null && valor > max -> PosicionEnBanda.ALTO
        else -> PosicionEnBanda.DENTRO
    }
}
