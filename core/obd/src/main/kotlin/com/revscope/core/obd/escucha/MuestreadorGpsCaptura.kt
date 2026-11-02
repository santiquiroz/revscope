package com.revscope.core.obd.escucha

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MuestreadorGpsCaptura @Inject constructor(@ApplicationContext private val context: Context) {

    private val muestras = mutableListOf<MuestraGps>()
    private var listener: LocationListener? = null
    private var inicio = 0L

    @Volatile
    var velocidadActualKmh: Float? = null
        private set

    fun tienePermiso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun iniciar(inicioEpochMs: Long): Boolean {
        if (listener != null) return true
        muestras.clear()
        velocidadActualKmh = null
        inicio = inicioEpochMs
        if (!tienePermiso()) return false
        val gestor = gestor() ?: return false
        val nuevo = escucha()
        return try {
            // Distancia mínima 0: detenido en un semáforo también interesa la muestra de 0 km/h.
            gestor.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVALO_MS, 0f, nuevo, context.mainLooper)
            listener = nuevo
            true
        } catch (e: Exception) {
            Timber.w(e, "MuestreadorGpsCaptura: GPS no disponible")
            false
        }
    }

    @Synchronized
    fun detener(): List<MuestraGps> {
        listener?.let { activo -> runCatching { gestor()?.removeUpdates(activo) } }
        listener = null
        velocidadActualKmh = null
        return muestras.toList()
    }

    @Synchronized
    private fun registrar(location: Location) {
        val muestra = MuestraGps(
            tMs = System.currentTimeMillis() - inicio,
            velocidadKmh = if (location.hasSpeed()) location.speed * MPS_A_KMH else null,
            precisionM = if (location.hasAccuracy()) location.accuracy else null,
            rumboGrados = if (location.hasBearing()) location.bearing else null,
        )
        muestras += muestra
        velocidadActualKmh = muestra.velocidadKmh
    }

    private fun gestor(): LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // API 26-29 declaran estos métodos abstractos: sin overrides el framework lanza AbstractMethodError.
    private fun escucha() = object : LocationListener {
        override fun onLocationChanged(location: Location) = registrar(location)

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private companion object {
        const val INTERVALO_MS = 1_000L
        const val MPS_A_KMH = 3.6f
    }
}
