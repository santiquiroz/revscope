package com.revscope.core.maps

import java.io.File

/**
 * Resuelve de dónde salen los tiles, en cascada:
 * 1. archivo `.pmtiles` local — el único tier que funciona sin red (Fase 4 lo descarga)
 * 2. `revscope-server`
 * 3. ráster de OpenStreetMap — paridad con lo que hace osmdroid hoy
 * 4. nada: la app sigue dibujando sus capas sobre fondo vacío
 *
 * El servidor NO es un requisito: sin él la pantalla debe seguir funcionando.
 *
 * El tier ráster existe porque el extracto vectorial de Colombia todavía no está generado
 * ni hospedado. Sin él, migrar el motor dejaría las pantallas sin calles, que es peor que
 * lo que había. Cuando el `.pmtiles` exista, el ráster queda como último recurso.
 */
object MapStyleProvider {

    const val PMTILES_FILE_NAME = "colombia.pmtiles"

    /** Subdirectorio de `filesDir` donde vive el `.pmtiles` — único literal, compartido por
     * MapsModule (mapsDir de MapDownloadService) y [localMapFile]. */
    const val MAPS_DIR_NAME = "maps"

    /** Path canónico del `.pmtiles` local dentro de `filesDir` — antes triplicado a mano
     * (MapsModule, LiveMapScreen, RealTrackMap); ahora un solo sitio. Sigue sin recibir
     * Context: [filesDir] es lo que el caller ya tiene vía `context.filesDir`. */
    fun localMapFile(filesDir: File): File = File(filesDir, "$MAPS_DIR_NAME/$PMTILES_FILE_NAME")

    /** Mismo servidor de tiles que usa osmdroid hoy vía TileSourceFactory.MAPNIK. */
    private const val OSM_RASTER_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    private const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"

    private const val BACKGROUND_LIGHT = "#f8f4f0"
    private const val BACKGROUND_DARK = "#1c1c28"

    // Fuente remota de glifos para textField (labels de peer-labels): sin red no cargan y las
    // etiquetas simplemente no se dibujan — el pin sigue mostrándose, degradación aceptada.
    private const val GLYPHS_URL = "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf"

    private const val SPRITE_URL_LIGHT = "https://protomaps.github.io/basemaps-assets/sprites/v4/light"
    private const val SPRITE_URL_DARK = "https://protomaps.github.io/basemaps-assets/sprites/v4/dark"

    /** Asset del release `tiles-v1` en GitHub — mismo extracto que [MapDownloadService] descarga
     * a disco, pero MapLibre puede streamearlo directo por HTTP range requests (el asset expone
     * `Accept-Ranges: bytes`, verificado al subirlo): estilo vectorial premium con red, sin que
     * el usuario descargue nada primero. Fuente única del literal — MapDownloadService.PMTILES_URL
     * lo referencia en vez de duplicarlo. */
    const val REMOTE_PMTILES_URL =
        "https://github.com/santiquiroz/revscope/releases/download/tiles-v1/colombia.pmtiles"

    // Detecta si el array de capas ya trae su propia capa de background (siempre la trae en los
    // assets reales generados por @protomaps/basemaps), sin parsear/reserializar el JSON — eso
    // preservaría el orden y formato original de las capas tal cual llegan.
    private val BACKGROUND_TYPE_REGEX = Regex("\"type\"\\s*:\\s*\"background\"")

    /**
     * Cascada: [localFile] (si existe) > [remoteUrl] (fix W1: tier remoto por internet, p. ej.
     * [REMOTE_PMTILES_URL]) > [serverBaseUrl] (revscope-server; sigue sin wirear en los call
     * sites reales, que le pasan null) > null (sin origen, cae a ráster/fondo en [styleJson]).
     * [remoteUrl] es la URL COMPLETA al `.pmtiles` (no un base como [serverBaseUrl]).
     */
    fun tilesUrl(localFile: File?, serverBaseUrl: String?, remoteUrl: String? = null): String? {
        if (localFile != null && localFile.isFile) {
            // MapLibre exige la URL interna completamente especificada.
            return "pmtiles://file://${localFile.absolutePath}"
        }
        val remote = remoteUrl?.trim()
        if (!remote.isNullOrEmpty()) {
            return "pmtiles://$remote"
        }
        val base = serverBaseUrl?.trim()?.trimEnd('/')
        if (base.isNullOrEmpty()) return null
        return "pmtiles://$base/$PMTILES_FILE_NAME"
    }

    /**
     * Estilo mínimo propio en vez del de Protomaps completo: para la Fase 1 alcanza con
     * fondo + la fuente de tiles, y deja el JSON bajo control para el modo oscuro.
     * El estilo cartográfico completo entra cuando se genere el extracto vectorial.
     *
     * @param tilesUrl URL `pmtiles://` de [tilesUrl], o null para caer al tier ráster.
     * @param rasterFallback si false y no hay [tilesUrl], el estilo queda solo con fondo.
     * @param layersJson array JSON crudo de capas de protomaps (ya leído del asset por el
     *   caller — este object sigue sin Context). Con [tilesUrl] presente, activa el estilo
     *   vectorial completo; null mantiene el placeholder de solo-fondo de siempre.
     */
    fun styleJson(
        tilesUrl: String?,
        dark: Boolean,
        rasterFallback: Boolean = true,
        layersJson: String? = null,
    ): String {
        val background = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
        return when {
            tilesUrl != null && layersJson != null -> fullVectorStyle(tilesUrl, dark, layersJson)
            tilesUrl != null -> vectorStyle(tilesUrl, background)
            rasterFallback -> rasterStyle(background, dark)
            else -> backgroundOnlyStyle(background)
        }
    }

    /**
     * Estilo vectorial completo con el catálogo de capas de protomaps (generadas en build-time
     * por `scripts/gen-map-layers.mjs`, ver assets `map-layers-{light,dark}.json`).
     */
    private fun fullVectorStyle(tilesUrl: String, dark: Boolean, layersJson: String): String {
        val background = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
        val sprite = if (dark) SPRITE_URL_DARK else SPRITE_URL_LIGHT
        val layers = layersWithBackground(layersJson, background)
        return """
            {
              "version": 8,
              "glyphs": "$GLYPHS_URL",
              "sprite": "$sprite",
              "sources": { "protomaps": { "type": "vector", "url": "$tilesUrl" } },
              "layers": $layers
            }
        """.trimIndent()
    }

    /**
     * Devuelve [layersJson] tal cual si ya trae su propia capa de background (caso real, T1
     * siempre la genera primero); si no la trae, antepone "fondo" como hacía el estilo viejo,
     * sin tocar el resto del array.
     */
    private fun layersWithBackground(layersJson: String, background: String): String {
        val layers = layersJson.trim()
        if (BACKGROUND_TYPE_REGEX.containsMatchIn(layers)) return layers
        return prependFondoLayer(layers, background)
    }

    private fun prependFondoLayer(layersArray: String, background: String): String {
        val fondo = """{ "id": "fondo", "type": "background", "paint": { "background-color": "$background" } }"""
        val body = layersArray.removePrefix("[").removeSuffix("]").trim()
        return if (body.isEmpty()) "[$fondo]" else "[$fondo,$body]"
    }

    private fun vectorStyle(tilesUrl: String, background: String): String = """
        {
          "version": 8,
          "glyphs": "$GLYPHS_URL",
          "sources": { "protomaps": { "type": "vector", "url": "$tilesUrl" } },
          "layers": [
            { "id": "fondo", "type": "background",
              "paint": { "background-color": "$background" } }
          ]
        }
    """.trimIndent()

    /**
     * MapLibre no tiene equivalente del INVERT_COLORS de osmdroid. Con tiles ráster el modo
     * oscuro solo puede aproximarse bajando brillo y saturación; el estilo oscuro de verdad
     * llega con el vectorial.
     */
    private fun rasterStyle(background: String, dark: Boolean): String {
        val paint = if (dark) {
            """, "raster-brightness-max": 0.45, "raster-saturation": -0.6, "raster-contrast": 0.15"""
        } else {
            ""
        }
        return """
            {
              "version": 8,
              "glyphs": "$GLYPHS_URL",
              "sources": {
                "osm": {
                  "type": "raster",
                  "tiles": ["$OSM_RASTER_TILES"],
                  "tileSize": 256,
                  "maxzoom": 19,
                  "attribution": "$OSM_ATTRIBUTION"
                }
              },
              "layers": [
                { "id": "fondo", "type": "background",
                  "paint": { "background-color": "$background" } },
                { "id": "osm", "type": "raster", "source": "osm",
                  "paint": { "raster-opacity": 1$paint } }
              ]
            }
        """.trimIndent()
    }

    private fun backgroundOnlyStyle(background: String): String = """
        {
          "version": 8,
          "glyphs": "$GLYPHS_URL",
          "sources": { },
          "layers": [
            { "id": "fondo", "type": "background",
              "paint": { "background-color": "$background" } }
          ]
        }
    """.trimIndent()
}
