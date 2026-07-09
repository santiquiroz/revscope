package com.revscope.core.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory route of the active trip, consumed by the live map. */
@Singleton
class LiveRouteHolder @Inject constructor() {

    data class RoutePoint(val lat: Double, val lon: Double)

    private val _points = MutableStateFlow<List<RoutePoint>>(emptyList())
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    fun append(lat: Double, lon: Double) {
        _points.value = (_points.value + RoutePoint(lat, lon)).takeLast(MAX_POINTS)
    }

    fun clear() {
        _points.value = emptyList()
    }

    companion object {
        // 1 fix/s con min-distance 3 m → ~5 h de viaje; protege la memoria
        const val MAX_POINTS = 18_000
    }
}
