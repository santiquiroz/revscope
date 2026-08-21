package com.revscope.feature.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapFeatureDescriptionTest {

    // Mediodía UTC del 15/01/2026 en epoch ms — evita cruces de día por huso horario en
    // cualquier zona horaria razonable (el formateador usa la zona del sistema que corre el test).
    private val noonUtcMs = 1768478400000L

    @Test
    fun `radar con limite conocido muestra el limite`() {
        val result = describeFeature(ICON_CAMERA, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 60))
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite 60 km/h"), result)
    }

    @Test
    fun `radar sin limite registrado lo indica`() {
        val result = describeFeature(ICON_CAMERA, emptyMap())
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite no registrado"), result)
    }

    @Test
    fun `radar objetivo describe igual que el radar normal`() {
        val result = describeFeature(ICON_CAMERA_TARGET, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 40))
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite 40 km/h"), result)
    }

    @Test
    fun `hueco con un solo golpe no pluraliza`() {
        val result = describeFeature(
            ICON_POTHOLE,
            mapOf(FEATURE_PROP_HITS to 1, FEATURE_PROP_LAST_HIT_MS to noonUtcMs),
        )
        assertEquals("Hueco reportado", result?.title)
        assertEquals(true, result?.subtitle?.startsWith("Reportado el "))
    }

    @Test
    fun `hueco con varios golpes muestra el conteo`() {
        val result = describeFeature(
            ICON_POTHOLE,
            mapOf(FEATURE_PROP_HITS to 4, FEATURE_PROP_LAST_HIT_MS to noonUtcMs),
        )
        assertEquals("Hueco reportado", result?.title)
        assertEquals(true, result?.subtitle?.startsWith("Reportado 4× · última vez "))
    }

    @Test
    fun `destino con nombre lo usa como titulo`() {
        val result = describeFeature(ICON_DESTINATION, mapOf(FEATURE_PROP_NAME to "Casa de Juan"))
        assertEquals(MapFeatureDescription("Casa de Juan", null), result)
    }

    @Test
    fun `destino sin nombre cae a Destino`() {
        val result = describeFeature(ICON_DESTINATION, emptyMap())
        assertEquals(MapFeatureDescription("Destino", null), result)
    }

    @Test
    fun `peer con velocidad la muestra junto al nombre`() {
        val result = describeFeature(
            ICON_PEER,
            mapOf(FEATURE_PROP_NAME to "Nico", FEATURE_PROP_SPEED_KMH to 82.0),
        )
        assertEquals(MapFeatureDescription("Nico", "82 km/h · en sala"), result)
    }

    @Test
    fun `peer con rumbo sin velocidad conocida solo dice en sala`() {
        val result = describeFeature(ICON_PEER_RUMBO, mapOf(FEATURE_PROP_NAME to "Cami"))
        assertEquals(MapFeatureDescription("Cami", "En sala"), result)
    }

    @Test
    fun `tipo desconocido no genera tarjeta`() {
        assertNull(describeFeature("icono-inexistente", emptyMap()))
    }

    @Test
    fun `el propio puck no genera tarjeta`() {
        assertNull(describeFeature(ICON_ME_MOTO, emptyMap()))
    }
}
