package com.revscope.core.obd.escucha

object GpsCsv {

    const val CABECERA = "t_ms,velocidad_kmh,precision_m,rumbo_grados"

    fun texto(muestras: List<MuestraGps>): String =
        (listOf(CABECERA) + muestras.map(::linea)).joinToString("\n", postfix = "\n")

    fun linea(m: MuestraGps): String =
        listOf(m.tMs.toString(), campo(m.velocidadKmh), campo(m.precisionM), campo(m.rumboGrados)).joinToString(",")

    fun parsear(linea: String): MuestraGps? {
        val partes = linea.trim().split(',')
        if (partes.size != 4) return null
        val tMs = partes[0].toLongOrNull() ?: return null
        return MuestraGps(tMs, partes[1].toFloatOrNull(), partes[2].toFloatOrNull(), partes[3].toFloatOrNull())
    }

    private fun campo(valor: Float?): String = valor?.toString() ?: ""
}
