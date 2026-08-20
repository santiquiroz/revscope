package com.revscope.core.maps

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

private const val TILES_URL = "pmtiles://https://x/y.pmtiles"

private val FIXTURE_LAYERS_WITH_BACKGROUND = """
    [
      { "id": "background", "type": "background", "paint": { "background-color": "#cccccc" } },
      { "id": "roads", "type": "line", "source": "protomaps", "source-layer": "roads", "paint": { "line-color": "#888888" } }
    ]
""".trimIndent()

private val FIXTURE_LAYERS_SIN_BACKGROUND = """
    [
      { "id": "roads", "type": "line", "source": "protomaps", "source-layer": "roads" }
    ]
""".trimIndent()

class MapStyleProviderTest {

    @Test
    fun `el archivo local gana sobre el servidor`() {
        val local = File.createTempFile("colombia", ".pmtiles").apply { deleteOnExit() }
        val url = MapStyleProvider.tilesUrl(local, "https://servidor.example")
        assertEquals("pmtiles://file://${local.absolutePath}", url)
    }

    @Test
    fun `sin archivo local usa el servidor`() {
        val url = MapStyleProvider.tilesUrl(null, "https://servidor.example")
        assertEquals("pmtiles://https://servidor.example/colombia.pmtiles", url)
    }

    @Test
    fun `un archivo local inexistente no gana`() {
        val url = MapStyleProvider.tilesUrl(File("/no/existe.pmtiles"), "https://servidor.example")
        assertEquals("pmtiles://https://servidor.example/colombia.pmtiles", url)
    }

    @Test
    fun `la barra final del servidor no duplica la del path`() {
        val url = MapStyleProvider.tilesUrl(null, "https://servidor.example/")
        assertEquals("pmtiles://https://servidor.example/colombia.pmtiles", url)
    }

    @Test
    fun `sin archivo ni servidor no hay origen`() {
        assertNull(MapStyleProvider.tilesUrl(null, null))
    }

    @Test
    fun `un servidor en blanco tampoco es un origen`() {
        assertNull(MapStyleProvider.tilesUrl(null, "   "))
    }

    @Test
    fun `sin origen vectorial cae al raster de OSM para no perder las calles`() {
        val json = MapStyleProvider.styleJson(null, dark = false)
        assertTrue(json.contains("\"type\": \"raster\""))
        assertTrue(json.contains("tile.openstreetmap.org"))
        assertTrue(json.contains("OpenStreetMap contributors"))
    }

    @Test
    fun `sin origen y sin raster el estilo queda solo con fondo pero valido`() {
        val json = MapStyleProvider.styleJson(null, dark = false, rasterFallback = false)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("background"))
        assertTrue(!json.contains("raster"))
    }

    @Test
    fun `el vectorial gana sobre el raster cuando hay origen`() {
        val json = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = false)
        assertTrue(json.contains("\"type\": \"vector\""))
        assertTrue(!json.contains("raster"))
    }

    @Test
    fun `el raster oscuro baja brillo y saturacion`() {
        val json = MapStyleProvider.styleJson(null, dark = true)
        assertTrue(json.contains("raster-brightness-max"))
        assertTrue(json.contains("raster-saturation"))
    }

    @Test
    fun `con origen el estilo declara la fuente vectorial`() {
        val json = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = false)
        assertTrue(json.contains("pmtiles://https://x/y.pmtiles"))
        assertTrue(json.contains("\"type\": \"vector\""))
    }

    @Test
    fun `el estilo oscuro difiere del claro`() {
        val claro = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = false)
        val oscuro = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = true)
        assertNotEquals(claro, oscuro)
    }

    @Test
    fun `el estilo vectorial declara los glyphs para los labels de peers`() {
        val json = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = false)
        assertTrue(json.contains("\"glyphs\": \"https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf\""))
    }

    @Test
    fun `el estilo raster tambien declara los glyphs`() {
        val json = MapStyleProvider.styleJson(null, dark = false)
        assertTrue(json.contains("\"glyphs\""))
    }

    @Test
    fun `el estilo solo-fondo tambien declara los glyphs`() {
        val json = MapStyleProvider.styleJson(null, dark = false, rasterFallback = false)
        assertTrue(json.contains("\"glyphs\""))
    }

    @Test
    fun `layersJson nulo mantiene el estilo vectorial actual sin cambios`() {
        val json = MapStyleProvider.styleJson(TILES_URL, dark = false)
        val expected = """
            {
              "version": 8,
              "glyphs": "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf",
              "sources": { "protomaps": { "type": "vector", "url": "$TILES_URL" } },
              "layers": [
                { "id": "fondo", "type": "background",
                  "paint": { "background-color": "#f8f4f0" } }
              ]
            }
        """.trimIndent()
        assertEquals(expected, json)
    }

    @Test
    fun `el estilo completo trae las capas del layersJson tal cual y en orden`() {
        val json = MapStyleProvider.styleJson(TILES_URL, dark = false, layersJson = FIXTURE_LAYERS_WITH_BACKGROUND)

        val layers = JSONObject(json).getJSONArray("layers")

        assertEquals(2, layers.length())
        assertEquals("background", layers.getJSONObject(0).getString("id"))
        assertEquals("roads", layers.getJSONObject(1).getString("id"))
    }

    @Test
    fun `el estilo completo no duplica el background cuando el layersJson ya trae uno`() {
        val json = MapStyleProvider.styleJson(TILES_URL, dark = false, layersJson = FIXTURE_LAYERS_WITH_BACKGROUND)

        val layers = JSONObject(json).getJSONArray("layers")
        val backgroundLayers = (0 until layers.length())
            .count { layers.getJSONObject(it).getString("type") == "background" }

        assertEquals(1, backgroundLayers)
    }

    @Test
    fun `el estilo completo antepone fondo propio si el layersJson no trae background`() {
        val json = MapStyleProvider.styleJson(TILES_URL, dark = false, layersJson = FIXTURE_LAYERS_SIN_BACKGROUND)

        val layers = JSONObject(json).getJSONArray("layers")

        assertEquals(2, layers.length())
        assertEquals("fondo", layers.getJSONObject(0).getString("id"))
        assertEquals("background", layers.getJSONObject(0).getString("type"))
        assertEquals("roads", layers.getJSONObject(1).getString("id"))
    }

    @Test
    fun `el estilo completo declara el sprite claro u oscuro segun el tema`() {
        val claro = MapStyleProvider.styleJson(TILES_URL, dark = false, layersJson = FIXTURE_LAYERS_WITH_BACKGROUND)
        val oscuro = MapStyleProvider.styleJson(TILES_URL, dark = true, layersJson = FIXTURE_LAYERS_WITH_BACKGROUND)

        assertEquals(
            "https://protomaps.github.io/basemaps-assets/sprites/v4/light",
            JSONObject(claro).getString("sprite"),
        )
        assertEquals(
            "https://protomaps.github.io/basemaps-assets/sprites/v4/dark",
            JSONObject(oscuro).getString("sprite"),
        )
    }

    @Test
    fun `el estilo completo declara la fuente protomaps y parsea como JSON valido`() {
        val json = MapStyleProvider.styleJson(TILES_URL, dark = false, layersJson = FIXTURE_LAYERS_WITH_BACKGROUND)

        val root = JSONObject(json)
        val source = root.getJSONObject("sources").getJSONObject("protomaps")

        assertEquals(8, root.getInt("version"))
        assertEquals("vector", source.getString("type"))
        assertEquals(TILES_URL, source.getString("url"))
    }
}
