package com.revscope.core.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La navegación viva de la app: una sola a la vez, compartida entre la pantalla y el servicio
 * en primer plano.
 *
 * Es singleton a propósito. Rodando en moto el celular va en el bolsillo y la pantalla
 * apagada; si la navegación viviera en el ViewModel del mapa, se moriría al salir de la
 * pantalla y las instrucciones se callarían justo cuando son lo único que queda.
 */
@Singleton
class NavigationController @Inject constructor(
    private val voice: NavigationVoice,
) {

    private val announcer = ManeuverAnnouncer()

    private val _state = MutableStateFlow<NavigationState?>(null)
    val state: StateFlow<NavigationState?> = _state.asStateFlow()

    private var session: NavigationSession? = null

    val isNavigating: Boolean get() = session != null

    fun start(route: NavigationRoute, origin: LatLon, destination: LatLon): Boolean {
        stop()
        val started = NavigationSession.create(
            osrmRouteJson = route.osrmRouteJson,
            route = route,
            origin = origin,
            destination = destination,
            polylinePrecision = OSRM_POLYLINE_PRECISION,
        ) ?: return false
        session = started
        announcer.reset()
        Timber.i("NavigationController: navegando ${route.steps.size} pasos")
        return true
    }

    fun stop() {
        session?.close()
        session = null
        _state.value = null
        announcer.reset()
    }

    /** Cada lectura del GPS. Devuelve el estado nuevo, o null si no hay navegación activa. */
    fun onFix(fix: LocationFix): NavigationState? {
        val active = session ?: return null
        val state = runCatching { active.update(fix) }
            .onFailure { Timber.w(it, "NavigationController: la sesión falló, se detiene") }
            .getOrNull()
            ?: run { stop(); return null }
        _state.value = state
        announcer.next(state)?.let(voice::say)
        if (state.arrived) stopAfterArrival()
        return state
    }

    /** Tras llegar, el estado se queda visible un momento; lo que se apaga es el motor. */
    private fun stopAfterArrival() {
        session?.close()
        session = null
    }

    private companion object {
        /** La app pide `geometries=polyline`, que es precisión 5. Ver PolylineDecoder. */
        const val OSRM_POLYLINE_PRECISION = 5
    }
}
