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
    val origen: String = ORIGEN_MCP,
    val muestrasGps: Int = 0,
) {
    companion object {
        const val ORIGEN_MCP = "mcp"
        const val ORIGEN_APP = "app"
    }
}

data class MuestraGps(
    val tMs: Long,
    val velocidadKmh: Float?,
    val precisionM: Float?,
    val rumboGrados: Float?,
)

object IdCan {
    fun texto(id: Int, extendido: Boolean): String =
        if (extendido) "%08X".format(id) else "%03X".format(id)
}
