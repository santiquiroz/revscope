package com.revscope.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.SunTimes
import com.revscope.core.common.stateInSafe
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.dao.SavedPlaceDao
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.SavedPlaceEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.cameras.CameraCoverageTracker
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.navigation.LatLon
import com.revscope.core.navigation.NavigationController
import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.navigation.NavigationState
import com.revscope.feature.map.location.MapLocationProvider
import com.revscope.feature.map.routing.OsrmRouteFetcher
import com.revscope.feature.map.routing.RerouteDecider
import com.revscope.feature.map.search.PhotonGeocoder
import com.revscope.feature.map.search.PlaceResult
import com.revscope.feature.map.social.RankingCalc
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
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
    private val locationProvider: MapLocationProvider,
    private val coverageTracker: CameraCoverageTracker,
    private val savedPlaceDao: SavedPlaceDao,
    private val settings: DataStore<Preferences>,
) : ViewModel() {

    // ── Navegación paso a paso ───────────────────────────────────────────────

    /** Estado de la navegación viva. Vive en el controlador, no aquí: sigue con la pantalla apagada. */
    val navigation: StateFlow<NavigationState?> = navigationController.state

    /** Arranca la guía por voz sobre la ruta ya calculada. Sin viaje activo, lo inicia. */
    fun startNavigation() {
        val route = _plannedRoute.value ?: return
        val origin = lastKnownPoint() ?: return
        val destination = _destination.value ?: return
        // La navegación recibe el GPS del servicio en primer plano; si no hay viaje,
        // se arranca uno GPS aquí mismo — un tap, como Google Maps. startGpsSession()
        // es no-op si ya hay sesión o el OBD está conectando/conectado.
        if (sessionManager.currentSessionId.value == null) {
            sessionManager.startGpsSession()
            // startGpsSession() marca isGpsSessionActive de forma SÍNCRONA (antes de su
            // scope.launch interno) pero currentSessionId recién queda seteado async, tras
            // el insert suspend en Room — comprobar solo currentSessionId acá daría un falso
            // "no arrancó" en el camino feliz (primer viaje, sin sesión previa). isGpsSessionActive
            // sí distingue ese caso de un no-op real (OBD Connecting/Connected — ver
            // ObdSessionManager.startGpsSession), que es cuando la nav arrancaría muda.
            if (sessionManager.currentSessionId.value == null && !sessionManager.isGpsSessionActive.value) {
                _navigationError.value = "Esperando el GPS — reintentá en unos segundos"
                return
            }
        }
        val started = navigationController.start(
            route = route,
            origin = LatLon(origin.lat, origin.lon),
            destination = LatLon(destination.lat, destination.lon),
        )
        if (!started) _navigationError.value = "No se pudo iniciar la navegación"
    }

    fun stopNavigation() {
        rerouteJob?.cancel()
        navigationController.stop()
    }

    private fun maybeReroute(state: NavigationState) {
        if (!state.offRoute) { rerouteDecider.shouldReroute(false, System.currentTimeMillis()); return }
        if (rerouteJob?.isActive == true) return
        if (!rerouteDecider.shouldReroute(true, System.currentTimeMillis())) return
        val current = route.value.lastOrNull()?.let { LatLon(it.lat, it.lon) }
            ?: locationProvider.fix.value?.let { LatLon(it.lat, it.lon) }
            ?: state.snapped
            ?: return
        val destination = _destination.value ?: return
        rerouteJob = viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                OsrmRouteFetcher.fetch(current.lat, current.lon, destination.lat, destination.lon)
            } ?: return@launch // falla de red: la ruta vieja sigue; el cooldown regula el reintento
            // Guard de staleness: si mientras viajaba el fetch el usuario paró la navegación
            // o cambió el destino, este resultado ya no manda.
            if (!navigationController.isNavigating) return@launch
            if (_destination.value != destination) return@launch
            _plannedRoute.value = fresh
            navigationController.swap(
                route = fresh,
                origin = current,
                destination = LatLon(destination.lat, destination.lon),
            )
        }
    }

    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError: StateFlow<String?> = _navigationError.asStateFlow()

    fun clearNavigationError() {
        _navigationError.value = null
    }

    private val rerouteDecider = RerouteDecider()
    private var rerouteJob: Job? = null

    // ── Rodada en grupo ──────────────────────────────────────────────────────

    val roomCode: StateFlow<String?> = roomClient.roomCode
    val peers: StateFlow<Map<String, RoomClient.Peer>> = roomClient.peers
    val roomState: StateFlow<RoomClient.RoomState> = roomClient.roomState

    val sharedDest: StateFlow<RoomClient.SharedDest?> = roomState
        .map { it.dest }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selfRiderName: StateFlow<String> = settings.data
        .map { it[PreferencesKeys.SERVER_RIDER_NAME]?.trim().orEmpty().ifBlank { "Vos" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Vos")

    // Recalcula en cada emisión de peers (posiciones ~1 Hz) o de roomState (destino nuevo,
    // sala legacy) — la posición/velocidad propia se lee como snapshot en ese instante,
    // igual que hace lastKnownPoint() para el ruteo (M4 no aplica: esto no dispara red).
    val ranking: StateFlow<List<RankingCalc.Entry>> = combine(peers, roomState) { peersMap, state ->
        val dest = state.dest
        if (state.legacyServer || dest == null) return@combine emptyList()
        val self = lastKnownPoint()?.let { selfRiderName.value to it }
        RankingCalc.rank(
            self = self,
            selfSpeedKmh = speedKmh.value?.toDouble(),
            peers = peersMap.values,
            dest = dest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** Propone mi destino fijado a toda la sala — botón "Compartir con la sala" del chip de ruta. */
    fun shareCurrentDestination() {
        val dest = _destination.value ?: return
        roomClient.shareDestination(dest.lat, dest.lon, _destinationName.value ?: "Destino")
    }

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

    // Debe correr ANTES de que se construya darkTiles (más abajo): su stateIn lee
    // initialCenter.value de forma síncrona para el valor inicial (M6) — los init{}
    // corren en orden textual, así que este bloque tiene que preceder esa declaración.
    init { loadLastKnownLocation() }

    // ── GPS vivo sin viaje ───────────────────────────────────────────────────

    /** Fix del provider del mapa — null sin permiso, sin señal o con el mapa cerrado. */
    val liveFix: StateFlow<LiveRouteHolder.RoutePoint?> = locationProvider.fix

    fun onMapVisible() = locationProvider.start()

    fun onMapHidden() = locationProvider.stop()

    // ── Modo nocturno ────────────────────────────────────────────────────────

    val nightMode: StateFlow<String> = settings.data
        .map { it[PreferencesKeys.MAP_NIGHT_MODE] ?: "auto" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "auto")

    /** Tick de un minuto: en modo auto el atardecer conmuta sin tocar nada. */
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_TICK_MS)
        }
    }

    val darkTiles: StateFlow<Boolean> = combine(nightMode, liveFix, initialCenter, minuteTick) { mode, fix, center, _ ->
        when (mode) {
            "on" -> true
            "off" -> false
            else -> {
                val at = fix ?: center
                if (at == null) false else SunTimes.isNight(at.lat, at.lon, System.currentTimeMillis())
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // nightMode arranca en "auto" (ver default de arriba): si ya hay lastKnown disponible
        // (loadLastKnownLocation corrió antes, ver init{} de arriba) evaluamos SunTimes ya
        // mismo y evitamos el flash claro al abrir de noche. "on" persistido en DataStore
        // igual tarda una emisión del combine — aceptado, documentado (M6).
        initialCenter.value?.let { SunTimes.isNight(it.lat, it.lon, System.currentTimeMillis()) } ?: false,
    )

    fun cycleNightMode() {
        val next = when (nightMode.value) {
            "auto" -> "on"
            "on" -> "off"
            else -> "auto"
        }
        viewModelScope.launch { settings.edit { it[PreferencesKeys.MAP_NIGHT_MODE] = next } }
    }

    override fun onCleared() {
        locationProvider.stop()
    }

    val speedKmh: StateFlow<Int?> = sessionManager.readings
        .map { (it["0D"] ?: it["GPS_SPEED"])?.value?.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // ── Ruta a destino (OSRM) ────────────────────────────────────────────────

    private val _destination = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val destination: StateFlow<LiveRouteHolder.RoutePoint?> = _destination.asStateFlow()

    // Nombre legible del destino fijado, si se conoce (búsqueda, lugar guardado o propuesta
    // de sala) — null en un long-press del mapa. Solo lo consume shareCurrentDestination().
    private val _destinationName = MutableStateFlow<String?>(null)

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

    /** Elegir un resultado reusa el mismo camino que el long-press y entra al historial. */
    fun selectSearchResult(place: PlaceResult) {
        clearSearch()
        setDestination(place.lat, place.lon, place.name)
        viewModelScope.launch {
            savedPlaceDao.recordRecent(
                SavedPlaceEntity(
                    type = "RECENT",
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun selectSavedPlace(place: SavedPlaceEntity) {
        clearSearch()
        setDestination(place.lat, place.lon, place.name)
        viewModelScope.launch { savedPlaceDao.touch(place.id, System.currentTimeMillis()) }
    }

    fun saveHome(place: PlaceResult) = saveSpecial("HOME", place)

    fun saveWork(place: PlaceResult) = saveSpecial("WORK", place)

    fun saveFavorite(place: PlaceResult) {
        viewModelScope.launch {
            if (savedPlaceDao.countFavorite(place.name, place.lat, place.lon) > 0) return@launch
            savedPlaceDao.insert(
                SavedPlaceEntity(type = "FAVORITE", name = place.name, lat = place.lat, lon = place.lon, lastUsedAt = System.currentTimeMillis()),
            )
        }
    }

    fun removePlace(id: Long) {
        viewModelScope.launch { savedPlaceDao.delete(id) }
    }

    private fun saveSpecial(type: String, place: PlaceResult) {
        viewModelScope.launch {
            savedPlaceDao.upsertSpecial(
                SavedPlaceEntity(
                    type = type,
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun lastKnownPoint(): LiveRouteHolder.RoutePoint? =
        route.value.lastOrNull() ?: locationProvider.fix.value ?: _initialCenter.value

    // Incrementada en cada setDestination(); guarda contra dos selects rápidos aterrizando
    // fuera de orden — el fetch más viejo se descarta si ya no es la generación vigente (M4).
    private var routingGeneration = 0L

    /**
     * Fija destino y pide la ruta a OSRM desde la posición actual — long-press del mapa,
     * selección de búsqueda/lugar guardado, o aceptar la propuesta de un peer. [name] es el
     * nombre legible del lugar cuando se conoce (null en un long-press).
     */
    fun setDestination(lat: Double, lon: Double, name: String? = null) {
        // Navegando no se cambia el destino con un toque: pará la navegación primero.
        if (navigationController.isNavigating) return
        val origin = route.value.lastOrNull() ?: locationProvider.fix.value ?: _initialCenter.value ?: return
        // Incremento y comparación deben quedar los dos main-confined: si el launch entero
        // corre en Dispatchers.IO (como antes) no hay edge de visibilidad garantizado entre
        // el ++ de acá y la lectura de otro hilo — solo el withContext interno toca IO.
        val generation = ++routingGeneration
        _destination.value = LiveRouteHolder.RoutePoint(lat, lon)
        _destinationName.value = name
        _plannedRoute.value = null
        _routing.value = true
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                OsrmRouteFetcher.fetch(origin.lat, origin.lon, lat, lon)
            }
            if (generation != routingGeneration) return@launch
            _plannedRoute.value = fetched
            _routing.value = false
        }
    }

    fun clearDestination() {
        rerouteJob?.cancel()
        navigationController.stop()
        _destination.value = null
        _destinationName.value = null
        _plannedRoute.value = null
        _routing.value = false
    }

    // Flow y no one-shot: este ViewModel sobrevive los cambios de tab (restoreState),
    // así que una lectura única dejaba el mapa ciego a descargas posteriores.
    val cameras: StateFlow<List<SpeedCameraEntity>> = cameraDao.observeAll()
        .stateInSafe(viewModelScope, emptyList())

    val potholes: StateFlow<List<PotholeEntity>> = potholeDao.observeAll()
        .stateInSafe(viewModelScope, emptyList())

    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = savedPlaceDao.observeAll()
        .stateInSafe(viewModelScope, emptyList())

    init {
        // loadLastKnownLocation() ya corrió en el init{} de más arriba (antes de darkTiles).
        // El tracker ya trae throttle/cooldown/chequeo de cobertura: acá solo se le
        // entregan los fixes que antes solo veía durante un viaje activo.
        viewModelScope.launch {
            locationProvider.fix.filterNotNull().collect { coverageTracker.onGpsFix(it.lat, it.lon) }
        }
        viewModelScope.launch {
            navigationController.state.collect { state ->
                if (state == null) { rerouteDecider.reset(); return@collect }
                maybeReroute(state)
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val MINUTE_TICK_MS = 60_000L
    }
}
