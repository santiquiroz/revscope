package com.revscope.core.obd.taller.referencia

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
        require(min != null || max != null) { "La banda $clave necesita al menos un límite" }
        require(min == null || max == null || min <= max) { "La banda $clave tiene el mínimo mayor que el máximo" }
        require(origen != OrigenBanda.FUENTE || fuente.isNotBlank()) { "La banda $clave con origen FUENTE necesita citar la fuente" }
    }

    val etiquetaOrigen: String
        get() = when (origen) {
            OrigenBanda.TIPICO -> "Típico (editable)"
            OrigenBanda.FUENTE -> "Fuente: $fuente"
            OrigenBanda.USUARIO -> "Editado por ti"
        }

    fun clasificar(valor: Double): PosicionEnBanda = when {
        min != null && valor < min -> PosicionEnBanda.BAJO
        max != null && valor > max -> PosicionEnBanda.ALTO
        else -> PosicionEnBanda.DENTRO
    }
}
