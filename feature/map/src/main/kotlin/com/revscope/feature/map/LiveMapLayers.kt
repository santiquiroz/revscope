package com.revscope.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.maps.geodesicCircle
import com.revscope.core.maps.physicalPxToDp
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.social.RoomClient
import com.revscope.core.navigation.NavigationRoute
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// Anchos heredados de osmdroid, donde strokeWidth eran píxeles FÍSICOS.
private const val LIVE_ROUTE_PHYSICAL_PX = 8f
private const val PLANNED_ROUTE_PHYSICAL_PX = 10f
private const val CIRCLE_TARGET_PHYSICAL_PX = 4f
private const val CIRCLE_NORMAL_PHYSICAL_PX = 2f
private const val CIRCLE_DIMMED_PHYSICAL_PX = 1f

private const val SRC_PLANNED = "src-ruta-planeada"
private const val LYR_PLANNED = "lyr-ruta-planeada"
private const val SRC_CIRCLES = "src-circulos-radar"
private const val LYR_CIRCLES_FILL = "lyr-circulos-radar-relleno"
private const val LYR_CIRCLES_LINE = "lyr-circulos-radar-borde"
private const val SRC_MARKERS = "src-marcadores"
private const val LYR_MARKERS = "lyr-marcadores"
private const val SRC_LIVE = "src-ruta-viva"
private const val LYR_LIVE = "lyr-ruta-viva"

private const val PROP_STATE = "estado"
private const val PROP_KIND = "tipo"
private const val STATE_TARGET = "objetivo"
private const val STATE_NORMAL = "normal"
private const val STATE_DIMMED = "atenuado"

private const val ICON_DESTINATION = "icono-destino"
private const val ICON_PEER = "icono-peer"
private const val ICON_POTHOLE = "icono-hueco"
private const val ICON_CAMERA = "icono-radar"
private const val ICON_CAMERA_TARGET = "icono-radar-objetivo"
private const val ICON_ME = "icono-yo"

/** Datos que las capas dibujan. Un solo objeto para no arrastrar ocho parámetros. */
data class LiveMapData(
    val route: List<LiveRouteHolder.RoutePoint>,
    val cameras: List<SpeedCameraEntity>,
    val potholes: List<PotholeEntity>,
    val peers: List<RoomClient.Peer>,
    val approachingId: Long?,
    val alertRadiusM: Int,
    val destination: LiveRouteHolder.RoutePoint?,
    val plannedRoute: NavigationRoute?,
    val liveFix: LiveRouteHolder.RoutePoint? = null,
)

/**
 * Crea fuentes y capas con los datos actuales, en orden de abajo hacia arriba:
 * ruta planeada → círculos de radar → marcadores → ruta viva.
 *
 * Se llama en la carga inicial del estilo Y en cada cambio de estilo (modo nocturno): al
 * recargar, las fuentes viejas quedan `detached` y `setGeoJson()` retorna en silencio.
 *
 * Diferencia consciente contra osmdroid: ahí los círculos y pines de radar se intercalaban por
 * cámara, así que el círculo de una tapaba el pin de la anterior. Acá van agrupados en capas,
 * que es lo que hace un motor vectorial; con radares muy juntos el apilado se ve distinto.
 */
fun installLiveMapLayers(style: Style, density: Float, data: LiveMapData) {
    registerIcons(style)

    style.addSource(GeoJsonSource(SRC_PLANNED, plannedGeometry(data.plannedRoute)))
    style.addLayer(
        LineLayer(LYR_PLANNED, SRC_PLANNED).withProperties(
            PropertyFactory.lineColor("#00E5FF"),
            PropertyFactory.lineWidth(physicalPxToDp(PLANNED_ROUTE_PHYSICAL_PX, density)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )

    style.addSource(GeoJsonSource(SRC_CIRCLES, circleFeatures(data)))
    style.addLayer(
        FillLayer(LYR_CIRCLES_FILL, SRC_CIRCLES).withProperties(
            PropertyFactory.fillColor(
                matchState("#FF1744", "#FF5252", "#FF5252"),
            ),
            PropertyFactory.fillOpacity(
                Expression.match(
                    Expression.get(PROP_STATE),
                    Expression.literal(0.13f),
                    Expression.stop(STATE_TARGET, Expression.literal(0.27f)),
                    Expression.stop(STATE_NORMAL, Expression.literal(0.13f)),
                    Expression.stop(STATE_DIMMED, Expression.literal(0.05f)),
                ),
            ),
        ),
    )
    style.addLayer(
        LineLayer(LYR_CIRCLES_LINE, SRC_CIRCLES).withProperties(
            PropertyFactory.lineColor(matchState("#FF1744", "#FF5252", "#FF5252")),
            PropertyFactory.lineOpacity(
                Expression.match(
                    Expression.get(PROP_STATE),
                    Expression.literal(0.4f),
                    Expression.stop(STATE_TARGET, Expression.literal(1.0f)),
                    Expression.stop(STATE_NORMAL, Expression.literal(0.4f)),
                    Expression.stop(STATE_DIMMED, Expression.literal(0.15f)),
                ),
            ),
            PropertyFactory.lineWidth(
                Expression.match(
                    Expression.get(PROP_STATE),
                    Expression.literal(physicalPxToDp(CIRCLE_NORMAL_PHYSICAL_PX, density)),
                    Expression.stop(STATE_TARGET, Expression.literal(physicalPxToDp(CIRCLE_TARGET_PHYSICAL_PX, density))),
                    Expression.stop(STATE_NORMAL, Expression.literal(physicalPxToDp(CIRCLE_NORMAL_PHYSICAL_PX, density))),
                    Expression.stop(STATE_DIMMED, Expression.literal(physicalPxToDp(CIRCLE_DIMMED_PHYSICAL_PX, density))),
                ),
            ),
        ),
    )

    style.addSource(GeoJsonSource(SRC_MARKERS, markerFeatures(data)))
    style.addLayer(
        SymbolLayer(LYR_MARKERS, SRC_MARKERS).withProperties(
            PropertyFactory.iconImage(Expression.get(PROP_KIND)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            // Los huecos y el marcador propio van centrados en el punto; el resto anclados
            // abajo, como en osmdroid.
            PropertyFactory.iconAnchor(
                Expression.match(
                    Expression.get(PROP_KIND),
                    Expression.literal(Property.ICON_ANCHOR_BOTTOM),
                    Expression.stop(ICON_POTHOLE, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                    Expression.stop(ICON_ME, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                ),
            ),
            PropertyFactory.iconOpacity(
                Expression.match(
                    Expression.get(PROP_KIND),
                    Expression.literal(1.0f),
                    Expression.stop(ICON_POTHOLE, Expression.literal(0.85f)),
                    Expression.stop(ICON_CAMERA, Expression.literal(0.75f)),
                ),
            ),
        ),
    )

    style.addSource(GeoJsonSource(SRC_LIVE, liveGeometry(data.route)))
    style.addLayer(
        LineLayer(LYR_LIVE, SRC_LIVE).withProperties(
            PropertyFactory.lineColor("#E8FF00"),
            PropertyFactory.lineWidth(physicalPxToDp(LIVE_ROUTE_PHYSICAL_PX, density)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
}

/** Reescribe los datos sin recrear capas. Silencioso y seguro si el estilo cambió de abajo. */
fun updateLiveMapData(style: Style, data: LiveMapData) {
    style.getSourceAs<GeoJsonSource>(SRC_PLANNED)?.setGeoJson(plannedGeometry(data.plannedRoute))
    style.getSourceAs<GeoJsonSource>(SRC_CIRCLES)?.setGeoJson(circleFeatures(data))
    style.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(markerFeatures(data))
    // Geometría pelada y no FeatureCollection: setGeoJson(FeatureCollection) hace una copia
    // defensiva extra, y esta línea llega a 18.000 puntos.
    style.getSourceAs<GeoJsonSource>(SRC_LIVE)?.setGeoJson(liveGeometry(data.route))
}

/** `toColor` explícito: los stops son strings y las propiedades de color esperan color. */
private fun matchState(target: String, normal: String, dimmed: String): Expression =
    Expression.toColor(
        Expression.match(
            Expression.get(PROP_STATE),
            Expression.literal(normal),
            Expression.stop(STATE_TARGET, Expression.literal(target)),
            Expression.stop(STATE_NORMAL, Expression.literal(normal)),
            Expression.stop(STATE_DIMMED, Expression.literal(dimmed)),
        ),
    )

private fun liveGeometry(route: List<LiveRouteHolder.RoutePoint>): LineString =
    LineString.fromLngLats(route.map { Point.fromLngLat(it.lon, it.lat) })

private fun plannedGeometry(planned: NavigationRoute?): LineString =
    LineString.fromLngLats(
        planned?.points?.map { Point.fromLngLat(it.lon, it.lat) } ?: emptyList(),
    )

private fun circleFeatures(data: LiveMapData): FeatureCollection {
    val features = data.cameras.map { cam ->
        val state = when {
            cam.osmId == data.approachingId -> STATE_TARGET
            data.approachingId != null -> STATE_DIMMED
            else -> STATE_NORMAL
        }
        Feature.fromGeometry(
            geodesicCircle(cam.latitude, cam.longitude, data.alertRadiusM.toDouble()),
        ).apply { addStringProperty(PROP_STATE, state) }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun markerFeatures(data: LiveMapData): FeatureCollection {
    val features = mutableListOf<Feature>()

    data.destination?.let {
        features += marker(it.lat, it.lon, ICON_DESTINATION)
    }
    data.peers.forEach { features += marker(it.lat, it.lon, ICON_PEER) }
    data.potholes.forEach { features += marker(it.latitude, it.longitude, ICON_POTHOLE) }
    data.cameras.forEach { cam ->
        val icon = if (cam.osmId == data.approachingId) ICON_CAMERA_TARGET else ICON_CAMERA
        features += marker(cam.latitude, cam.longitude, icon)
    }
    // Con viaje activo el puck sigue la ruta viva; sin viaje, el fix del provider del mapa.
    (data.route.lastOrNull() ?: data.liveFix)?.let { features += marker(it.lat, it.lon, ICON_ME) }

    return FeatureCollection.fromFeatures(features)
}

private fun marker(lat: Double, lon: Double, kind: String): Feature =
    Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply { addStringProperty(PROP_KIND, kind) }

/**
 * Íconos dibujados en código: al salir osmdroid desaparecen sus drawables por defecto, y no
 * vale la pena arrastrar un set de assets para esta fase.
 *
 * `addImage` hace `.recycle()` del Bitmap, así que cada registro decodifica uno nuevo — nunca
 * se guarda uno en un campo para reusar.
 */
private fun registerIcons(style: Style) {
    style.addImage(ICON_DESTINATION, pinBitmap(0xFF00E5FF.toInt()))
    style.addImage(ICON_PEER, pinBitmap(0xFFE8FF00.toInt()))
    style.addImage(ICON_POTHOLE, dotBitmap(0xFFFFA726.toInt()))
    style.addImage(ICON_CAMERA, pinBitmap(0xFFFF5252.toInt()))
    style.addImage(ICON_CAMERA_TARGET, pinBitmap(0xFFFF1744.toInt()))
    style.addImage(ICON_ME, dotBitmap(0xFFE8FF00.toInt()))
}

private const val PIN_SIZE_PX = 48
private const val DOT_SIZE_PX = 28

private fun pinBitmap(color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(PIN_SIZE_PX, PIN_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0A0A0F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val cx = PIN_SIZE_PX / 2f
    val radius = PIN_SIZE_PX / 2f - 6f
    canvas.drawCircle(cx, radius + 3f, radius, body)
    canvas.drawCircle(cx, radius + 3f, radius, border)
    // Punta hacia abajo: el anchor BOTTOM la deja sobre la coordenada real.
    canvas.drawCircle(cx, PIN_SIZE_PX - 4f, 3.5f, body)
    return bmp
}

private fun dotBitmap(color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(DOT_SIZE_PX, DOT_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0A0A0F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val c = DOT_SIZE_PX / 2f
    canvas.drawCircle(c, c, c - 3f, body)
    canvas.drawCircle(c, c, c - 3f, border)
    return bmp
}
