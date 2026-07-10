package com.revscope.core.obd.service

/**
 * Sink genérico de fixes GPS consumido por alertas que viven fuera de :core:obd (ej.
 * CityInfoAlerter en :core:intelligence, que llama a la API de Claude). :core:obd no
 * puede referenciar esa clase directamente — :core:intelligence ya depende de
 * :core:obd, y la relación inversa crearía un ciclo de dependencias Gradle — así que
 * [GpsTrackRecorder]/[ObdForegroundService] reciben esta interfaz en su lugar. El
 * binding real (GpsInfoSink → CityInfoAlerter) se resuelve en el módulo Hilt de :app,
 * el único que ve ambos módulos a la vez.
 */
fun interface GpsInfoSink {
    fun onGpsFix(latitude: Double, longitude: Double)
}
