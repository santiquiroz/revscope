package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class MapLayersAssetsTest {

    @Test
    fun `english dark map selects english asset`() {
        assertEquals("map-layers-dark-en.json", mapLayersAssetName(dark = true, language = "en"))
    }

    @Test
    fun `spanish light map selects spanish asset`() {
        assertEquals("map-layers-light.json", mapLayersAssetName(dark = false, language = "es"))
    }

    @Test
    fun `unsupported language falls back to spanish asset`() {
        assertEquals("map-layers-light.json", mapLayersAssetName(dark = false, language = "fr"))
    }
}
