package com.revscope.feature.map

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
internal fun describeFeature(kind: String?, properties: Map<String, Any?>): MapFeatureDescription? = when (kind) {
    ICON_CAMERA, ICON_CAMERA_TARGET -> describeRadar(properties)
    ICON_POTHOLE -> describePothole(properties)
    ICON_DESTINATION -> describeDestination(properties)
    ICON_PEER, ICON_PEER_RUMBO -> describePeer(properties)
    else -> null
}

private fun describeRadar(properties: Map<String, Any?>): MapFeatureDescription {
    val limitKmh = properties.numberOrNull(FEATURE_PROP_SPEED_LIMIT_KMH)?.toInt()
    val subtitle = if (limitKmh != null) "Fotomulta · límite $limitKmh km/h" else "Fotomulta · límite no registrado"
    return MapFeatureDescription(title = "Radar fijo", subtitle = subtitle)
}

private fun describePothole(properties: Map<String, Any?>): MapFeatureDescription {
    val hits = properties.numberOrNull(FEATURE_PROP_HITS)?.toInt() ?: 1
    val lastHitAtMs = properties.numberOrNull(FEATURE_PROP_LAST_HIT_MS)?.toLong()
    val subtitle = lastHitAtMs?.let { formatPotholeSubtitle(hits, it) }
    return MapFeatureDescription(title = "Hueco reportado", subtitle = subtitle)
}

private fun formatPotholeSubtitle(hits: Int, lastHitAtMs: Long): String {
    val date = formatHitDate(lastHitAtMs)
    return if (hits > 1) "Reportado ${hits}× · última vez $date" else "Reportado el $date"
}

private fun describeDestination(properties: Map<String, Any?>): MapFeatureDescription {
    val name = properties[FEATURE_PROP_NAME] as? String
    return MapFeatureDescription(title = name ?: "Destino", subtitle = null)
}

private fun describePeer(properties: Map<String, Any?>): MapFeatureDescription {
    val name = properties[FEATURE_PROP_NAME] as? String ?: "Compañero de sala"
    val speedKmh = properties.numberOrNull(FEATURE_PROP_SPEED_KMH)?.toInt()
    val subtitle = if (speedKmh != null) "$speedKmh km/h · en sala" else "En sala"
    return MapFeatureDescription(title = name, subtitle = subtitle)
}

private fun Map<String, Any?>.numberOrNull(key: String): Number? = this[key] as? Number

private fun formatHitDate(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(HIT_DATE_FORMAT)

private val HIT_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es"))
