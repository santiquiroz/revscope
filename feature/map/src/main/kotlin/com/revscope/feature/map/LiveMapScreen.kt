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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.SpeedCameraEntity
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
    val cameras by viewModel.cameras.collectAsState()
    val context = LocalContext.current

    // Evita recentrar el mapa (y pelear con el usuario si lo está paneando) en cada
    // recomposición disparada por lecturas OBD; solo recentra cuando cambia el número
    // de puntos de la ruta, y usa la ubicación inicial una única vez sin viaje activo.
    var lastCenteredRouteSize by remember { mutableStateOf(-1) }
    var hasCenteredInitial by remember { mutableStateOf(false) }

    // Evita limpiar y reconstruir los overlays (hasta 18.000 puntos de ruta) en cada
    // recomposición; solo reconstruye cuando cambia la cantidad de puntos o de cámaras.
    var lastOverlayRouteSize by remember { mutableStateOf(-1) }
    var lastOverlayCameraCount by remember { mutableStateOf(-1) }

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
                val overlaysStale = route.size != lastOverlayRouteSize ||
                    cameras.size != lastOverlayCameraCount
                if (overlaysStale) {
                    rebuildMapOverlays(map, route, cameras)
                    lastOverlayRouteSize = route.size
                    lastOverlayCameraCount = cameras.size
                }
                if (route.isNotEmpty()) {
                    val last = route.last()
                    if (route.size != lastCenteredRouteSize) {
                        lastCenteredRouteSize = route.size
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

private fun rebuildMapOverlays(
    map: MapView,
    route: List<LiveRouteHolder.RoutePoint>,
    cameras: List<SpeedCameraEntity>,
) {
    map.overlays.clear()
    cameras.forEach { cam ->
        map.overlays.add(speedCameraAlertCircle(map, cam))
        map.overlays.add(speedCameraMarker(map, cam))
    }
    if (route.isEmpty()) return
    val last = route.last()
    map.overlays.add(Polyline(map).apply {
        setPoints(route.map { GeoPoint(it.lat, it.lon) })
        outlinePaint.color = 0xFFE8FF00.toInt()
        outlinePaint.strokeWidth = 8f
    })
    map.overlays.add(Marker(map).apply {
        position = GeoPoint(last.lat, last.lon)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        title = "Tú"
    })
}

private fun speedCameraAlertCircle(
    map: MapView,
    camera: SpeedCameraEntity,
) = Polygon(map).apply {
    points = Polygon.pointsAsCircle(
        GeoPoint(camera.latitude, camera.longitude),
        CAMERA_ALERT_RADIUS_METERS,
    )
    fillPaint.color = 0x22FF5252
    outlinePaint.color = 0x66FF5252.toInt()
    outlinePaint.strokeWidth = 2f
}

private fun speedCameraMarker(
    map: MapView,
    camera: SpeedCameraEntity,
) = Marker(map).apply {
    position = GeoPoint(camera.latitude, camera.longitude)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
    title = "Radar" + (camera.maxSpeedKmh?.let { " · $it km/h" } ?: "")
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
