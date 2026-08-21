package com.revscope.feature.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.data.db.entities.VehicleType
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
import kotlin.math.roundToInt

// Anchos heredados de osmdroid, donde strokeWidth eran píxeles FÍSICOS.
private const val LIVE_ROUTE_PHYSICAL_PX = 8f
private const val PLANNED_ROUTE_PHYSICAL_PX = 10f
private const val ALT_ROUTE_PHYSICAL_PX = 8f
private const val CIRCLE_TARGET_PHYSICAL_PX = 4f
private const val CIRCLE_NORMAL_PHYSICAL_PX = 2f
private const val CIRCLE_DIMMED_PHYSICAL_PX = 1f

private const val SRC_PLANNED = "src-ruta-planeada"
private const val LYR_PLANNED = "lyr-ruta-planeada"
private const val SRC_PLANNED_ALT = "src-rutas-alt"
private const val LYR_PLANNED_ALT = "lyr-rutas-alt"
private const val SRC_CIRCLES = "src-circulos-radar"
private const val LYR_CIRCLES_FILL = "lyr-circulos-radar-relleno"
private const val LYR_CIRCLES_LINE = "lyr-circulos-radar-borde"
private const val SRC_MARKERS = "src-marcadores"
private const val LYR_MARKERS = "lyr-marcadores"
private const val LYR_PEER_LABELS = "peer-labels"
private const val SRC_LIVE = "src-ruta-viva"
private const val LYR_LIVE = "lyr-ruta-viva"

private const val PROP_STATE = "estado"
private const val PROP_KIND = "tipo"
private const val PROP_HEADING = "heading"
private const val PROP_LABEL = "label"
private const val STATE_TARGET = "objetivo"
private const val STATE_NORMAL = "normal"
private const val STATE_DIMMED = "atenuado"

private const val ICON_DESTINATION = "icono-destino"
private const val ICON_PEER = "icono-peer"
private const val ICON_PEER_RUMBO = "icono-peer-rumbo"
private const val ICON_POTHOLE = "icono-hueco"
private const val ICON_CAMERA = "icono-radar"
private const val ICON_CAMERA_TARGET = "icono-radar-objetivo"
// internal (no private): la selección de ícono del puck (puckIcon) tiene test unitario chico
// en el mismo módulo y necesita comparar contra estos valores.
internal const val ICON_ME = "icono-yo"
internal const val ICON_ME_MOTO = "icono-yo-moto"
internal const val ICON_ME_AUTO = "icono-yo-auto"

private const val TEXT_FONT = "Noto Sans Regular"

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
    // Rutas de OSRM que NO son la elegida (spec F6) — se dibujan en gris debajo de la
    // planeada; la elegida nunca aparece acá dos veces (ver alternativeGeometries).
    val routeAlternatives: List<NavigationRoute> = emptyList(),
    // Tipo de vehículo del perfil activo — null sin perfil configurado, cae al dot plano
    // (ver puckIcon). La capa no lee storage: el dato viaja desde el caller (LiveMapScreen).
    val vehicleType: VehicleType? = null,
    // Rumbo del puck en grados (0 = norte) — hoy currentBearingDegrees(route) desde el
    // caller; un futuro NavBearing puede alimentar esta misma property sin tocar la capa.
    val bearingDeg: Double = 0.0,
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

    // Las alternativas grises van DEBAJO de la planeada (se agregan primero): así la elegida
    // siempre queda encima y visible, aunque dos rutas compartan tramo.
    style.addSource(GeoJsonSource(SRC_PLANNED_ALT, alternativeGeometries(data)))
    style.addLayer(
        LineLayer(LYR_PLANNED_ALT, SRC_PLANNED_ALT).withProperties(
            PropertyFactory.lineColor("#6B7089"),
            PropertyFactory.lineWidth(physicalPxToDp(ALT_ROUTE_PHYSICAL_PX, density)),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            PropertyFactory.lineOpacity(0.6f),
        ),
    )

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
            // Los huecos, el marcador propio y la flecha de rumbo van centrados en el punto
            // (la flecha gira sobre su propio eje con iconRotate); el resto anclados abajo,
            // como en osmdroid.
            PropertyFactory.iconAnchor(
                Expression.match(
                    Expression.get(PROP_KIND),
                    Expression.literal(Property.ICON_ANCHOR_BOTTOM),
                    Expression.stop(ICON_POTHOLE, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                    Expression.stop(ICON_ME, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                    Expression.stop(ICON_ME_MOTO, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                    Expression.stop(ICON_ME_AUTO, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                    Expression.stop(ICON_PEER_RUMBO, Expression.literal(Property.ICON_ANCHOR_CENTER)),
                ),
            ),
            // Property "heading" en grados (0 en marcadores sin rumbo, inofensivo: solo la
            // flecha de rumbo es asimétrica y por lo tanto sensible a la rotación).
            PropertyFactory.iconRotate(Expression.get(PROP_HEADING)),
            // Default es "auto" (viewport con placement de punto): con course-up el mapa rota
            // pero el icono no, y el puck/flecha de rumbo terminan apuntando de costado. "map"
            // rota el icono junto con el mapa.
            PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
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

    style.addLayer(
        SymbolLayer(LYR_PEER_LABELS, SRC_MARKERS).withProperties(
            PropertyFactory.textField(Expression.get(PROP_LABEL)),
            PropertyFactory.textFont(arrayOf(TEXT_FONT)),
            PropertyFactory.textSize(12f),
            // Encima del pin/flecha del peer, no encima de su propio anclaje del marcador.
            PropertyFactory.textOffset(arrayOf(0f, -2.2f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_BOTTOM),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textHaloColor("#0A0A0F"),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ).withFilter(Expression.has(PROP_LABEL)),
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
    style.getSourceAs<GeoJsonSource>(SRC_PLANNED_ALT)?.setGeoJson(alternativeGeometries(data))
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

/** Todas las alternativas MENOS la elegida — comparación estructural, no de instancia:
 * `_plannedRoute` puede llegar como una copia equivalente y seguiría siendo "la misma". */
private fun alternativeGeometries(data: LiveMapData): FeatureCollection {
    val features = data.routeAlternatives
        .filter { it != data.plannedRoute }
        .map { route -> Feature.fromGeometry(LineString.fromLngLats(route.points.map { Point.fromLngLat(it.lon, it.lat) })) }
    return FeatureCollection.fromFeatures(features)
}

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
    data.peers.forEach { peer ->
        val icon = if (peer.headingDeg != null) ICON_PEER_RUMBO else ICON_PEER
        features += marker(peer.lat, peer.lon, icon, heading = peer.headingDeg ?: 0.0, label = peerLabel(peer))
    }
    data.potholes.forEach { features += marker(it.latitude, it.longitude, ICON_POTHOLE) }
    data.cameras.forEach { cam ->
        val icon = if (cam.osmId == data.approachingId) ICON_CAMERA_TARGET else ICON_CAMERA
        features += marker(cam.latitude, cam.longitude, icon)
    }
    // Con viaje activo el puck sigue la ruta viva; sin viaje, el fix del provider del mapa.
    (data.route.lastOrNull() ?: data.liveFix)?.let {
        features += marker(it.lat, it.lon, puckIcon(data.vehicleType), heading = data.bearingDeg)
    }

    return FeatureCollection.fromFeatures(features)
}

/** Ícono del puck según el vehículo activo — sin perfil configurado cae al dot plano. */
internal fun puckIcon(vehicleType: VehicleType?): String = when (vehicleType) {
    VehicleType.MOTORCYCLE -> ICON_ME_MOTO
    VehicleType.CAR -> ICON_ME_AUTO
    null -> ICON_ME
}

private fun marker(
    lat: Double,
    lon: Double,
    kind: String,
    heading: Double = 0.0,
    label: String? = null,
): Feature =
    Feature.fromGeometry(Point.fromLngLat(lon, lat)).apply {
        addStringProperty(PROP_KIND, kind)
        addNumberProperty(PROP_HEADING, heading)
        label?.let { addStringProperty(PROP_LABEL, it) }
    }

/** "$rider" solo, o "$rider\n$speed km/h" cuando el peer reporta velocidad. */
private fun peerLabel(peer: RoomClient.Peer): String {
    val speedKmh = peer.speedKmh ?: return peer.rider
    return "${peer.rider}\n${speedKmh.roundToInt()} km/h"
}

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
    style.addImage(ICON_PEER_RUMBO, arrowBitmap(0xFFE8FF00.toInt()))
    style.addImage(ICON_POTHOLE, dotBitmap(0xFFFFA726.toInt()))
    style.addImage(ICON_CAMERA, pinBitmap(0xFFFF5252.toInt()))
    style.addImage(ICON_CAMERA_TARGET, pinBitmap(0xFFFF1744.toInt()))
    style.addImage(ICON_ME, dotBitmap(0xFFE8FF00.toInt()))
    style.addImage(ICON_ME_MOTO, motoBitmap(0xFFE8FF00.toInt()))
    style.addImage(ICON_ME_AUTO, autoBitmap(0xFFE8FF00.toInt()))
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

/** Flecha apuntando al norte (0°) en reposo: `iconRotate` gira en sentido horario, igual
 *  convención que el rumbo GPS (0 = norte, 90 = este), así que la rotación queda directa. */
private fun arrowBitmap(color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(PIN_SIZE_PX, PIN_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0A0A0F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val tip = 4f
    val tail = PIN_SIZE_PX - 6f
    val waist = PIN_SIZE_PX * 0.68f
    val path = Path().apply {
        moveTo(PIN_SIZE_PX / 2f, tip)
        lineTo(tail, tail)
        lineTo(PIN_SIZE_PX / 2f, waist)
        lineTo(6f, tail)
        close()
    }
    canvas.drawPath(path, body)
    canvas.drawPath(path, border)
    return bmp
}

/**
 * Silueta de moto deportiva vista desde arriba, apuntando al norte (arriba del bitmap) en
 * reposo — morro afilado, cintura angosta y cola partida en dos aletas en flecha (silueta
 * ORIGINAL tipo "speeder", no un calco de ninguna franquicia). El acento cian cerca del morro
 * marca la posición del piloto y suma la paleta neón de la app sin depender de un segundo color
 * de parámetro. */
private fun motoBitmap(color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(PIN_SIZE_PX, PIN_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0A0A0F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val canopy = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF00E5FF.toInt() }
    val cx = PIN_SIZE_PX / 2f
    val path = Path().apply {
        moveTo(cx, 3f)
        lineTo(cx + 6f, 15f)
        lineTo(cx + 3f, 21f)
        lineTo(cx + 11f, 34f)
        lineTo(cx + 1f, 29f)
        lineTo(cx, 41f)
        lineTo(cx - 1f, 29f)
        lineTo(cx - 11f, 34f)
        lineTo(cx - 3f, 21f)
        lineTo(cx - 6f, 15f)
        close()
    }
    canvas.drawPath(path, body)
    canvas.drawPath(path, border)
    canvas.drawCircle(cx, 13f, 2.5f, canopy)
    return bmp
}

/**
 * Silueta de auto deportivo visto desde arriba, apuntando al norte en reposo — morro
 * redondeado y angosto, hombros y cola más anchos (postura ancha "de pista"). El trapecio cian
 * marca el parabrisas/cabina y da lectura de "auto" incluso al tamaño de un pin de mapa. */
private fun autoBitmap(color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(PIN_SIZE_PX, PIN_SIZE_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = 0xFF0A0A0F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val cabin = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0xFF00E5FF.toInt() }
    val cx = PIN_SIZE_PX / 2f
    val path = Path().apply {
        moveTo(cx, 5f)
        quadTo(cx + 11f, 8f, cx + 12f, 20f)
        quadTo(cx + 13f, 30f, cx + 16f, 38f)
        quadTo(cx + 17f, 44f, cx + 9f, 45f)
        lineTo(cx - 9f, 45f)
        quadTo(cx - 17f, 44f, cx - 16f, 38f)
        quadTo(cx - 13f, 30f, cx - 12f, 20f)
        quadTo(cx - 11f, 8f, cx, 5f)
        close()
    }
    canvas.drawPath(path, body)
    canvas.drawPath(path, border)
    val cabinPath = Path().apply {
        moveTo(cx, 12f)
        lineTo(cx + 7f, 24f)
        lineTo(cx - 7f, 24f)
        close()
    }
    canvas.drawPath(cabinPath, cabin)
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
