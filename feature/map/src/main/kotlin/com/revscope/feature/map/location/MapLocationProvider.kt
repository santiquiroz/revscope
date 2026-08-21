package com.revscope.feature.map.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import com.revscope.core.obd.service.LiveRouteHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import timber.log.Timber

/**
 * GPS para el mapa sin viaje activo. Vive solo mientras la pantalla del mapa está
 * visible (la pantalla llama start/stop): con el mapa cerrado no consume nada.
 * Durante un viaje el service sigue siendo la fuente del puck; este provider solo
 * llena el vacío cuando no hay sesión.
 */
class MapLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _fix = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val fix: StateFlow<LiveRouteHolder.RoutePoint?> = _fix.asStateFlow()

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        seedLastKnownFix(lm)
        // API 26-29: la interfaz de plataforma aún declara estos métodos abstractos (defaults
        // llegaron en API 30) — sin overrides explícitos el framework crashea con AbstractMethodError.
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                _fix.value = LiveRouteHolder.RoutePoint(location.latitude, location.longitude)
            }
            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        // Sin permiso o sin provider GPS: el mapa queda en lastKnown, el banner de la
        // pantalla se encarga de pedirlo — acá no se revienta.
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, UPDATE_MIN_DISTANCE_M, l)
        }.onSuccess { listener = l }
    }

    fun stop() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listener?.let { lm.removeUpdates(it) }
        listener = null
    }

    /**
     * Arranque instantáneo (X1): un fix GPS frío tarda 10-30s, y hasta entonces el mapa no
     * tiene target para centrar/enganchar la cámara. `getLastKnownLocation` es sync y no
     * requiere señal — puede tener minutos de edad, pero alcanza para arrancar YA; el fix
     * real (más preciso) lo pisa apenas llega, vía el listener de arriba.
     */
    @SuppressLint("MissingPermission")
    private fun seedLastKnownFix(lm: LocationManager) {
        val seed = runCatching {
            newestOf(
                safeLastKnownLocation(lm, LocationManager.GPS_PROVIDER),
                safeLastKnownLocation(lm, LocationManager.NETWORK_PROVIDER),
            )
        }.getOrNull() ?: return
        _fix.value = LiveRouteHolder.RoutePoint(seed.latitude, seed.longitude)
        Timber.d("MapLocationProvider: seeded last-known fix, age=${System.currentTimeMillis() - seed.time}ms")
    }

    private fun safeLastKnownLocation(lm: LocationManager, provider: String): Location? =
        runCatching { lm.getLastKnownLocation(provider) }.getOrNull()

    private fun newestOf(a: Location?, b: Location?): Location? = when {
        a == null -> b
        b == null -> a
        a.time >= b.time -> a
        else -> b
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val UPDATE_MIN_DISTANCE_M = 3f
    }
}
