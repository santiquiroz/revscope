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
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.maps.LocalMapReadiness
import com.revscope.core.maps.MapDownloadService
import com.revscope.core.maps.MapStyleProvider
import com.revscope.core.obd.cameras.CameraCoverageTracker
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.navigation.LatLon
import com.revscope.core.navigation.NavigationController
import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.navigation.NavigationState
import com.revscope.core.navigation.NavigationVoice
import com.revscope.feature.map.location.MapLocationProvider
import com.revscope.feature.map.routing.OsrmRouteFetcher
import com.revscope.feature.map.routing.RerouteDecider
import com.revscope.feature.map.search.PhotonGeocoder
import com.revscope.feature.map.search.PlaceResult
import com.revscope.feature.map.social.ArrivalLedger
import com.revscope.feature.map.social.RaceArrival
import com.revscope.feature.map.social.RaceCountdown
import com.revscope.feature.map.social.RankingCalc
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LiveMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    routeHolder: LiveRouteHolder,
    private val cameraDao: SpeedCameraDao,
    private val potholeDao: PotholeDao,
    private val sessionManager: ObdSessionManager,
    cameraAlerter: SpeedCameraAlerter,
    private val roomClient: RoomClient,
    // Reusa el mismo canal que la navegación (AlertsEngine, vía NavigationVoiceModule) para
    // el anuncio de llegada de carrera — es una interfaz de un solo método ya provista a nivel
    // singleton, así que inyectarla directo acá es más barato que abrir un puerto nuevo en el VM.
    private val navigationVoice: NavigationVoice,
    private val navigationController: NavigationController,
    private val locationProvider: MapLocationProvider,
    private val coverageTracker: CameraCoverageTracker,
    private val savedPlaceDao: SavedPlaceDao,
    private val settings: DataStore<Preferences>,
    private val mapDownloadService: MapDownloadService,
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
            // Las alternativas viejas partían de otro origen — ya no aplican a esta ruta.
            _routeAlternatives.value = emptyList()
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

    /** Tick liviano SOLO para re-evaluar staleness de peers (F5) — RoomClient re-filtra su
     * mapa al recibir un `pos` nuevo, pero si TODOS se detienen (p. ej. llegaron y
     * estacionaron) nadie dispara ese filtro nunca más y un peer muerto queda "vivo" para
     * siempre en el mapa y en el ranking. */
    private val peerStaleTick = flow {
        while (true) {
            emit(Unit)
            delay(PEER_STALE_CHECK_MS)
        }
    }

    /** Peers de la sala con staleness re-evaluada cada [PEER_STALE_CHECK_MS] — flatMapLatest
     * sobre roomCode: el tick solo corre con sala activa. El collector permanente del ranking
     * (init) lo mantiene vivo también en background mientras haya sala — a propósito: el TTS
     * de llegada debe sonar con pantalla apagada y necesita el prune activo (costo: un filter
     * cada 10s). Los llegados del ArrivalLedger (F1) son sticky y no dependen de esto. */
    val peers: StateFlow<Map<String, RoomClient.Peer>> = roomCode.flatMapLatest { code ->
        if (code == null) flowOf(emptyMap())
        else combine(roomClient.peers, peerStaleTick) { map, _ -> pruneStalePeers(map) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun pruneStalePeers(map: Map<String, RoomClient.Peer>): Map<String, RoomClient.Peer> {
        val now = System.currentTimeMillis()
        return map.filterValues { now - it.seenAtMs < RoomClient.PEER_STALE_MS }
    }

    val roomState: StateFlow<RoomClient.RoomState> = roomClient.roomState

    /** Modo fantasma (F3): mientras esté activo, mi posición no se retransmite a la sala. Sin
     * persistencia — vive en RoomClient, se resetea con el proceso como el resto de su estado. */
    val ghost: StateFlow<Boolean> = roomClient.ghost

    fun setGhost(enabled: Boolean) = roomClient.setGhost(enabled)

    val sharedDest: StateFlow<RoomClient.SharedDest?> = roomState
        .map { it.dest }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Mismo fallback "rider" que ServerClient.config() — si difiere, el filtro de "dest
    // propio" del banner (LiveMapScreen) nunca matchea para quien no configuró apodo.
    val selfRiderName: StateFlow<String> = settings.data
        .map { it[PreferencesKeys.SERVER_RIDER_NAME]?.trim().orEmpty().ifBlank { "rider" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "rider")

    /** Nombre real asignado por el server (F6) — trae el sufijo si hubo colisión con otro
     * rider ya en la sala. Cae a [selfRiderName] con un server viejo que todavía no manda
     * `you` en `room_state`. Reemplaza a selfRiderName en toda comparación de identidad de
     * sala (ranking propio, botón Detener carrera, filtro del banner de destino). */
    val effectiveSelfName: StateFlow<String> = combine(roomState, selfRiderName) { state, local ->
        state.you ?: local
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "rider")

    // Recalcula en cada emisión de peers (posiciones ~1 Hz) o de roomState (destino nuevo,
    // sala legacy) — la posición/velocidad propia se lee como snapshot en ese instante,
    // igual que hace lastKnownPoint() para el ruteo (M4 no aplica: esto no dispara red).
    // Crudo: sin memoria de carrera, es el insumo del ArrivalLedger (F1) — no se expone
    // directo, ver `ranking` más abajo.
    private val rawRanking: StateFlow<List<RankingCalc.Entry>> = combine(peers, roomState) { peersMap, state ->
        val dest = state.dest
        if (state.legacyServer || dest == null) return@combine emptyList()
        val self = lastKnownPoint()?.let { (state.you ?: selfRiderName.value) to it }
        RankingCalc.rank(
            self = self,
            selfSpeedKmh = speedKmh.value?.toDouble(),
            peers = peersMap.values,
            dest = dest,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _ranking = MutableStateFlow<List<RankingCalc.Entry>>(emptyList())

    /** Posiciones de la sala respecto del destino compartido, CON el orden real de cruce
     * aplicado (F1: ArrivalLedger) — llegados reales primero en su orden real, sticky durante
     * toda la carrera. Se recalcula en el mismo collector que alimenta [arrivalLedgerState],
     * ver el init{} de más abajo. */
    val ranking: StateFlow<List<RankingCalc.Entry>> = _ranking.asStateFlow()

    // ── Carreras de sala (F4) ────────────────────────────────────────────────

    fun startRace() = roomClient.startRace()

    fun stopRace() = roomClient.stopRace()

    /** Tick liviano para el countdown — RaceCountdown.secondsToShow cambia de a 1 s, pero
     * muestrear a 250 ms evita un "salto" visible al cruzar cada segundo. */
    private val raceClockTick = flow {
        while (true) {
            emit(Unit)
            delay(RACE_CLOCK_TICK_MS)
        }
    }

    /** null = sin overlay. 0 = "¡YA!". 1..5 = segundos restantes para la largada. flatMapLatest
     * sobre roomState: sin carrera no hay reloj corriendo. El inner flow además se completa solo
     * — vía RaceCountdown.isFinished — apenas se pasa la ventana de visibilidad, así que tampoco
     * queda tiqueando cada 250 ms el resto de una carrera larga que nadie detuvo todavía; el
     * último valor emitido (null) es justamente el que deja el overlay oculto. */
    val raceCountdown: StateFlow<Int?> = roomState.flatMapLatest { state ->
        val race = state.race ?: return@flatMapLatest flowOf<Int?>(null)
        raceClockTick.transformWhile {
            val now = System.currentTimeMillis()
            emit(RaceCountdown.secondsToShow(race.startAtMs, now))
            !RaceCountdown.isFinished(race.startAtMs, now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Estado de ESTA sesión de UI para "Llegaste, posición N" (no de RoomClient/RankingCalc,
    // que son puros) — el reductor en sí (RaceArrival.step) es puro y testeado; acá solo vive
    // el último estado devuelto, igual de simple que milAnnouncedThisSession en AlertsEngine.
    private var raceArrivalState = RaceArrival.State()

    // Registro real de orden de llegada (F1) — mismo patrón que raceArrivalState: el reductor
    // (ArrivalLedger.step) es puro y testeado, acá solo vive el último estado devuelto. Se
    // pisa en el mismo collector que recalcula `ranking`, ver init{} más abajo.
    private var arrivalLedgerState = ArrivalLedger.State()

    /** [entries] ya viene con el orden real de cruce aplicado (F1: ArrivalLedger, ver el
     * collector en init{}) — el número anunciado es ese orden real, no un índice de lista.
     * Sin posición propia todavía (carrera activa pero sin fix GPS) se salta el tick entero en
     * vez de sembrar un `arrived = false` que podría ser falso. */
    private fun maybeAnnounceArrival(
        entries: List<RankingCalc.Entry>,
        race: RoomClient.RaceState?,
        nowMs: Long,
    ) {
        val self = entries.firstOrNull { it.isSelf }
        if (race != null && self == null) return
        val (nextState, shouldAnnounce) = RaceArrival.step(
            state = raceArrivalState,
            raceStartAtMs = race?.startAtMs,
            arrived = self?.arrived ?: false,
            nowMs = nowMs,
        )
        raceArrivalState = nextState
        if (!shouldAnnounce) return
        val position = arrivalLedgerState.finishers[self?.name]?.order ?: return
        navigationVoice.say("Llegaste, posición $position")
    }

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

    // ── Mapa offline (tier local) ───────────────────────────────────────────

    /** True mientras el `.pmtiles` de Colombia esté usable en disco — dispara el estilo
     * vectorial completo en LiveMapScreen. Deriva de MapDownloadService.state con
     * [LocalMapReadiness] (pura, testeada en core/maps): Idle es autoritativo, pero
     * Downloading/Error CONSERVAN el valor previo — una RE-descarga no borra el `.pmtiles`
     * viejo hasta el rename atómico final, así que leer `state is Idle && exists` en cada
     * emisión degradaba el mapa a ráster durante toda la descarga con un archivo perfectamente
     * usable en disco. El seed lee el disco directo (no el state en memoria): si este VM se
     * crea con una descarga ya en curso (pantalla reabierta), el state por sí solo no dice si
     * ya había un mapa previo instalado. */
    val localMapFileExists: StateFlow<Boolean> = mapDownloadService.state
        .scan(localMapFileSeed()) { prev, state -> LocalMapReadiness.step(prev, state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), localMapFileSeed())

    private fun localMapFileSeed(): Boolean = MapStyleProvider.localMapFile(appContext.filesDir).isFile

    private val _offlineMapCorruptedMessage = MutableStateFlow<String?>(null)
    val offlineMapCorruptedMessage: StateFlow<String?> = _offlineMapCorruptedMessage.asStateFlow()

    /** LiveMapScreen llama esto cuando MapLibre reporta un fallo de carga con el tier 1 (`.pmtiles`
     * local) activo — el archivo se asume corrupto: se borra (la cascada cae a ráster/servidor
     * sola, vía [localMapFileExists]) y se avisa para que el usuario re-descargue en Ajustes. */
    fun onOfflineMapLoadFailed() {
        mapDownloadService.delete()
        _offlineMapCorruptedMessage.value = "Mapa offline dañado — re-descargalo en Ajustes"
    }

    fun clearOfflineMapCorruptedMessage() {
        _offlineMapCorruptedMessage.value = null
    }

    override fun onCleared() {
        locationProvider.stop()
    }

    val speedKmh: StateFlow<Int?> = sessionManager.readings
        .map { (it["0D"] ?: it["GPS_SPEED"])?.value?.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tipo del vehículo activo, para el ícono del puck (LiveMapLayers.puckIcon) — null sin
     * perfil configurado, mismo fallback que usa el gear learner (ver DashboardViewModel). */
    val puckVehicleType: StateFlow<VehicleType?> = sessionManager.activeProfile
        .map { it?.vehicleType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), sessionManager.activeProfile.value?.vehicleType)

    // ── Ruta a destino (OSRM) ────────────────────────────────────────────────

    private val _destination = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val destination: StateFlow<LiveRouteHolder.RoutePoint?> = _destination.asStateFlow()

    // Nombre legible del destino fijado, si se conoce (búsqueda, lugar guardado o propuesta
    // de sala) — null en un long-press del mapa. Alimenta shareCurrentDestination() y la
    // tarjeta descriptiva de tap sobre el marcador de destino (fix D).
    private val _destinationName = MutableStateFlow<String?>(null)
    val destinationName: StateFlow<String?> = _destinationName.asStateFlow()

    private val _plannedRoute = MutableStateFlow<NavigationRoute?>(null)
    val plannedRoute: StateFlow<NavigationRoute?> = _plannedRoute.asStateFlow()

    // Todas las rutas devueltas por OSRM (spec F6) — la elegida es siempre _plannedRoute, que
    // puede ser cualquiera de estas o, tras un reroute mid-viaje, una recalculada que ya no
    // pertenece a este set (por eso el reroute las vacía: ver maybeReroute).
    private val _routeAlternatives = MutableStateFlow<List<NavigationRoute>>(emptyList())
    val routeAlternatives: StateFlow<List<NavigationRoute>> = _routeAlternatives.asStateFlow()

    private val _routing = MutableStateFlow(false)
    val routing: StateFlow<Boolean> = _routing.asStateFlow()

    /** Cambia la ruta activa a una de las alternativas ya calculadas — la navegación y la
     * carrera siguen leyendo [_plannedRoute], así que apenas cambia acá lo ven de inmediato. */
    fun selectAlternative(index: Int) {
        if (navigationController.isNavigating) return
        val alternative = _routeAlternatives.value.getOrNull(index) ?: return
        _plannedRoute.value = alternative
    }

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
        _routeAlternatives.value = emptyList()
        _routing.value = true
        viewModelScope.launch {
            val fetched = withContext(Dispatchers.IO) {
                OsrmRouteFetcher.fetchAlternatives(origin.lat, origin.lon, lat, lon)
            }
            if (generation != routingGeneration) return@launch
            _routeAlternatives.value = fetched
            // OSRM entrega la recomendada (la más rápida) primero — selección inicial natural.
            _plannedRoute.value = fetched.firstOrNull()
            _routing.value = false
        }
    }

    fun clearDestination() {
        // Bumpea la generación como cualquier otro cambio de destino (ver setDestination):
        // sin esto, un fetchAlternatives en vuelo que resuelve después de limpiar resucita
        // ruta y chips con el destino viejo.
        ++routingGeneration
        rerouteJob?.cancel()
        navigationController.stop()
        _destination.value = null
        _destinationName.value = null
        _plannedRoute.value = null
        _routeAlternatives.value = emptyList()
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
        // Único punto que pisa arrivalLedgerState (F1): step() con las entries crudas de este
        // tick, aplica el orden real resultante sobre `ranking` (lo que ve la UI) y recién
        // ahí decide si corresponde anunciar la llegada propia — maybeAnnounceArrival necesita
        // el ledger ya actualizado para resolver la posición real, no un índice.
        viewModelScope.launch {
            combine(rawRanking, roomState) { entries, state -> entries to state.race }
                .collect { (entries, race) ->
                    // Un único nowMs para ambos reducers: con relojes distintos en el mismo
                    // tick, un cruce en la ventana de ms alrededor de startAtMs podía quedar
                    // pre-largada para el ledger y post-largada para RaceArrival, tragándose
                    // el anuncio con `announced` ya consumido.
                    val nowMs = System.currentTimeMillis()
                    arrivalLedgerState = ArrivalLedger.step(
                        state = arrivalLedgerState,
                        entries = entries,
                        raceKey = race?.startAtMs,
                        nowMs = nowMs,
                    )
                    val ranked = RankingCalc.applyArrivalOrder(entries, arrivalLedgerState)
                    _ranking.value = ranked
                    maybeAnnounceArrival(ranked, race, nowMs)
                }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val MINUTE_TICK_MS = 60_000L
        const val RACE_CLOCK_TICK_MS = 250L
        const val PEER_STALE_CHECK_MS = 10_000L
    }
}
