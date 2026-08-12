package com.revscope.feature.session

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.revscope.core.maps.MapLibreMapView
import com.revscope.core.maps.MapStyleProvider
import com.revscope.core.maps.boundsOf
import com.revscope.core.maps.physicalPxToDp
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val SEGMENT_SIZE = 8
private val AttributionColor = Color(0xFF6B7089)

// Anchos heredados de osmdroid, donde strokeWidth eran píxeles FÍSICOS. Se convierten por
// densidad antes de pasarlos a MapLibre, que interpreta lineWidth densidad-independiente.
private const val CASING_PHYSICAL_PX = 16f
private const val SEGMENT_PHYSICAL_PX = 12f

private const val SRC_CASING = "src-recorrido-casing"
private const val LYR_CASING = "lyr-recorrido-casing"
private const val SRC_SEGMENTS = "src-recorrido-segmentos"
private const val LYR_SEGMENTS = "lyr-recorrido-segmentos"
private const val PROP_COLOR = "color"

private const val BOUNDS_PADDING_DP = 24

/**
 * El recorrido del viaje sobre calles reales de OpenStreetMap. Los segmentos se gradúan por
 * velocidad como la racing line offline: azul lento → amarillo → rojo rápido.
 *
 * Una sola [LineLayer] con el color por expresión en vez de una polilínea por segmento: con
 * 600 puntos downsampleados eran 76 overlays.
 */
@Composable
fun RealTrackMap(
    track: List<Pair<Double, Double>>,
    speeds: List<Float>,
    modifier: Modifier = Modifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    // Sin extracto vectorial propio todavía: el tier ráster mantiene las mismas calles que
    // mostraba osmdroid. El replay de un viaje no necesita modo oscuro.
    val styleJson = remember { MapStyleProvider.styleJson(tilesUrl = null, dark = false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        MapLibreMapView(
            modifier = modifier,
            styleJson = styleJson,
        ) { map, style ->
            if (track.size < 2) return@MapLibreMapView

            style.addSource(GeoJsonSource(SRC_CASING, casingGeometry(track)))
            style.addLayer(
                LineLayer(LYR_CASING, SRC_CASING).withProperties(
                    PropertyFactory.lineColor(AndroidColor.parseColor("#CCFFFFFF")),
                    PropertyFactory.lineWidth(physicalPxToDp(CASING_PHYSICAL_PX, density)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )

            style.addSource(GeoJsonSource(SRC_SEGMENTS, segmentFeatures(track, speeds)))
            style.addLayer(
                LineLayer(LYR_SEGMENTS, SRC_SEGMENTS).withProperties(
                    PropertyFactory.lineColor(Expression.get(PROP_COLOR)),
                    PropertyFactory.lineWidth(physicalPxToDp(SEGMENT_PHYSICAL_PX, density)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )

            boundsOf(track)?.let { b ->
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(b[0], b[1]))
                    .include(LatLng(b[2], b[3]))
                    .build()
                val padding = (BOUNDS_PADDING_DP * density).toInt()
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
            }
        }
        Text(
            "© OpenStreetMap contributors",
            color = AttributionColor,
            fontSize = 9.sp,
        )
    }
}

private fun casingGeometry(track: List<Pair<Double, Double>>): LineString =
    LineString.fromLngLats(track.map { Point.fromLngLat(it.second, it.first) })

/**
 * Segmentos contiguos de [SEGMENT_SIZE] puntos que **comparten vértice** con el siguiente
 * (`index = end`), para que no queden huecos. Cada uno lleva su color como propiedad.
 *
 * La escala del gradiente es RELATIVA al máximo de esta sesión: un viaje urbano lento igual
 * muestra rojo en su propio máximo. Si las velocidades no vienen alineadas con el track, todos
 * los segmentos quedan planos en amarillo — nunca se indexa fuera de rango.
 */
private fun segmentFeatures(
    track: List<Pair<Double, Double>>,
    speeds: List<Float>,
): FeatureCollection {
    val hasSpeeds = speeds.size == track.size && speeds.isNotEmpty()
    val maxSpeed = if (hasSpeeds) speeds.max().coerceAtLeast(1f) else 1f
    val features = mutableListOf<Feature>()

    var index = 0
    while (index < track.size - 1) {
        val end = minOf(index + SEGMENT_SIZE, track.size - 1)
        val points = (index..end).map { Point.fromLngLat(track[it].second, track[it].first) }
        val color = if (hasSpeeds) {
            val avg = (index..end).map { speeds[it] }.average().toFloat() / maxSpeed
            speedToColorHex(avg.coerceIn(0f, 1f))
        } else {
            "#E8FF00"
        }
        features += Feature.fromGeometry(LineString.fromLngLats(points)).apply {
            addStringProperty(PROP_COLOR, color)
        }
        index = end
    }
    return FeatureCollection.fromFeatures(features)
}

private fun speedToColorHex(fraction: Float): String {
    fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
    val slow = Triple(0x3D, 0x8B, 0xFF)
    val mid = Triple(0xE8, 0xFF, 0x00)
    val fast = Triple(0xFF, 0x3D, 0x5A)
    val (from, to, t) = if (fraction < 0.5f) Triple(slow, mid, fraction * 2f)
    else Triple(mid, fast, (fraction - 0.5f) * 2f)
    return String.format(
        "#%02X%02X%02X",
        lerp(from.first, to.first, t),
        lerp(from.second, to.second, t),
        lerp(from.third, to.third, t),
    )
}
