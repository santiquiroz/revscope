package com.revscope.feature.map.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.revscope.core.obd.service.LiveRouteHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

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
        val l = LocationListener { location: Location ->
            _fix.value = LiveRouteHolder.RoutePoint(location.latitude, location.longitude)
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

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val UPDATE_MIN_DISTANCE_M = 3f
    }
}
