package com.revscope.core.maps

import java.io.File

/**
 * Resuelve de dónde salen los tiles, en cascada:
 * 1. archivo `.pmtiles` local (Fase 4 lo descarga; acá ya se respeta si existe)
 * 2. `revscope-server`
 * 3. nada: la app sigue dibujando sus capas sobre fondo vacío
 *
 * El servidor NO es un requisito: sin él la pantalla debe seguir funcionando.
 */
object MapStyleProvider {

    const val PMTILES_FILE_NAME = "colombia.pmtiles"

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
     * fondo + la fuente vectorial, y deja el JSON bajo control para el modo oscuro.
     * El estilo cartográfico completo entra cuando se genere el extracto.
     */
    fun styleJson(tilesUrl: String?, dark: Boolean): String {
        val background = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
        val sources = if (tilesUrl == null) "" else
            """"protomaps": { "type": "vector", "url": "$tilesUrl" }"""
        return """
            {
              "version": 8,
              "sources": { $sources },
              "layers": [
                { "id": "fondo", "type": "background",
                  "paint": { "background-color": "$background" } }
              ]
            }
        """.trimIndent()
    }
}
