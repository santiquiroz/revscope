package com.revscope.feature.map

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.revscope.core.maps.MapLibreMapView
import com.revscope.core.maps.MapStyleProvider
import com.revscope.core.maps.peekMapLayersAsset
import com.revscope.core.maps.readMapLayersAsset
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.telemetry.TripStatsCalculator
import com.revscope.core.navigation.LatLon
import com.revscope.core.navigation.NavigationRoute
import com.revscope.core.navigation.RouteScoring
import com.revscope.feature.map.location.InitialCentering
import com.revscope.feature.map.navigation.NavCamera
import com.revscope.feature.map.navigation.NavigationBanner
import com.revscope.feature.map.navigation.NavigationProgressBar
import com.revscope.feature.map.social.Leaderboard
import com.revscope.feature.map.social.RaceCountdownOverlay
import kotlinx.coroutines.flow.StateFlow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.MoveGestureDetector
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AttributionColor = Color(0xFF6B7089)

/** RoomClient.SharedDest no es Parcelable/Serializable — rememberSaveable necesita un Saver
 * explícito para sobrevivir un cambio de configuración (rotación) sin perder qué destino ya
 * descartó el usuario del banner. */
private val SharedDestSaver = listSaver<RoomClient.SharedDest?, Any>(
    save = { dest -> dest?.let { listOf(it.rider, it.lat, it.lon, it.name) } ?: emptyList() },
    restore = { saved ->
        if (saved.isEmpty()) null
        else RoomClient.SharedDest(
            rider = saved[0] as String,
            lat = saved[1] as Double,
            lon = saved[2] as Double,
            name = saved[3] as String,
        )
    },
)

@Composable
fun LiveMapScreen(viewModel: LiveMapViewModel = hiltViewModel()) {
    val route by viewModel.route.collectAsState()
    val routeRevision by viewModel.routeRevision.collectAsState()
    val cameras by viewModel.cameras.collectAsState()
    val potholes by viewModel.potholes.collectAsState()
    val approaching by viewModel.approachingCamera.collectAsState()
    val alertRadiusM by viewModel.cameraAlertRadiusM.collectAsState()
    val roomCode by viewModel.roomCode.collectAsState()
    val peers by viewModel.peers.collectAsState()
    val roomBusy by viewModel.roomBusy.collectAsState()
    val roomState by viewModel.roomState.collectAsState()
    val sharedDest by viewModel.sharedDest.collectAsState()
    val ranking by viewModel.ranking.collectAsState()
    val raceCountdown by viewModel.raceCountdown.collectAsState()
    val effectiveSelfName by viewModel.effectiveSelfName.collectAsState()
    val ghost by viewModel.ghost.collectAsState()
    val destination by viewModel.destination.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val routeAlternatives by viewModel.routeAlternatives.collectAsState()
    val routing by viewModel.routing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    val navigation by viewModel.navigation.collectAsState()
    val navigationError by viewModel.navigationError.collectAsState()
    val speedKmh by viewModel.speedKmh.collectAsState()
    val liveFix by viewModel.liveFix.collectAsState()
    val initialCenter by viewModel.initialCenter.collectAsState()
    val darkTiles by viewModel.darkTiles.collectAsState()
    val nightMode by viewModel.nightMode.collectAsState()
    val localMapFileExists by viewModel.localMapFileExists.collectAsState()
    val offlineMapCorruptedMessage by viewModel.offlineMapCorruptedMessage.collectAsState()
    // Durante un viaje la ruta viva ya alimenta puck y efectos a ~1 Hz; pasar también el fix
    // del provider duplicaría los re-writes del dataset completo sin cambio visual (T4 lo enmascara).
    val standaloneFix = if (route.isEmpty()) liveFix else null
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Android 12+ ignora un request de FINE sin COARSE en el mismo diálogo.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        hasLocationPermission = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true
    }
    val centering = remember { InitialCentering() }

    // GPS solo con el mapa visible Y la app en foreground: Home o pantalla apagada lo cortan.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(hasLocationPermission, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (hasLocationPermission) viewModel.onMapVisible()
                Lifecycle.Event.ON_STOP -> viewModel.onMapHidden()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (hasLocationPermission && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onMapVisible()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onMapHidden()
        }
    }

    var showRoomDialog by remember { mutableStateOf(false) }
    var followEnabled by remember { mutableStateOf(true) }
    var headingUp by remember { mutableStateOf(false) }
    var leaderboardExpanded by remember { mutableStateOf(false) }
    var dismissedDest by rememberSaveable(stateSaver = SharedDestSaver) { mutableStateOf<RoomClient.SharedDest?>(null) }
    val density = context.resources.displayMetrics.density
    // Destino ajeno recién propuesto: ni el mío propio (server puede hacer eco), ni de un
    // server legacy (no debería llegar, pero el gate es explícito), ni el que ya descarté.
    // Navegando el "Ir" quedaría como no-op silencioso (setDestination no cambia nada con
    // navigationController.isNavigating) — igual que RouteInfoChip, se oculta con nav activa.
    val incomingDest = sharedDest?.takeIf {
        navigation == null && !roomState.legacyServer && it.rider != effectiveSelfName && it != dismissedDest
    }
    val canShareDestination = roomCode != null && !roomState.legacyServer && destination != null

    // Iniciar navegación toma el control de la cámara aunque el usuario venía paneando.
    LaunchedEffect(navigation != null) {
        if (navigation != null) followEnabled = true
    }

    // Tier server sigue sin wirear (el server hoy no hospeda tiles): tilesUrl solo considera el
    // .pmtiles local, gobernado por localMapFileExists (MapDownloadService, vía el VM — con la
    // semántica correcta de LocalMapReadiness: una RE-descarga en curso no lo apaga). Cuando el
    // archivo aparece o desaparece (descarga completa / borrado por corrupción) este remember
    // recalcula y MapLibreMapView vuelve a cargar el estilo — mismo mecanismo que ya usa el
    // cambio de tema claro/oscuro.
    val localMapFile = remember(context) { MapStyleProvider.localMapFile(context.filesDir) }
    // El asset de capas pesa ~240 KB: leerlo síncrono en composición bloquearía el frame. Corre
    // en IO y cachea en memoria por tema (readMapLayersAsset). El initialValue se siembra desde
    // ese mismo cache (peekMapLayersAsset, sin IO): con el tema ya leído antes, el toggle
    // día/noche entrega el estilo final en el mismo frame, sin el flash de placeholder + reload
    // que daba resetear siempre a null. Solo un cache-miss real (primera vez que se ve ese
    // tema en el proceso) pasa por null y resuelve async, como antes.
    val layersJson by produceState<String?>(initialValue = peekMapLayersAsset(darkTiles), darkTiles) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { readMapLayersAsset(context, dark = darkTiles) }
        }
    }
    val styleJson = remember(localMapFileExists, darkTiles, layersJson) {
        val tilesUrl = MapStyleProvider.tilesUrl(if (localMapFileExists) localMapFile else null, null)
        MapStyleProvider.styleJson(tilesUrl = tilesUrl, dark = darkTiles, layersJson = layersJson)
    }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleEpoch by remember { mutableStateOf(0) }

    // Solo re-centra cuando llega una revisión nueva, para no pelear con el usuario mientras
    // panea ni recentrar en recomposiciones ajenas (route.size se estanca en viajes de 5h+,
    // por eso la señal es la revisión y no el tamaño).
    var lastCenteredRevision by remember { mutableStateOf(-1L) }

    val data = LiveMapData(
        route = route,
        cameras = cameras,
        potholes = potholes,
        peers = peers.values.toList(),
        approachingId = approaching?.osmId,
        alertRadiusM = alertRadiusM,
        destination = destination,
        plannedRoute = plannedRoute,
        liveFix = standaloneFix,
        routeAlternatives = routeAlternatives,
    )

    Box(Modifier.fillMaxSize()) {
        MapLibreMapView(
            modifier = Modifier.fillMaxSize(),
            styleJson = styleJson,
            onDidFailLoadingMap = { _ ->
                // Solo tratamos esto como corrupción del .pmtiles con el tier 1 realmente
                // activo — un fallo de red del ráster/sprite remoto no debe borrar nada.
                if (localMapFileExists) viewModel.onOfflineMapLoadFailed()
            },
        ) { map, style ->
            installLiveMapLayers(style, density, data)
            mapRef = map
            // Sube en cada instalación de estilo para que los efectos de datos y cámara
            // vuelvan a correr: al recargar el estilo las fuentes viejas quedan detached.
            styleEpoch++
        }

        // Un solo registro por instancia de mapa: registrarlos en el callback de estilo
        // los duplicaría en cada cambio de modo nocturno.
        LaunchedEffect(mapRef) {
            val map = mapRef ?: return@LaunchedEffect
            map.addOnMapLongClickListener { latLng ->
                viewModel.setDestination(latLng.latitude, latLng.longitude)
                true
            }
            map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                // Cualquier gesto del usuario apaga el follow; el FAB lo devuelve.
                override fun onMoveBegin(detector: MoveGestureDetector) {
                    followEnabled = false
                    centering.onUserPan()
                }
                override fun onMove(detector: MoveGestureDetector) = Unit
                override fun onMoveEnd(detector: MoveGestureDetector) = Unit
            })
            map.addOnScaleListener(object : MapLibreMap.OnScaleListener {
                // Pinch = el usuario toma el control, igual que el pan; el FAB lo devuelve.
                override fun onScaleBegin(detector: StandardScaleGestureDetector) {
                    followEnabled = false
                    centering.onUserPan()
                }
                override fun onScale(detector: StandardScaleGestureDetector) = Unit
                override fun onScaleEnd(detector: StandardScaleGestureDetector) = Unit
            })
        }

        // La ruta viva llega a 18.000 puntos y cada escritura re-indexa el dataset completo,
        // así que se escribe solo cuando la revisión cambia, no en cada recomposición.
        LaunchedEffect(mapRef, styleEpoch, routeRevision, cameras, potholes, peers, approaching, alertRadiusM, destination, plannedRoute, standaloneFix, routeAlternatives) {
            mapRef?.style?.let { if (it.isFullyLoaded()) updateLiveMapData(it, data) }
        }

        // Nav "idle" = sin navegación o ya llegado — en ambos casos NavCamera dejó de dictar el
        // bearing, así que la des-rotación (abajo) y el de-tilt (más abajo) pueden actuar.
        val navIdle = navigation == null || navigation?.arrived == true

        LaunchedEffect(mapRef, styleEpoch, routeRevision, followEnabled, headingUp, standaloneFix, initialCenter, navigation) {
            val map = mapRef ?: return@LaunchedEffect
            // Navegando: cámara dedicada — course-up, inclinada, zoom por velocidad y maniobra.
            val nav = navigation
            if (nav != null && !nav.arrived && followEnabled) {
                val target = nav.snapped ?: route.lastOrNull()?.let { LatLon(it.lat, it.lon) }
                if (target != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(target.lat, target.lon))
                                .bearing(currentBearingDegrees(route))
                                .zoom(NavCamera.zoom(speedKmh, nav.distanceToManeuverM))
                                .tilt(NavCamera.PITCH)
                                .build(),
                        ),
                    )
                }
                return@LaunchedEffect
            }
            val last = route.lastOrNull()
            if (last != null) {
                if (followEnabled && routeRevision != lastCenteredRevision) {
                    lastCenteredRevision = routeRevision
                    // Sin .zoom(): el follow re-centra pero no re-zoomea, así que el pinch del
                    // usuario se respeta indefinidamente.
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(last.lat, last.lon))
                                .bearing(if (headingUp) currentBearingDegrees(route) else 0.0)
                                .build(),
                        ),
                    )
                } else if (!headingUp && map.cameraPosition.bearing != 0.0 && navIdle) {
                    // Apagar rumbo-arriba des-rota de inmediato, sin esperar revisión — pero no
                    // con nav activa (y no arrived) y follow off: ahí el bearing lo sigue
                    // dictando NavCamera, y des-rotar acá movería la cámara sola en la
                    // siguiente emisión (M1). Al llegar (arrived) NavCamera ya no manda, así
                    // que un rumbo residual sí debe poder des-rotarse acá.
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().bearing(0.0).build(),
                        ),
                    )
                }
            } else {
                centering.onLiveFix(standaloneFix)?.let {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), it.zoom))
                    return@LaunchedEffect
                }
                centering.onLastKnown(initialCenter)?.let {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), it.zoom))
                    return@LaunchedEffect
                }
                // Sin viaje pero con follow armado: el puck standalone también se sigue.
                if (followEnabled && standaloneFix != null) {
                    val fix = standaloneFix
                    map.animateCamera(
                        CameraUpdateFactory.newLatLng(LatLng(fix.lat, fix.lon)),
                    )
                }
            }
        }

        // Fin de navegación O llegada: quitar la inclinación; zoom y centro quedan como estaban.
        // "arrived" deja el NavigationBanner en pantalla (nav sigue no-null) pero la cámara ya
        // no debe quedar inclinada a 50° mientras se muestra ese estado final (M3). navIdle
        // declarado arriba, compartido con la guarda de de-rotación.
        LaunchedEffect(navIdle) {
            if (navIdle) {
                mapRef?.let { map ->
                    if (map.cameraPosition.tilt != 0.0) {
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder().tilt(0.0).build(),
                            ),
                        )
                    }
                }
            }
        }

        Text(
            "© OpenStreetMap contributors",
            color = AttributionColor,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        if (!hasLocationPermission) {
            Surface(
                color = Color(0xF2121218),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ubicación desactivada",
                        color = Color(0xFFF0F0F8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                    )
                    TextButton(
                        onClick = {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                        },
                    ) {
                        Text("Permitir", color = Color(0xFFE8FF00), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        SearchOverlay(
            query = searchQuery,
            results = searchResults,
            searching = searching,
            savedPlaces = savedPlaces,
            onQueryChange = viewModel::updateSearchQuery,
            onClear = viewModel::clearSearch,
            onSelect = viewModel::selectSearchResult,
            onSelectSaved = viewModel::selectSavedPlace,
            onSaveFavorite = viewModel::saveFavorite,
            onRemoveSaved = viewModel::removePlace,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp, start = 12.dp, end = 12.dp),
        )

        SpeedOverlay(viewModel.speedKmh, Modifier.align(Alignment.BottomStart).padding(16.dp))

        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            approaching?.let { target -> ApproachingCameraBanner(target) }
            incomingDest?.let { incoming ->
                if (approaching != null) Spacer(Modifier.height(8.dp))
                SharedDestBanner(
                    dest = incoming,
                    onAccept = {
                        viewModel.setDestination(incoming.lat, incoming.lon, incoming.name)
                        dismissedDest = incoming
                    },
                    onDismiss = { dismissedDest = incoming },
                )
            }
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(
                onClick = viewModel::cycleNightMode,
                containerColor = if (darkTiles) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    if (nightMode == "auto") Icons.Default.BrightnessAuto else Icons.Default.DarkMode,
                    contentDescription = "Mapa nocturno: " + when (nightMode) {
                        "auto" -> "automático"
                        "on" -> "encendido"
                        else -> "apagado"
                    },
                    tint = if (darkTiles) Color(0xFF0A0A0F) else Color(0xFFF0F0F8),
                )
            }
            Spacer(Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = { headingUp = !headingUp },
                containerColor = if (headingUp) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    Icons.Default.Explore,
                    contentDescription = "Rumbo arriba",
                    tint = if (headingUp) Color(0xFF0A0A0F) else Color(0xFFF0F0F8),
                )
            }
            Spacer(Modifier.height(8.dp))
            SmallFloatingActionButton(
                onClick = { followEnabled = true },
                containerColor = if (followEnabled) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    Icons.Default.MyLocation,
                    contentDescription = "Seguir mi posición",
                    tint = if (followEnabled) Color(0xFF0A0A0F) else Color(0xFFF0F0F8),
                )
            }
            Spacer(Modifier.height(12.dp))
            FloatingActionButton(
                onClick = { showRoomDialog = true },
                containerColor = if (roomCode != null) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = "Rodada en grupo",
                    tint = if (roomCode != null) Color(0xFF0A0A0F) else Color(0xFFF0F0F8),
                )
            }
            Spacer(Modifier.height(12.dp))
            FloatingActionButton(
                onClick = {
                    openExternalNavigation(
                        context,
                        destination ?: route.lastOrNull() ?: viewModel.initialCenter.value,
                        turnByTurn = destination != null,
                    )
                },
            ) {
                Icon(Icons.Default.Navigation, contentDescription = "Abrir en Maps")
            }
        }

        navigation?.let { live ->
            NavigationBanner(
                state = live,
                onStop = viewModel::stopNavigation,
                modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 12.dp, vertical = 8.dp),
            )
            NavigationProgressBar(
                state = live,
                modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 12.dp, vertical = 24.dp),
            )
        }

        if (destination != null && navigation == null) {
            RouteInfoChip(
                routing = routing,
                plannedRoute = plannedRoute,
                routeAlternatives = routeAlternatives,
                canShare = canShareDestination,
                onStart = viewModel::startNavigation,
                onClear = viewModel::clearDestination,
                onShare = viewModel::shareCurrentDestination,
                onSelectAlternative = viewModel::selectAlternative,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }

        if (offlineMapCorruptedMessage != null || navigationError != null) {
            Column(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 90.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                offlineMapCorruptedMessage?.let { message ->
                    ErrorBanner(message, onDismiss = viewModel::clearOfflineMapCorruptedMessage)
                    if (navigationError != null) Spacer(Modifier.height(8.dp))
                }
                navigationError?.let { message ->
                    ErrorBanner(message, onDismiss = viewModel::clearNavigationError)
                }
            }
        }

        if (roomCode != null) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Surface(
                    color = Color(0xE6121218),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    Text(
                        "\uD83C\uDFCD\uFE0F Sala $roomCode \u00b7 ${peers.size} en l\u00ednea",
                        color = Color(0xFFE8FF00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                if (sharedDest != null && !roomState.legacyServer) {
                    Spacer(Modifier.height(8.dp))
                    Leaderboard(
                        entries = ranking,
                        expanded = leaderboardExpanded,
                        onToggleExpanded = { leaderboardExpanded = !leaderboardExpanded },
                        race = roomState.race,
                        selfRiderName = effectiveSelfName,
                        onStartRace = viewModel::startRace,
                        onStopRace = viewModel::stopRace,
                        modifier = Modifier.width(220.dp),
                    )
                }
            }
        }

        raceCountdown?.let { seconds ->
            RaceCountdownOverlay(secondsToShow = seconds, modifier = Modifier.align(Alignment.Center))
        }
    }

    if (showRoomDialog) {
        GroupRideDialog(
            activeCode = roomCode,
            busy = roomBusy,
            legacyServer = roomState.legacyServer,
            ghostEnabled = ghost,
            onToggleGhost = viewModel::setGhost,
            onCreate = { viewModel.createRoom { showRoomDialog = false } },
            onJoin = { code -> viewModel.joinRoom(code); showRoomDialog = false },
            onLeave = { viewModel.leaveRoom(); showRoomDialog = false },
            onDismiss = { showRoomDialog = false },
        )
    }
}

@Composable
private fun GroupRideDialog(
    activeCode: String?,
    busy: Boolean,
    legacyServer: Boolean = false,
    ghostEnabled: Boolean = false,
    onToggleGhost: (Boolean) -> Unit = {},
    onCreate: () -> Unit,
    onJoin: (String) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var codeInput by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12121A),
        title = { Text("Rodada en grupo", color = Color(0xFFF0F0F8), fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (activeCode != null) {
                    Text("Est\u00e1s en la sala $activeCode. Comparte el c\u00f3digo con tu parche.", color = Color(0xFF6B7089), fontSize = 13.sp)
                    if (legacyServer) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "El servidor necesita actualizarse para las funciones sociales (destino compartido, carreras).",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    GhostModeToggle(enabled = ghostEnabled, onToggle = onToggleGhost)
                } else {
                    Text("Crea una sala y comparte el c\u00f3digo, o \u00fanete a la de un parcero. Ver\u00e1s sus posiciones en el mapa en vivo.", color = Color(0xFF6B7089), fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().take(6) },
                        label = { Text("C\u00f3digo de sala", fontSize = 12.sp) },
                        singleLine = true,
                    )
                    Text("Requiere un servidor configurado en Ajustes.", color = Color(0xFF6B7089), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        confirmButton = {
            if (activeCode != null) {
                TextButton(onClick = onLeave) { Text("Salir de la sala", color = Color(0xFFFF5252)) }
            } else {
                TextButton(onClick = onCreate, enabled = !busy) { Text(if (busy) "Creando\u2026" else "Crear sala", color = Color(0xFFE8FF00)) }
            }
        },
        dismissButton = {
            if (activeCode == null && codeInput.length == 6) {
                TextButton(onClick = { onJoin(codeInput) }) { Text("Unirse", color = Color(0xFFE8FF00)) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cerrar", color = Color(0xFF6B7089)) }
            }
        },
    )
}

/** F3: deja la sala sin retransmitir mi posición (RoomClient.setGhost) — el resto sigue viendo
 * las posiciones de los demás normalmente, solo la mía deja de salir. Sin persistencia. */
@Composable
private fun GhostModeToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Modo fantasma", color = Color(0xFFF0F0F8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(
                "Ves a la sala, pero tu posición no se comparte.",
                color = Color(0xFF6B7089),
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color(0xFF0A0A0F),
                checkedTrackColor = Color(0xFFE8FF00),
            ),
        )
    }
}

/** Chip con distancia y ETA de la ruta planeada — o el estado del cálculo. Con más de una
 * alternativa de OSRM, agrega debajo los chips de selección (Rápida/Alt/Curvas — spec F6). */
@Composable
private fun RouteInfoChip(
    routing: Boolean,
    plannedRoute: NavigationRoute?,
    routeAlternatives: List<NavigationRoute>,
    canShare: Boolean,
    onStart: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onSelectAlternative: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xE6121218),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        routing -> "Calculando ruta…"
                        plannedRoute != null -> formatRouteSummary(plannedRoute)
                        else -> "Sin ruta — ¿hay internet?"
                    },
                    color = Color(0xFFE8FF00),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (plannedRoute != null) {
                    TextButton(onClick = onStart) {
                        Text("Navegar", color = Color(0xFFE8FF00), fontWeight = FontWeight.Black)
                    }
                }
                if (canShare) {
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir con la sala", tint = Color(0xFFE8FF00))
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar destino", tint = Color(0xFF6B7089))
                }
            }
            if (routeAlternatives.size > 1) {
                RouteAlternativeChips(
                    routes = routeAlternatives,
                    selected = plannedRoute,
                    onSelect = onSelectAlternative,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
    }
}

/** Chips de selección entre rutas alternativas — etiquetadas por [RouteScoring.labelAlternatives]
 * (Rápida/Alt/Curvas). La activa se resalta comparando por igualdad estructural con [selected],
 * no por índice: tras un reroute la elegida puede ya no pertenecer a este set. */
@Composable
private fun RouteAlternativeChips(
    routes: List<NavigationRoute>,
    selected: NavigationRoute?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = remember(routes) { RouteScoring.labelAlternatives(routes) }
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        routes.forEachIndexed { index, route ->
            RouteAlternativeChip(
                label = labels[index],
                isSelected = route == selected,
                onClick = { onSelect(index) },
            )
        }
    }
}

@Composable
private fun RouteAlternativeChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1C1C26),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            if (label == "Curvas") "Curvas 🏍️" else label,
            color = if (isSelected) Color(0xFF0A0A0F) else Color(0xFFB0B0C0),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Propuesta de destino de otro rider de la sala — aceptar fija ese destino localmente. */
@Composable
private fun SharedDestBanner(
    dest: RoomClient.SharedDest,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color(0xE6121218),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Text(
                "🎯 ${dest.rider} propone destino: ${dest.name}",
                color = Color(0xFFF0F0F8),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onAccept) {
                Text("Ir", color = Color(0xFFE8FF00), fontWeight = FontWeight.Black)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Descartar propuesta", tint = Color(0xFF6B7089))
            }
        }
    }
}

private fun formatRouteSummary(route: NavigationRoute): String {
    val km = route.distanceM / 1000.0
    val minutes = (route.durationS / 60.0).toInt()
    val distance = if (km >= 10) "%.0f km".format(km) else "%.1f km".format(km)
    val time = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return "$distance · $time"
}

/** Aviso inline dismisseable — mismo look para el error de navegación y el de mapa offline
 * corrupto, así ambos pueden convivir apilados sin duplicar el Surface/Row/Text/IconButton. */
@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = Color(0xF2121218),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                color = Color(0xFFE8FF00),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar aviso", tint = AttributionColor)
            }
        }
    }
}

/** Rumbo actual a partir de los dos últimos puntos de la ruta viva (0 = norte). */
private fun currentBearingDegrees(route: List<LiveRouteHolder.RoutePoint>): Double {
    if (route.size < 2) return 0.0
    val prev = route[route.size - 2]
    val last = route.last()
    return TripStatsCalculator.initialBearingDegrees(prev.lat, prev.lon, last.lat, last.lon)
}

/** Banner del radar al que el vehículo se dirige — solo ese, nunca los de otras calles. */
@Composable
private fun ApproachingCameraBanner(
    target: SpeedCameraAlerter.ApproachingCamera,
    modifier: Modifier = Modifier,
) {
    Surface(color = Color(0xE6B71C1C), shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append("Radar en ${target.distanceM} m")
                    target.maxSpeedKmh?.let { append("  ·  límite $it") }
                },
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SpeedOverlay(speedFlow: StateFlow<Int?>, modifier: Modifier = Modifier) {
    val speed by speedFlow.collectAsState()
    speed?.let { CurrentSpeedBadge(it, modifier) }
}

@Composable
private fun CurrentSpeedBadge(speedKmh: Int, modifier: Modifier = Modifier) {
    Surface(color = Color(0xCC12121A), modifier = modifier) {
        Text(
            "$speedKmh km/h",
            color = Color(0xFFE8FF00),
            fontSize = 28.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

private fun openExternalNavigation(
    context: Context,
    target: LiveRouteHolder.RoutePoint?,
    turnByTurn: Boolean = false,
) {
    // Con destino fijado se lanza navegación giro a giro real; sin destino, solo el mapa.
    val uri = when {
        target != null && turnByTurn -> "google.navigation:q=${target.lat},${target.lon}"
        target != null -> "geo:${target.lat},${target.lon}"
        else -> "geo:0,0"
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}
