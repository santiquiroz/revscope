package com.revscope.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.navigation.LatLon
import com.revscope.core.navigation.NavigationController
import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.navigation.NavigationState
import com.revscope.feature.map.routing.OsrmRouteFetcher
import com.revscope.feature.map.search.PhotonGeocoder
import com.revscope.feature.map.search.PlaceResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    routeHolder: LiveRouteHolder,
    private val cameraDao: SpeedCameraDao,
    private val potholeDao: PotholeDao,
    private val sessionManager: ObdSessionManager,
    cameraAlerter: SpeedCameraAlerter,
    private val roomClient: RoomClient,
    private val navigationController: NavigationController,
) : ViewModel() {

    // ── Navegación paso a paso ───────────────────────────────────────────────

    /** Estado de la navegación viva. Vive en el controlador, no aquí: sigue con la pantalla apagada. */
    val navigation: StateFlow<NavigationState?> = navigationController.state

    /** Arranca la guía por voz sobre la ruta ya calculada. */
    fun startNavigation() {
        val route = _plannedRoute.value ?: return
        val origin = lastKnownPoint() ?: return
        val destination = _destination.value ?: return
        // El GPS lo entrega el servicio en primer plano, y ese solo corre con un viaje activo.
        // Sin viaje, la navegación arrancaría y se quedaría muda: mejor decirlo.
        if (sessionManager.currentSessionId.value == null) {
            _navigationError.value = "Inicia un viaje para que la navegación reciba el GPS"
            return
        }
        val started = navigationController.start(
            route = route,
            origin = LatLon(origin.lat, origin.lon),
            destination = LatLon(destination.lat, destination.lon),
        )
        if (!started) _navigationError.value = "No se pudo iniciar la navegación"
    }

    fun stopNavigation() = navigationController.stop()

    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError: StateFlow<String?> = _navigationError.asStateFlow()

    fun clearNavigationError() {
        _navigationError.value = null
    }

    // ── Rodada en grupo ──────────────────────────────────────────────────────

    val roomCode: StateFlow<String?> = roomClient.roomCode
    val peers: StateFlow<Map<String, RoomClient.Peer>> = roomClient.peers

    private val _roomBusy = MutableStateFlow(false)
    val roomBusy: StateFlow<Boolean> = _roomBusy.asStateFlow()

    fun createRoom(onCode: (String?) -> Unit) {
        viewModelScope.launch {
            _roomBusy.value = true
            onCode(roomClient.createAndJoin())
            _roomBusy.value = false
        }
    }

    fun joinRoom(code: String) = roomClient.join(code)

    fun leaveRoom() = roomClient.leave()

    /** Radar hacia el que se dirige el vehículo (cono ±60°, <1 km) — null si ninguno aplica. */
    val approachingCamera: StateFlow<SpeedCameraAlerter.ApproachingCamera?> = cameraAlerter.approaching

    /** Radio configurado del aviso de radar — el círculo del mapa dibuja el mismo valor que dispara la voz. */
    val cameraAlertRadiusM: StateFlow<Int> = cameraAlerter.alertRadiusM

    val route: StateFlow<List<LiveRouteHolder.RoutePoint>> = routeHolder.points

    // route.size se estanca al llegar al tope de puntos; revision avanza siempre que
    // la ruta cambia, incluso cuando el tamaño no cambia (viajes largos).
    val routeRevision: StateFlow<Long> = routeHolder.revision

    // Centro inicial cuando no hay viaje activo (mismo patrón que SettingsViewModel)
    private val _initialCenter = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val initialCenter: StateFlow<LiveRouteHolder.RoutePoint?> = _initialCenter.asStateFlow()

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation() {
        runCatching {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()?.let {
            _initialCenter.value = LiveRouteHolder.RoutePoint(it.latitude, it.longitude)
        }
    }

    val speedKmh: StateFlow<Int?> = sessionManager.readings
        .map { it["0D"]?.value?.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Ruta a destino (OSRM) ────────────────────────────────────────────────

    private val _destination = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val destination: StateFlow<LiveRouteHolder.RoutePoint?> = _destination.asStateFlow()

    private val _plannedRoute = MutableStateFlow<NavigationRoute?>(null)
    val plannedRoute: StateFlow<NavigationRoute?> = _plannedRoute.asStateFlow()

    private val _routing = MutableStateFlow(false)
    val routing: StateFlow<Boolean> = _routing.asStateFlow()

    // ── Búsqueda de direcciones ──────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PlaceResult>>(emptyList())
    val searchResults: StateFlow<List<PlaceResult>> = _searchResults.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().length < PhotonGeocoder.MIN_QUERY_LENGTH) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }
        _searching.value = true
        searchJob = viewModelScope.launch {
            // Photon es un servicio público y gratuito: una consulta por pulsación es abusar.
            delay(SEARCH_DEBOUNCE_MS)
            val bias = lastKnownPoint()
            val results = withContext(Dispatchers.IO) {
                PhotonGeocoder.search(query, bias?.lat, bias?.lon)
            }
            _searchResults.value = results
            _searching.value = false
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searching.value = false
    }

    /** Elegir un resultado reusa el mismo camino que el long-press en el mapa. */
    fun selectSearchResult(place: PlaceResult) {
        clearSearch()
        setDestination(place.lat, place.lon)
    }

    private fun lastKnownPoint(): LiveRouteHolder.RoutePoint? =
        route.value.lastOrNull() ?: _initialCenter.value

    /** Long-press en el mapa: fija destino y pide la ruta a OSRM desde la posición actual. */
    fun setDestination(lat: Double, lon: Double) {
        val origin = route.value.lastOrNull() ?: _initialCenter.value ?: return
        _destination.value = LiveRouteHolder.RoutePoint(lat, lon)
        _plannedRoute.value = null
        _routing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val fetched = OsrmRouteFetcher.fetch(origin.lat, origin.lon, lat, lon)
            _plannedRoute.value = fetched
            _routing.value = false
        }
    }

    fun clearDestination() {
        navigationController.stop()
        _destination.value = null
        _plannedRoute.value = null
        _routing.value = false
    }

    // Flow y no one-shot: este ViewModel sobrevive los cambios de tab (restoreState),
    // así que una lectura única dejaba el mapa ciego a descargas posteriores.
    val cameras: StateFlow<List<SpeedCameraEntity>> = cameraDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val potholes: StateFlow<List<PotholeEntity>> = potholeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadLastKnownLocation()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
