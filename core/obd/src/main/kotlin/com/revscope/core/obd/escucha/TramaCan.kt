package com.revscope.core.obd.escucha

data class TramaCan(
    val tMs: Long,
    val id: Int,
    val extendido: Boolean,
    val datos: List<Int>,
)

data class Hueco(val desdeMs: Long, val hastaMs: Long)

data class CapturaCan(
    val id: String,
    val etiqueta: String,
    val inicioEpochMs: Long,
    val duracionMs: Long,
    val protocolo: String,
    val bits29: Boolean,
    val filtro: String?,
    val silencioso: Boolean,
    val desbordes: Int,
    val errores: Int,
    val tramas: List<TramaCan>,
    val totalTramas: Int = tramas.size,
    val huecos: List<Hueco> = emptyList(),
)

object IdCan {
    fun texto(id: Int, extendido: Boolean): String =
        if (extendido) "%08X".format(id) else "%03X".format(id)
}
