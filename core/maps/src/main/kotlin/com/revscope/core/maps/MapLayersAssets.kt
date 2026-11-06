package com.revscope.core.maps

import android.content.Context
import com.revscope.core.common.i18n.AppLanguage

// Cache de proceso por tema e idioma: los assets son inmutables durante la vida de la app,
// así que una vez leídos no hay motivo para volver a tocar disco — LiveMapScreen los relee cada
// vez que cambia darkTiles (modo nocturno auto/manual) vía produceState en IO. El lock solo
// protege la escritura del resultado; la lectura del asset en sí corre fuera de él.
private val cacheLock = Any()
private val cache = HashMap<Pair<Boolean, String>, String?>()

internal fun mapLayersAssetName(dark: Boolean, language: String): String {
    val theme = if (dark) "dark" else "light"
    return if (language == "en") "map-layers-$theme-en.json" else "map-layers-$theme.json"
}

/**
 * Lee el array JSON de capas cartográficas de protomaps (T1: `scripts/gen-map-layers.mjs`,
 * assets `map-layers-{light,dark}.json`) para el tema pedido. Vive fuera de [MapStyleProvider]
 * a propósito: ese object sigue puro/sin Context, y [MapStyleProvider.styleJson] solo recibe el
 * String ya leído. Pensada para llamarse desde un dispatcher de IO (~240 KB por tema): el
 * caller decide eso (ver `produceState` en LiveMapScreen/RealTrackMap).
 *
 * null si el asset falta o no se puede leer — el caller cae al estilo vectorial placeholder
 * (fondo + fuente, sin capas) en vez de crashear.
 */
fun readMapLayersAsset(context: Context, dark: Boolean): String? {
    val language = AppLanguage.current(context).language
    val key = dark to language
    synchronized(cacheLock) {
        if (cache.containsKey(key)) return cache[key]
    }
    val name = mapLayersAssetName(dark, language)
    val result = readAsset(context, name)
        ?: if (language == "en") readAsset(context, mapLayersAssetName(dark, "es")) else null
    synchronized(cacheLock) { cache[key] = result }
    return result
}

/**
 * Mira el cache en memoria SIN tocar IO — para sembrar `produceState` sincrónicamente cuando el
 * tema pedido ya se leyó antes en este proceso. Sin esto, cada toggle día/noche resetea
 * `layersJson` a null (cache miss "aparente" desde el punto de vista del composable), lo que
 * arma un estilo placeholder + un segundo `setStyle`/reload apenas resuelve el IO — flicker
 * visible aunque el dato ya estuviera en memoria.
 *
 * null tanto si el tema nunca se leyó (cache miss real) como si la única lectura previa falló
 * (falla cacheada) — en ambos casos el caller debe resolverlo async con [readMapLayersAsset];
 * ahí un cache-miss real toca disco y una falla cacheada retorna null de inmediato sin IO.
 */
fun peekMapLayersAsset(dark: Boolean, language: String): String? =
    synchronized(cacheLock) { cache[dark to language] }

private fun readAsset(context: Context, name: String): String? = runCatching {
    context.assets.open(name).bufferedReader().use { it.readText() }
}.getOrNull()
