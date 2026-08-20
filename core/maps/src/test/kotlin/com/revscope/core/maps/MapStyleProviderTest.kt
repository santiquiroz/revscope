package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
}
