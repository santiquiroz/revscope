package com.revscope.feature.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Group
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import android.view.MotionEvent
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.telemetry.TripStatsCalculator
import com.revscope.feature.map.routing.OsrmRouteFetcher
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

private const val INITIAL_ZOOM = 16.0
private const val IDLE_ZOOM = 13.0
private val AttributionColor = Color(0xFF6B7089)

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
    val destination by viewModel.destination.collectAsState()
    val plannedRoute by viewModel.plannedRoute.collectAsState()
    val routing by viewModel.routing.collectAsState()
    var showRoomDialog by remember { mutableStateOf(false) }
    var followEnabled by remember { mutableStateOf(true) }
    var headingUp by remember { mutableStateOf(false) }
    var darkTiles by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Long-press = fijar destino; el overlay vive fuera del rebuild para no perder el listener.
    val mapEventsOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { viewModel.setDestination(it.latitude, it.longitude) }
                return p != null
            }
        })
    }

    // Evita recentrar el mapa (y pelear con el usuario si lo está paneando) en cada
    // recomposición disparada por lecturas OBD; solo recentra cuando cambia routeRevision
    // (route.size se estanca en viajes de 5h+, por eso no sirve como señal de cambio),
    // y usa la ubicación inicial una única vez sin viaje activo.
    var lastCenteredRevision by remember { mutableStateOf(-1L) }
    var hasCenteredInitial by remember { mutableStateOf(false) }

    // Evita limpiar y reconstruir los overlays (hasta 18.000 puntos de ruta) en cada
    // recomposición; cuando la ruta solo CRECE (por debajo del tope) se agregan los
    // puntos nuevos al Polyline existente (addPoint) — la reconstrucción completa queda
    // para cuando cambian las cámaras, la ruta se encoge/reinicia (nuevo viaje), o la
    // ruta cambió pero su tamaño se estancó (viaje en el tope de MAX_POINTS).
    var lastOverlayRevision by remember { mutableStateOf(-1L) }
    var lastOverlaySize by remember { mutableStateOf(-1) }
    var lastOverlayCameraCount by remember { mutableStateOf(-1) }
    var lastOverlayAlertRadius by remember { mutableStateOf(-1) }
    var lastApproachingId by remember { mutableStateOf<Long?>(null) }
    var lastPeerCount by remember { mutableStateOf(-1) }
    var lastDestination by remember { mutableStateOf<LiveRouteHolder.RoutePoint?>(null) }
    var lastPlannedRoute by remember { mutableStateOf<OsrmRouteFetcher.Route?>(null) }
    var routeOverlays by remember { mutableStateOf(RouteOverlays(polyline = null, marker = null)) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    load(ctx, ctx.getSharedPreferences("osmdroid", 0))
                }
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(INITIAL_ZOOM)
                    // Pan/zoom manual apaga el follow — el FAB de recentrar lo devuelve.
                    setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) followEnabled = false
                        false
                    }
                }
            },
            update = { map ->
                val camerasChanged = cameras.size != lastOverlayCameraCount ||
                    alertRadiusM != lastOverlayAlertRadius
                // Cambió el radar objetivo (entró/salió del cono de rumbo) → repintar para
                // resaltar solo ese y atenuar el resto.
                val approachingChanged = approaching?.osmId != lastApproachingId
                val peersChanged = peers.size != lastPeerCount
                val destinationChanged = destination != lastDestination || plannedRoute != lastPlannedRoute
                val routeReset = routeRevision < lastOverlayRevision ||
                    (route.isEmpty() && routeOverlays.polyline != null)
                val revisionChanged = routeRevision != lastOverlayRevision
                val routeGrew = revisionChanged && !routeReset && route.size > lastOverlaySize
                val missingPolyline = route.isNotEmpty() && routeOverlays.polyline == null
                val needsFullRebuild = camerasChanged || approachingChanged || peersChanged || destinationChanged ||
                    routeReset || missingPolyline || (revisionChanged && !routeGrew)

                if (needsFullRebuild) {
                    routeOverlays = rebuildMapOverlays(
                        map, route, cameras, potholes, peers.values.toList(), approaching?.osmId,
                        alertRadiusM, mapEventsOverlay, destination, plannedRoute,
                    )
                    lastOverlayCameraCount = cameras.size
                    lastOverlayAlertRadius = alertRadiusM
                    lastApproachingId = approaching?.osmId
                    lastPeerCount = peers.size
                    lastDestination = destination
                    lastPlannedRoute = plannedRoute
                } else if (routeGrew) {
                    appendRoutePoints(routeOverlays.polyline, route, lastOverlaySize)
                    updateCurrentPositionMarker(routeOverlays.marker, route.last())
                }
                lastOverlayRevision = routeRevision
                lastOverlaySize = route.size

                map.overlayManager.tilesOverlay.setColorFilter(
                    if (darkTiles) TilesOverlay.INVERT_COLORS else null,
                )

                if (route.isNotEmpty()) {
                    val last = route.last()
                    if (followEnabled && routeRevision != lastCenteredRevision) {
                        lastCenteredRevision = routeRevision
                        map.controller.setCenter(GeoPoint(last.lat, last.lon))
                        map.mapOrientation = if (headingUp) {
                            -currentBearingDegrees(route).toFloat()
                        } else {
                            0f
                        }
                    }
                    if (!headingUp && map.mapOrientation != 0f) map.mapOrientation = 0f
                } else if (!hasCenteredInitial) {
                    viewModel.initialCenter.value?.let {
                        map.controller.setCenter(GeoPoint(it.lat, it.lon))
                        map.controller.setZoom(IDLE_ZOOM)
                        hasCenteredInitial = true
                    }
                }
                map.invalidate()
            },
            onRelease = { it.onDetach() },
        )

        Text(
            "© OpenStreetMap contributors",
            color = AttributionColor,
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        )

        SpeedOverlay(viewModel.speedKmh, Modifier.align(Alignment.BottomStart).padding(16.dp))

        approaching?.let { target ->
            ApproachingCameraBanner(target, Modifier.align(Alignment.TopCenter).padding(top = 28.dp))
        }

        Column(Modifier.align(Alignment.BottomEnd).padding(16.dp), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(
                onClick = { darkTiles = !darkTiles },
                containerColor = if (darkTiles) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    Icons.Default.DarkMode,
                    contentDescription = "Mapa nocturno",
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

        if (destination != null) {
            RouteInfoChip(
                routing = routing,
                plannedRoute = plannedRoute,
                onClear = viewModel::clearDestination,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        }

        if (roomCode != null) {
            Surface(
                color = Color(0xE6121218),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            ) {
                Text(
                    "\uD83C\uDFCD\uFE0F Sala $roomCode \u00b7 ${peers.size} en l\u00ednea",
                    color = Color(0xFFE8FF00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }

    if (showRoomDialog) {
        GroupRideDialog(
            activeCode = roomCode,
            busy = roomBusy,
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

/** Chip con distancia y ETA de la ruta planeada — o el estado del cálculo. */
@Composable
private fun RouteInfoChip(
    routing: Boolean,
    plannedRoute: OsrmRouteFetcher.Route?,
    onClear: () -> Unit,
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
                when {
                    routing -> "Calculando ruta…"
                    plannedRoute != null -> formatRouteSummary(plannedRoute)
                    else -> "Sin ruta — ¿hay internet?"
                },
                color = Color(0xFFE8FF00),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            IconButton(onClick = onClear) {
                Icon(Icons.Default.Close, contentDescription = "Quitar destino", tint = Color(0xFF6B7089))
            }
        }
    }
}

private fun formatRouteSummary(route: OsrmRouteFetcher.Route): String {
    val km = route.distanceM / 1000.0
    val minutes = (route.durationS / 60.0).toInt()
    val distance = if (km >= 10) "%.0f km".format(km) else "%.1f km".format(km)
    val time = if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
    return "$distance · $time"
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

/** Live route Polyline + "Tú" position Marker, so the update block can grow/move them incrementally. */
private data class RouteOverlays(val polyline: Polyline?, val marker: Marker?)

private fun rebuildMapOverlays(
    map: MapView,
    route: List<LiveRouteHolder.RoutePoint>,
    cameras: List<SpeedCameraEntity>,
    potholes: List<PotholeEntity>,
    peers: List<RoomClient.Peer>,
    approachingId: Long?,
    alertRadiusM: Int,
    eventsOverlay: MapEventsOverlay,
    destination: LiveRouteHolder.RoutePoint?,
    plannedRoute: OsrmRouteFetcher.Route?,
): RouteOverlays {
    map.overlays.clear()
    // Primero en la lista = debajo de todo y recibe los long-press que nadie más consuma.
    map.overlays.add(eventsOverlay)
    plannedRoute?.let { planned ->
        map.overlays.add(
            Polyline(map).apply {
                setPoints(planned.points.map { GeoPoint(it.lat, it.lon) })
                outlinePaint.color = 0xFF00E5FF.toInt()
                outlinePaint.strokeWidth = 10f
            },
        )
    }
    destination?.let { dest ->
        map.overlays.add(
            Marker(map).apply {
                position = GeoPoint(dest.lat, dest.lon)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Destino"
            },
        )
    }
    peers.forEach { map.overlays.add(peerMarker(map, it)) }
    potholes.forEach { map.overlays.add(potholeMarker(map, it)) }
    // Con un radar objetivo activo, los demás se atenúan: el mapa informa el radar al que
    // VAS, no todos los que existen alrededor.
    cameras.forEach { cam ->
        val isTarget = cam.osmId == approachingId
        val dimmed = approachingId != null && !isTarget
        map.overlays.add(speedCameraAlertCircle(map, cam, isTarget, dimmed, alertRadiusM))
        map.overlays.add(speedCameraMarker(map, cam, isTarget))
    }
    if (route.isEmpty()) return RouteOverlays(polyline = null, marker = null)
    val last = route.last()
    val polyline = Polyline(map).apply {
        setPoints(route.map { GeoPoint(it.lat, it.lon) })
        outlinePaint.color = 0xFFE8FF00.toInt()
        outlinePaint.strokeWidth = 8f
    }
    val marker = Marker(map).apply {
        position = GeoPoint(last.lat, last.lon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = "Tú"
    }
    map.overlays.add(polyline)
    map.overlays.add(marker)
    return RouteOverlays(polyline, marker)
}

/** Appends only the points added since [fromIndex] — avoids rebuilding a route of up to 18.000 points on every reading. */
private fun appendRoutePoints(
    polyline: Polyline?,
    route: List<LiveRouteHolder.RoutePoint>,
    fromIndex: Int,
) {
    if (polyline == null) return
    for (i in fromIndex.coerceAtLeast(0) until route.size) {
        polyline.addPoint(GeoPoint(route[i].lat, route[i].lon))
    }
}

private fun updateCurrentPositionMarker(marker: Marker?, position: LiveRouteHolder.RoutePoint) {
    marker?.position = GeoPoint(position.lat, position.lon)
}

private fun peerMarker(
    map: MapView,
    peer: RoomClient.Peer,
) = Marker(map).apply {
    position = GeoPoint(peer.lat, peer.lon)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    title = "\uD83C\uDFCD\uFE0F ${peer.rider}" + (peer.speedKmh?.let { " \u00b7 ${it.toInt()} km/h" } ?: "")
}

private fun potholeMarker(
    map: MapView,
    pothole: PotholeEntity,
) = Marker(map).apply {
    position = GeoPoint(pothole.latitude, pothole.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    title = "Hueco · %.1fG · %dx".format(pothole.severityG, pothole.hits)
    alpha = 0.85f
}

private fun speedCameraAlertCircle(
    map: MapView,
    camera: SpeedCameraEntity,
    isTarget: Boolean,
    dimmed: Boolean,
    alertRadiusM: Int,
) = Polygon(map).apply {
    points = Polygon.pointsAsCircle(
        GeoPoint(camera.latitude, camera.longitude),
        alertRadiusM.toDouble(),
    )
    when {
        isTarget -> {
            fillPaint.color = 0x44FF1744
            outlinePaint.color = 0xFFFF1744.toInt()
            outlinePaint.strokeWidth = 4f
        }
        dimmed -> {
            fillPaint.color = 0x0DFF5252
            outlinePaint.color = 0x26FF5252
            outlinePaint.strokeWidth = 1f
        }
        else -> {
            fillPaint.color = 0x22FF5252
            outlinePaint.color = 0x66FF5252.toInt()
            outlinePaint.strokeWidth = 2f
        }
    }
}

private fun speedCameraMarker(
    map: MapView,
    camera: SpeedCameraEntity,
    isTarget: Boolean,
) = Marker(map).apply {
    position = GeoPoint(camera.latitude, camera.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    title = (if (isTarget) "⚠ Radar en tu ruta" else "Radar") +
        (camera.maxSpeedKmh?.let { " · $it km/h" } ?: "")
    alpha = if (isTarget) 1f else 0.75f
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
