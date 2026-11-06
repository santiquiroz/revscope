package com.revscope.feature.map

import com.revscope.core.common.i18n.StringResolver
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Claves de las properties "extra" que LiveMapLayers.marker() agrega a un Feature y que
// describableProperties (en LiveMapLayers.kt) vuelve a leer al tocar el mapa (fix D). Viven acá
// porque este archivo es el dueño del contrato de entrada de describeFeature.
internal const val FEATURE_PROP_NAME = "nombre"
internal const val FEATURE_PROP_SPEED_LIMIT_KMH = "limite_kmh"
internal const val FEATURE_PROP_SPEED_KMH = "velocidad_kmh"
internal const val FEATURE_PROP_HITS = "golpes"
internal const val FEATURE_PROP_LAST_HIT_MS = "ultimo_golpe_ms"

/** Título + subtítulo (1-2 líneas) de la tarjeta que aparece al tocar un ícono del mapa. */
internal data class MapFeatureDescription(val title: String, val subtitle: String?)

/**
 * Puro: sin dependencias de Android ni de MapLibre — [properties] ya llega como strings/números
 * planos, extraídos del Feature de GeoJSON por describableProperties (LiveMapLayers.kt), que es
 * el único punto del módulo que toca el SDK del mapa.
 *
 * null = sin tarjeta: tipo desconocido o el propio puck (ICON_ME/ICON_ME_MOTO/ICON_ME_AUTO) —
 * tocar la propia posición no aporta nada que describir.
 */
internal fun describeFeature(
    kind: String?,
    properties: Map<String, Any?>,
    resolver: StringResolver,
): MapFeatureDescription? = when (kind) {
    ICON_CAMERA, ICON_CAMERA_TARGET -> describeRadar(properties, resolver)
    ICON_POTHOLE -> describePothole(properties, resolver)
    ICON_DESTINATION -> describeDestination(properties, resolver)
    ICON_PEER, ICON_PEER_RUMBO -> describePeer(properties, resolver)
    else -> null
}

private fun describeRadar(properties: Map<String, Any?>, resolver: StringResolver): MapFeatureDescription {
    val limitKmh = properties.numberOrNull(FEATURE_PROP_SPEED_LIMIT_KMH)?.toInt()
    val subtitle = if (limitKmh != null) {
        resolver.get(R.string.map_feature_camera_limit, limitKmh)
    } else {
        resolver.get(R.string.map_feature_camera_no_limit)
    }
    return MapFeatureDescription(title = resolver.get(R.string.map_feature_camera_title), subtitle = subtitle)
}

private fun describePothole(properties: Map<String, Any?>, resolver: StringResolver): MapFeatureDescription {
    val hits = properties.numberOrNull(FEATURE_PROP_HITS)?.toInt() ?: 1
    val lastHitAtMs = properties.numberOrNull(FEATURE_PROP_LAST_HIT_MS)?.toLong()
    val subtitle = lastHitAtMs?.let { formatPotholeSubtitle(hits, it, resolver) }
    return MapFeatureDescription(title = resolver.get(R.string.map_feature_pothole_title), subtitle = subtitle)
}

private fun formatPotholeSubtitle(hits: Int, lastHitAtMs: Long, resolver: StringResolver): String {
    val date = formatHitDate(lastHitAtMs)
    return if (hits > 1) {
        resolver.get(R.string.map_feature_pothole_hits, hits, date)
    } else {
        resolver.get(R.string.map_feature_pothole_once, date)
    }
}

private fun describeDestination(properties: Map<String, Any?>, resolver: StringResolver): MapFeatureDescription {
    val name = properties[FEATURE_PROP_NAME] as? String
    return MapFeatureDescription(title = name ?: resolver.get(R.string.map_feature_destination), subtitle = null)
}

private fun describePeer(properties: Map<String, Any?>, resolver: StringResolver): MapFeatureDescription {
    val name = properties[FEATURE_PROP_NAME] as? String ?: resolver.get(R.string.map_feature_peer)
    val speedKmh = properties.numberOrNull(FEATURE_PROP_SPEED_KMH)?.toInt()
    val subtitle = if (speedKmh != null) {
        resolver.get(R.string.map_feature_peer_speed, speedKmh)
    } else {
        resolver.get(R.string.map_feature_peer_in_room)
    }
    return MapFeatureDescription(title = name, subtitle = subtitle)
}

private fun Map<String, Any?>.numberOrNull(key: String): Number? = this[key] as? Number

private fun formatHitDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HIT_DATE_FORMAT)

private val HIT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es"))
