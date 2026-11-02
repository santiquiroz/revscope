package com.revscope.feature.workshop.taller.capturacan

import com.revscope.core.obd.escucha.CapturaRutaCan
import com.revscope.core.obd.escucha.EstadoCapturaRuta
import kotlin.math.roundToInt

data class CapturaRutaUi(
    val estado: EstadoCapturaRuta,
    val conectado: Boolean,
    val etiqueta: String,
    val permisoGps: Boolean,
) {
    val capturando: Boolean get() = estado is EstadoCapturaRuta.Capturando
    val botonHabilitado: Boolean get() = capturando || conectado
    val textoBoton: String get() = if (capturando) "Detener y guardar" else "Iniciar captura"
}

internal object FormatoCapturaRuta {

    val tope: String = cronometro(CapturaRutaCan.MAX_RUTA_MS)

    fun cronometro(ms: Long): String {
        val segundos = ms.coerceAtLeast(0) / 1_000
        return "%02d:%02d".format(segundos / 60, segundos % 60)
    }

    fun velocidad(kmh: Float?): String = kmh?.let { "${it.roundToInt()} km/h" } ?: "sin señal"

    fun resumenTerminada(t: EstadoCapturaRuta.Terminada): String {
        val gps = if (t.muestrasGps == 0) "sin muestras GPS" else "${t.muestrasGps} muestras GPS"
        return "${t.tramas} tramas · $gps · ${cronometro(t.duracionMs)}"
    }
}
