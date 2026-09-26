package com.revscope.core.obd.taller.pruebas

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.coroutines.resume

fun interface LectorAmbiente {
    suspend fun leer(): LecturasAmbiente
}

// La altitud del último fix sirve si es reciente: con el motor apagado en el taller no hay fix nuevo.
object AltitudVigente {
    const val VIGENCIA_MS = 60 * 60_000L

    fun de(altitudM: Double?, fixEpochMs: Long, ahoraEpochMs: Long): Double? =
        altitudM?.takeIf { ahoraEpochMs - fixEpochMs in 0..VIGENCIA_MS }
}

// Barómetro del teléfono (si lo tiene) y la altitud del último fix GPS; sin permiso de ubicación, sin altitud.
class LectorAmbienteAndroid @Inject constructor(@ApplicationContext private val context: Context) : LectorAmbiente {

    override suspend fun leer(): LecturasAmbiente = LecturasAmbiente(presionHpa(), altitudM())

    private suspend fun presionHpa(): Double? {
        val sensores = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return null
        val barometro = sensores.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return null
        return withTimeoutOrNull(ESPERA_BAROMETRO_MS) { primeraLectura(sensores, barometro) }
    }

    private suspend fun primeraLectura(sensores: SensorManager, barometro: Sensor): Double = suspendCancellableCoroutine { cont ->
        val oyente = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                sensores.unregisterListener(this)
                if (cont.isActive) cont.resume(event.values[0].toDouble())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensores.registerListener(oyente, barometro, SensorManager.SENSOR_DELAY_NORMAL)
        cont.invokeOnCancellation { sensores.unregisterListener(oyente) }
    }

    @SuppressLint("MissingPermission")
    private fun altitudM(): Double? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val ubicacion = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val fix = runCatching { ubicacion.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull() ?: return null
        return AltitudVigente.de(fix.altitude.takeIf { fix.hasAltitude() }, fix.time, System.currentTimeMillis())
    }

    private companion object {
        const val ESPERA_BAROMETRO_MS = 2_000L
    }
}
