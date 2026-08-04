package com.revscope.core.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory route of the active trip, consumed by the live map. */
@Singleton
class LiveRouteHolder internal constructor(
    // Inyectable solo para tests: 0 = snapshot en cada append (semántica pre-throttle).
    private val snapshotIntervalMs: Long,
) {

    @Inject constructor() : this(SNAPSHOT_INTERVAL_MS)

    data class RoutePoint(val lat: Double, val lon: Double)

    // Buffer interno mutable: append O(1). El StateFlow materializa una copia como máximo
    // cada SNAPSHOT_INTERVAL_MS — copiar una lista de hasta 18k puntos en cada fix de 1 Hz
    // era una asignación continua de ~140 KB/s durante todo el viaje.
    private val route = ArrayDeque<RoutePoint>()

    private val _points = MutableStateFlow<List<RoutePoint>>(emptyList())
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    // route.size se estanca en MAX_POINTS en viajes largos, así que la UI no puede
    // detectar cambios mirando solo el tamaño; revision avanza con cada snapshot.
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    // Último punto sin esperar el snapshot — lo usa el SMS de emergencia de CrashResponder.
    private val _lastPoint = MutableStateFlow<RoutePoint?>(null)
    val lastPoint: StateFlow<RoutePoint?> = _lastPoint.asStateFlow()

    // Última velocidad GPS conocida — respaldo de CrashDetector cuando el enlace OBD
    // se cae (p. ej. el adaptador se desconecta justo en el impacto de una caída).
    private val _lastSpeedKmh = MutableStateFlow(0f)
    val lastSpeedKmh: StateFlow<Float> = _lastSpeedKmh.asStateFlow()

    private var lastSnapshotMs = 0L

    @Synchronized
    fun append(lat: Double, lon: Double) {
        val point = RoutePoint(lat, lon)
        route.addLast(point)
        while (route.size > MAX_POINTS) route.removeFirst()
        _lastPoint.value = point
        val now = System.currentTimeMillis()
        if (now - lastSnapshotMs >= snapshotIntervalMs) {
            lastSnapshotMs = now
            _points.value = route.toList()
            _revision.value += 1
        }
    }

    fun updateSpeed(speedKmh: Float) {
        _lastSpeedKmh.value = speedKmh
    }

    @Synchronized
    fun clear() {
        route.clear()
        _points.value = emptyList()
        _lastPoint.value = null
        _revision.value = 0L
        _lastSpeedKmh.value = 0f
        lastSnapshotMs = 0L
    }

    companion object {
        // 1 fix/s con min-distance 3 m → ~5 h de viaje; protege la memoria
        const val MAX_POINTS = 18_000
        private const val SNAPSHOT_INTERVAL_MS = 2_000L
    }
}
