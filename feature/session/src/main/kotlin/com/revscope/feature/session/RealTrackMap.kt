package com.revscope.feature.session

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import com.revscope.core.maps.MapLibreMapView
import com.revscope.core.maps.MapStyleProvider
import com.revscope.core.maps.boundsOf
import com.revscope.core.maps.physicalPxToDp
import com.revscope.core.maps.peekMapLayersAsset
import com.revscope.core.maps.readMapLayersAsset
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    // Misma cascada que LiveMapScreen (T5 + fix W1): local (.pmtiles ya descargado) → remoto
    // (release tiles-v1 de GitHub, streamed por HTTP range) → ráster. Esta pantalla es puntual
    // (un viaje ya terminado) — a diferencia de LiveMapScreen no observa MapDownloadService en
    // vivo ni muestra el banner de promo ni el de "mapa dañado" acá (sin ViewModel propio para
    // persistir nada de eso), solo lee el disco una vez al componer (aceptado: T5 plan).
    //
    // remoteTilesFailed SÍ hace falta pese a lo anterior: a diferencia del tier ráster (cada
    // tile falla solo, en silencio, sin tocar el resto del estilo), el tier vectorial remoto
    // necesita resolver la metadata del `.pmtiles` antes de poder dibujar nada — sin red, la
    // fuente entera falla y el estilo completo (incluidas las capas del track que agrega este
    // composable más abajo) no llega a instalarse. Sin este flag, un replay sin red y sin
    // `.pmtiles` local quedaría en blanco para siempre, sin ni siquiera el trazado. Local,
    // de sesión de esta pantalla (remember sin key), igual que remoteTilesFailed en
    // LiveMapScreen — nunca reintenta el tier remoto en loop dentro de la misma visita.
    var remoteTilesFailed by remember { mutableStateOf(false) }
    val tilesUrl = remember(context, remoteTilesFailed) {
        MapStyleProvider.tilesUrl(
            localFile = MapStyleProvider.localMapFile(context.filesDir),
            serverBaseUrl = null,
            remoteUrl = if (remoteTilesFailed) null else MapStyleProvider.REMOTE_PMTILES_URL,
        )
    }
    // El asset de capas pesa ~240 KB: leerlo síncrono en composición bloquearía el frame — corre
    // en IO, igual que LiveMapScreen (dark fijo false acá, así que sin key adicional). Sembrado
    // desde el cache (peekMapLayersAsset, sin IO): si LiveMapScreen ya leyó el tema claro en
    // este proceso, esta pantalla arranca con el estilo final de una, sin frame de placeholder.
    val layersJson by produceState<String?>(initialValue = peekMapLayersAsset(dark = false)) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { readMapLayersAsset(context, dark = false) }
        }
    }
    val styleJson = remember(tilesUrl, layersJson) {
        MapStyleProvider.styleJson(tilesUrl = tilesUrl, dark = false, layersJson = layersJson)
    }

    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleEpoch by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxWidth()) {
        MapLibreMapView(
            modifier = modifier,
            styleJson = styleJson,
            onDidFailLoadingMap = { _ ->
                // Local corrupto: sin ViewModel acá para borrar/avisar (gap aceptado, ver
                // comentario de arriba) — no hay nada seguro que hacer más que no relanzar el
                // tier remoto, que tampoco es la causa. Solo el tier remoto reacciona.
                if (tilesUrl?.startsWith("pmtiles://file://") != true) remoteTilesFailed = true
            },
        ) { map, style ->
            // Las capas se crean vacías: el track llega asíncrono del ViewModel, así que en la
            // primera composición todavía no está. Poblarlas acá y salir dejaría el mapa sin
            // trazado para siempre, porque el estilo no vuelve a cargar.
            style.addSource(GeoJsonSource(SRC_CASING, emptyLine()))
            style.addLayer(
                LineLayer(LYR_CASING, SRC_CASING).withProperties(
                    PropertyFactory.lineColor(AndroidColor.parseColor("#CCFFFFFF")),
                    PropertyFactory.lineWidth(physicalPxToDp(CASING_PHYSICAL_PX, density)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )

            style.addSource(GeoJsonSource(SRC_SEGMENTS, FeatureCollection.fromFeatures(emptyList())))
            style.addLayer(
                LineLayer(LYR_SEGMENTS, SRC_SEGMENTS).withProperties(
                    // toColor es obligatorio: get() devuelve string y lineColor espera color.
                    // Sin la conversión la capa no pinta nada, en silencio.
                    PropertyFactory.lineColor(Expression.toColor(Expression.get(PROP_COLOR))),
                    PropertyFactory.lineWidth(physicalPxToDp(SEGMENT_PHYSICAL_PX, density)),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )

            mapRef = map
            styleEpoch++
        }

        LaunchedEffect(styleEpoch, track, speeds) {
            val map = mapRef ?: return@LaunchedEffect
            val style = map.style ?: return@LaunchedEffect
            if (!style.isFullyLoaded() || track.size < 2) return@LaunchedEffect

            style.getSourceAs<GeoJsonSource>(SRC_CASING)?.setGeoJson(casingGeometry(track))
            style.getSourceAs<GeoJsonSource>(SRC_SEGMENTS)?.setGeoJson(segmentFeatures(track, speeds))

            boundsOf(track)?.let { b ->
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(b[0], b[1]))
                    .include(LatLng(b[2], b[3]))
                    .build()
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, (BOUNDS_PADDING_DP * density).toInt()),
                )
            }
        }
        Text(
            "© OpenStreetMap contributors",
            color = AttributionColor,
            fontSize = 9.sp,
        )
    }
}

private fun emptyLine(): LineString = LineString.fromLngLats(emptyList<Point>())

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
