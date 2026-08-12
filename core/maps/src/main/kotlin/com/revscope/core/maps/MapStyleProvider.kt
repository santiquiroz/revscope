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

    /** Mismo servidor de tiles que usa osmdroid hoy vía TileSourceFactory.MAPNIK. */
    private const val OSM_RASTER_TILES = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
    private const val OSM_ATTRIBUTION = "© OpenStreetMap contributors"

    private const val BACKGROUND_LIGHT = "#f8f4f0"
    private const val BACKGROUND_DARK = "#1c1c28"

    fun tilesUrl(localFile: File?, serverBaseUrl: String?): String? {
        if (localFile != null && localFile.isFile) {
            // MapLibre exige la URL interna completamente especificada.
            return "pmtiles://file://${localFile.absolutePath}"
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
     */
    fun styleJson(tilesUrl: String?, dark: Boolean, rasterFallback: Boolean = true): String {
        val background = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
        return when {
            tilesUrl != null -> vectorStyle(tilesUrl, background)
            rasterFallback -> rasterStyle(background, dark)
            else -> backgroundOnlyStyle(background)
        }
    }

    private fun vectorStyle(tilesUrl: String, background: String): String = """
        {
          "version": 8,
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
          "sources": { },
          "layers": [
            { "id": "fondo", "type": "background",
              "paint": { "background-color": "$background" } }
          ]
        }
    """.trimIndent()
}
