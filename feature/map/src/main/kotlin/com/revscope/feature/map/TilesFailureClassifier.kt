package com.revscope.feature.map

/** Acción a tomar ante un fallo de MapLibre (onDidFailLoadingMap) sobre el mapa en vivo. */
internal enum class TilesFailureAction { DELETE_LOCAL, DEGRADE_REMOTE, IGNORE }

/**
 * Clasifica un fallo de MapLibre por el tag del estilo que REALMENTE falló ([failedTier]) —
 * nunca por el tier "actual" de la composición (fix W1 CRITICAL).
 *
 * `MapLibreMapView.onDidFailLoadingMap` se despacha vía `rememberUpdatedState`, que SIEMPRE
 * reenvía a la lambda más reciente del caller. Sin esta correlación explícita, el flujo primario
 * del feature la dispara así: el usuario abre el mapa con el tier REMOTE activo (sin `.pmtiles`
 * local todavía) y, desde el banner de promo de esta misma pantalla, arranca la descarga
 * completa de 913 MB — el streaming del tier remoto compite por ancho de banda con esa descarga,
 * así que su fallo es más probable justo en ese momento. Si la descarga completa MIENTRAS el
 * request remoto sigue en vuelo, la composición ya avanzó a tier LOCAL antes de que el fallo
 * (tardío) del tier REMOTE llegue — clasificarlo por el tier "actual" lo atribuye a LOCAL, borra
 * el `.pmtiles` recién descargado y muestra un banner de "mapa dañado" falso. Viola el contrato
 * documentado: el tier REMOTE nunca borra nada, porque nunca descargó nada.
 *
 * Esta función es deliberadamente ciega a cualquier estado "actual": solo mira [failedTier], el
 * tag que MapLibreMapView correlacionó con el estilo en el momento exacto de aplicarlo. Es la
 * pieza pura y testeable de esa corrección — el wiring de Compose que produce/consume el tag
 * (LiveMapScreen.kt) no se testea por convención de este proyecto.
 */
internal fun classifyTilesFailure(failedTier: TilesTier?): TilesFailureAction = when (failedTier) {
    TilesTier.LOCAL -> TilesFailureAction.DELETE_LOCAL
    TilesTier.REMOTE -> TilesFailureAction.DEGRADE_REMOTE
    TilesTier.NONE, null -> TilesFailureAction.IGNORE
}

/** Traduce el tag `String` que viaja por MapLibreMapView (no conoce [TilesTier], vive en
 * core/maps) de vuelta al enum — null si no matchea ningún tier conocido. */
internal fun parseTilesTier(tag: String?): TilesTier? =
    tag?.let { candidate -> TilesTier.entries.find { it.name == candidate } }
