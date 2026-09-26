package com.revscope.core.obd.telemetry

/**
 * Frecuencia del sondeo normal (todos los PIDs). Regla única: intervalo = min(base del grupo, tope).
 * [ESTANDAR_2S] reproduce exactamente los intervalos de v1.19; [MAXIMO] no espera entre ciclos y el
 * adaptador fija la tasa real. Para ver un sensor a alta tasa está la captura rápida.
 */
enum class SamplingPreset(val topeMs: Long?, val claveMcp: String, val etiqueta: String) {
    ESTANDAR_2S(null, "estandar_2s", "Estándar (2 s)"),
    UN_SEGUNDO(1_000L, "1s", "1 s"),
    MEDIO_SEGUNDO(500L, "500ms", "500 ms"),
    CUARTO_SEGUNDO(250L, "250ms", "250 ms"),
    MAXIMO(0L, "maximo", "Máximo"),
    ;

    fun intervaloPara(baseMs: Long): Long = topeMs?.let { minOf(baseMs, it) } ?: baseMs

    companion object {
        val DEFAULT = ESTANDAR_2S

        fun desdeClave(clave: String?): SamplingPreset =
            entries.firstOrNull { it.name == clave } ?: DEFAULT

        fun desdeClaveMcp(clave: String?): SamplingPreset? =
            entries.firstOrNull { it.claveMcp.equals(clave?.trim(), ignoreCase = true) }
    }
}
