package com.revscope.feature.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import com.revscope.core.obd.service.LiveRouteHolder
import kotlinx.coroutines.flow.StateFlow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

private const val CAMERA_ALERT_RADIUS_METERS = 400.0
private const val INITIAL_ZOOM = 16.0
private const val IDLE_ZOOM = 13.0
private val AttributionColor = Color(0xFF6B7089)

@Composable
fun LiveMapScreen(viewModel: LiveMapViewModel = hiltViewModel()) {
    val route by viewModel.route.collectAsState()
    val routeRevision by viewModel.routeRevision.collectAsState()
    val cameras by viewModel.cameras.collectAsState()
    val approaching by viewModel.approachingCamera.collectAsState()
    val context = LocalContext.current

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
    var lastApproachingId by remember { mutableStateOf<Long?>(null) }
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
                }
            },
            update = { map ->
                val camerasChanged = cameras.size != lastOverlayCameraCount
                // Cambió el radar objetivo (entró/salió del cono de rumbo) → repintar para
                // resaltar solo ese y atenuar el resto.
                val approachingChanged = approaching?.osmId != lastApproachingId
                val routeReset = routeRevision < lastOverlayRevision ||
                    (route.isEmpty() && routeOverlays.polyline != null)
                val revisionChanged = routeRevision != lastOverlayRevision
                val routeGrew = revisionChanged && !routeReset && route.size > lastOverlaySize
                val missingPolyline = route.isNotEmpty() && routeOverlays.polyline == null
                val needsFullRebuild = camerasChanged || approachingChanged || routeReset || missingPolyline ||
                    (revisionChanged && !routeGrew)

                if (needsFullRebuild) {
                    routeOverlays = rebuildMapOverlays(map, route, cameras, approaching?.osmId)
                    lastOverlayCameraCount = cameras.size
                    lastApproachingId = approaching?.osmId
                } else if (routeGrew) {
                    appendRoutePoints(routeOverlays.polyline, route, lastOverlaySize)
                    updateCurrentPositionMarker(routeOverlays.marker, route.last())
                }
                lastOverlayRevision = routeRevision
                lastOverlaySize = route.size

                if (route.isNotEmpty()) {
                    val last = route.last()
                    if (routeRevision != lastCenteredRevision) {
                        lastCenteredRevision = routeRevision
                        map.controller.setCenter(GeoPoint(last.lat, last.lon))
                    }
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

        FloatingActionButton(
            onClick = {
                openExternalNavigation(
                    context,
                    route.lastOrNull() ?: viewModel.initialCenter.value,
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Navigation, contentDescription = "Abrir en Maps")
        }
    }
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
    approachingId: Long?,
): RouteOverlays {
    map.overlays.clear()
    // Con un radar objetivo activo, los demás se atenúan: el mapa informa el radar al que
    // VAS, no todos los que existen alrededor.
    cameras.forEach { cam ->
        val isTarget = cam.osmId == approachingId
        val dimmed = approachingId != null && !isTarget
        map.overlays.add(speedCameraAlertCircle(map, cam, isTarget, dimmed))
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

private fun speedCameraAlertCircle(
    map: MapView,
    camera: SpeedCameraEntity,
    isTarget: Boolean,
    dimmed: Boolean,
) = Polygon(map).apply {
    points = Polygon.pointsAsCircle(
        GeoPoint(camera.latitude, camera.longitude),
        CAMERA_ALERT_RADIUS_METERS,
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
) {
    val uri = if (target != null) "geo:${target.lat},${target.lon}" else "geo:0,0"
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    }
}
