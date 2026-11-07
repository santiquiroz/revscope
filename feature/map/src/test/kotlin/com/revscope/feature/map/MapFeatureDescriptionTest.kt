package com.revscope.feature.map

import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.common.units.UnitSystem
import com.revscope.core.uitesting.XmlStringResolver
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MapFeatureDescriptionTest {

    // Mediodía UTC del 15/01/2026 en epoch ms — evita cruces de día por huso horario en
    // cualquier zona horaria razonable (el formateador usa la zona del sistema que corre el test).
    private val noonUtcMs = 1768478400000L

    private val es = XmlStringResolver.es(R::class.java)

    private val en = XmlStringResolver.en(R::class.java)

    private val metric = UnitFormatter(UnitSystem.METRIC, Locale("es"))

    private fun describe(kind: String?, properties: Map<String, Any?>) = describeFeature(kind, properties, es, metric)

    @Test
    fun `radar con limite conocido muestra el limite`() {
        val result = describe(ICON_CAMERA, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 60))
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite 60 km/h"), result)
    }

    @Test
    fun `radar sin limite registrado lo indica`() {
        val result = describe(ICON_CAMERA, emptyMap())
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite no registrado"), result)
    }

    @Test
    fun `radar objetivo describe igual que el radar normal`() {
        val result = describe(ICON_CAMERA_TARGET, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 40))
        assertEquals(MapFeatureDescription("Radar fijo", "Fotomulta · límite 40 km/h"), result)
    }

    @Test
    fun `hueco con un solo golpe no pluraliza`() {
        val result = describe(
            ICON_POTHOLE,
            mapOf(FEATURE_PROP_HITS to 1, FEATURE_PROP_LAST_HIT_MS to noonUtcMs),
        )
        assertEquals("Hueco reportado", result?.title)
        assertEquals(true, result?.subtitle?.startsWith("Reportado el "))
    }

    @Test
    fun `hueco con varios golpes muestra el conteo`() {
        val result = describe(
            ICON_POTHOLE,
            mapOf(FEATURE_PROP_HITS to 4, FEATURE_PROP_LAST_HIT_MS to noonUtcMs),
        )
        assertEquals("Hueco reportado", result?.title)
        assertEquals(true, result?.subtitle?.startsWith("Reportado 4× · última vez "))
    }

    @Test
    fun `destino con nombre lo usa como titulo`() {
        val result = describe(ICON_DESTINATION, mapOf(FEATURE_PROP_NAME to "Casa de Juan"))
        assertEquals(MapFeatureDescription("Casa de Juan", null), result)
    }

    @Test
    fun `destino sin nombre cae a Destino`() {
        val result = describe(ICON_DESTINATION, emptyMap())
        assertEquals(MapFeatureDescription("Destino", null), result)
    }

    @Test
    fun `peer con velocidad la muestra junto al nombre`() {
        val result = describe(
            ICON_PEER,
            mapOf(FEATURE_PROP_NAME to "Nico", FEATURE_PROP_SPEED_KMH to 82.0),
        )
        assertEquals(MapFeatureDescription("Nico", "82 km/h · en sala"), result)
    }

    @Test
    fun `peer con rumbo sin velocidad conocida solo dice en sala`() {
        val result = describe(ICON_PEER_RUMBO, mapOf(FEATURE_PROP_NAME to "Cami"))
        assertEquals(MapFeatureDescription("Cami", "En sala"), result)
    }

    @Test
    fun `tipo desconocido no genera tarjeta`() {
        assertNull(describe("icono-inexistente", emptyMap()))
    }

    @Test
    fun `el propio puck no genera tarjeta`() {
        assertNull(describe(ICON_ME_MOTO, emptyMap()))
    }

    @Test
    fun `en ingles el hueco con varios golpes muestra el conteo`() {
        val result = describeFeature(
            ICON_POTHOLE,
            mapOf(FEATURE_PROP_HITS to 4, FEATURE_PROP_LAST_HIT_MS to noonUtcMs),
            en,
            metric,
        )
        assertEquals("Reported pothole", result?.title)
        assertEquals(true, result?.subtitle?.startsWith("Reported 4× · last on "))
    }

    @Test
    fun `en ingles el radar con limite conocido muestra el limite`() {
        val result = describeFeature(ICON_CAMERA, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 60), en, metric)
        assertEquals(MapFeatureDescription("Fixed speed camera", "Photo enforcement · limit 60 km/h"), result)
    }

    @Test
    fun `en US el limite del radar va en mph`() {
        val us = UnitFormatter(UnitSystem.US, Locale.US)
        val result = describeFeature(ICON_CAMERA, mapOf(FEATURE_PROP_SPEED_LIMIT_KMH to 80), en, us)
        assertEquals(MapFeatureDescription("Fixed speed camera", "Photo enforcement · limit 50 mph"), result)
    }

    @Test
    fun `en US la velocidad del peer va en mph`() {
        val us = UnitFormatter(UnitSystem.US, Locale.US)
        val result = describeFeature(ICON_PEER, mapOf(FEATURE_PROP_NAME to "Nico", FEATURE_PROP_SPEED_KMH to 100.0), en, us)
        assertEquals(MapFeatureDescription("Nico", "62 mph · in room"), result)
    }
}
