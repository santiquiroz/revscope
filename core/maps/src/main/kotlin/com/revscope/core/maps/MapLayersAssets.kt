package com.revscope.core.maps

import android.content.Context

private const val LAYERS_ASSET_LIGHT = "map-layers-light.json"
private const val LAYERS_ASSET_DARK = "map-layers-dark.json"

/**
 * Lee el array JSON de capas cartográficas de protomaps (T1: `scripts/gen-map-layers.mjs`,
 * assets `map-layers-{light,dark}.json`) para el tema pedido. Vive fuera de [MapStyleProvider]
 * a propósito: ese object sigue puro/sin Context, y [styleJson] solo recibe el String ya leído.
 * El caller decide cómo cachear el resultado (remember/lazy por tema en Compose).
 *
 * null si el asset falta o no se puede leer — el caller cae al estilo vectorial placeholder
 * (fondo + fuente, sin capas) en vez de crashear.
 */
fun readMapLayersAsset(context: Context, dark: Boolean): String? {
    val name = if (dark) LAYERS_ASSET_DARK else LAYERS_ASSET_LIGHT
    return runCatching {
        context.assets.open(name).bufferedReader().use { it.readText() }
    }.getOrNull()
}
