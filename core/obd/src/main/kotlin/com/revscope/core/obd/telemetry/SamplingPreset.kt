package com.revscope.core.obd.telemetry

import androidx.annotation.StringRes
import com.revscope.core.obd.R
/**
 * Frecuencia del sondeo normal (todos los PIDs). Regla única: intervalo = min(base del grupo, tope).
 * [ESTANDAR_2S] reproduce exactamente los intervalos de v1.19; [MAXIMO] no espera entre ciclos y el
 * adaptador fija la tasa real. Para ver un sensor a alta tasa está la captura rápida.
 */
enum class SamplingPreset(val topeMs: Long?, val claveMcp: String, @StringRes val etiquetaRes: Int) {
    ESTANDAR_2S(null, "estandar_2s", R.string.obd_sampling_standard),
    UN_SEGUNDO(1_000L, "1s", R.string.obd_sampling_one_second),
    MEDIO_SEGUNDO(500L, "500ms", R.string.obd_sampling_half_second),
    CUARTO_SEGUNDO(250L, "250ms", R.string.obd_sampling_quarter_second),
    MAXIMO(0L, "maximo", R.string.obd_sampling_maximum),
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
