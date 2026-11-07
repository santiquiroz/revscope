package com.revscope.feature.workshop.taller.capturacan

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.escucha.CapturaRutaCan
import com.revscope.core.obd.escucha.EstadoCapturaRuta
import com.revscope.feature.workshop.R
import kotlin.math.roundToInt

data class CapturaRutaUi(
    val estado: EstadoCapturaRuta,
    val conectado: Boolean,
    val etiqueta: String,
    val permisoGps: Boolean,
) {
    val capturando: Boolean get() = estado is EstadoCapturaRuta.Capturando
    val botonHabilitado: Boolean get() = capturando || conectado

    @get:StringRes
    val textoBoton: Int
        get() = if (capturando) R.string.workshop_can_capture_stop_and_save else R.string.workshop_can_capture_start
}

internal object FormatoCapturaRuta {

    val tope: String = cronometro(CapturaRutaCan.MAX_RUTA_MS)

    fun cronometro(ms: Long): String {
        val segundos = ms.coerceAtLeast(0) / 1_000
        return "%02d:%02d".format(segundos / 60, segundos % 60)
    }

    fun velocidad(kmh: Float?, resolver: StringResolver): String =
        kmh?.let { resolver.get(R.string.workshop_can_capture_speed_kmh, it.roundToInt()) }
            ?: resolver.get(R.string.workshop_can_capture_no_signal)

    fun resumenTerminada(t: EstadoCapturaRuta.Terminada, resolver: StringResolver): String {
        val gps = if (t.muestrasGps == 0) {
            resolver.get(R.string.workshop_can_capture_no_gps_samples)
        } else {
            resolver.plural(R.plurals.workshop_can_capture_gps_samples, t.muestrasGps, t.muestrasGps)
        }
        return resolver.plural(R.plurals.workshop_can_capture_summary, t.tramas, t.tramas, gps, cronometro(t.duracionMs))
    }
}
