package com.revscope.feature.session

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.Text
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

private const val SEGMENT_SIZE = 8
private val AttributionColor = Color(0xFF6B7089)

/**
 * The trip route over real OpenStreetMap streets (osmdroid — no API key, tiles
 * cached offline after first view). Route segments graded by speed like the
 * offline racing line: blue slow → yellow → red fast.
 */
@Composable
fun RealTrackMap(
    track: List<Pair<Double, Double>>,
    speeds: List<Float>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                Configuration.getInstance().apply {
                    userAgentValue = context.packageName
                    load(context, context.getSharedPreferences("osmdroid", 0))
                }
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                }
            },
            update = { map ->
                map.overlays.clear()
                if (track.size >= 2) {
                    addSpeedGradedRoute(map, track, speeds)
                    val points = track.map { GeoPoint(it.first, it.second) }
                    map.post {
                        map.zoomToBoundingBox(
                            BoundingBox.fromGeoPoints(points).increaseByScale(1.25f),
                            false,
                        )
                    }
                }
                map.invalidate()
            },
        )
        Text(
            "© OpenStreetMap contributors",
            color = AttributionColor,
            fontSize = 9.sp,
        )
    }
}

private fun addSpeedGradedRoute(
    map: MapView,
    track: List<Pair<Double, Double>>,
    speeds: List<Float>,
) {
    val hasSpeeds = speeds.size == track.size && speeds.isNotEmpty()
    val maxSpeed = if (hasSpeeds) speeds.max().coerceAtLeast(1f) else 1f

    var index = 0
    while (index < track.size - 1) {
        val end = minOf(index + SEGMENT_SIZE, track.size - 1)
        val segment = (index..end).map { GeoPoint(track[it].first, track[it].second) }
        val color = if (hasSpeeds) {
            val avg = (index..end).map { speeds[it] }.average().toFloat() / maxSpeed
            speedToColor(avg.coerceIn(0f, 1f))
        } else {
            AndroidColor.parseColor("#E8FF00")
        }
        map.overlays.add(
            Polyline(map).apply {
                setPoints(segment)
                outlinePaint.color = color
                outlinePaint.strokeWidth = 9f
            }
        )
        index = end
    }
}

private fun speedToColor(fraction: Float): Int {
    fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
    val slow = Triple(0x3D, 0x8B, 0xFF)
    val mid = Triple(0xE8, 0xFF, 0x00)
    val fast = Triple(0xFF, 0x3D, 0x5A)
    val (from, to, t) = if (fraction < 0.5f) Triple(slow, mid, fraction * 2f)
    else Triple(mid, fast, (fraction - 0.5f) * 2f)
    return AndroidColor.rgb(
        lerp(from.first, to.first, t),
        lerp(from.second, to.second, t),
        lerp(from.third, to.third, t),
    )
}
