package com.revscope.feature.settings

import com.revscope.core.uitesting.XmlStringResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val resolverEs = XmlStringResolver.es(R::class.java)

    private fun sampleEntries(): List<SettingsEntryMeta> = listOf(
        SettingsEntryMeta(
            id = "radares_velocidad",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = resolverEs.get(R.string.settings_entry_speed_cameras_title),
            subtitle = resolverEs.get(R.string.settings_entry_speed_cameras_subtitle),
            keywords = resolverEs.get(R.string.settings_search_keywords_speed_cameras).split('|'),
        ),
        SettingsEntryMeta(
            id = "servidor_mcp",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = resolverEs.get(R.string.settings_entry_mcp_server_title),
            subtitle = "",
            keywords = resolverEs.get(R.string.settings_search_keywords_mcp_server).split('|'),
        ),
        SettingsEntryMeta(
            id = "combustible",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = resolverEs.get(R.string.settings_entry_fuel_title),
            subtitle = resolverEs.get(R.string.settings_entry_fuel_subtitle),
            keywords = resolverEs.get(R.string.settings_search_keywords_fuel).split('|'),
        ),
    )

    @Test
    fun `query vacio no filtra`() {
        val entries = sampleEntries()
        assertEquals(entries, SettingsSearch.filter("", entries))
    }

    @Test
    fun `query de solo espacios no filtra`() {
        val entries = sampleEntries()
        assertEquals(entries, SettingsSearch.filter("   ", entries))
    }

    @Test
    fun `query de un caracter no filtra`() {
        val entries = sampleEntries()
        assertEquals(entries, SettingsSearch.filter("a", entries))
    }

    @Test
    fun `query de una letra acentuada no filtra`() {
        val entries = sampleEntries()
        assertEquals(entries, SettingsSearch.filter("á", entries))
    }

    @Test
    fun `query de dos caracteres si filtra`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("mc", entries)
        assertEquals(listOf("servidor_mcp"), result.map { it.id })
    }

    @Test
    fun `isActive es falso para queries menores a 2 caracteres`() {
        assertFalse(SettingsSearch.isActive(""))
        assertFalse(SettingsSearch.isActive(" "))
        assertFalse(SettingsSearch.isActive("a"))
    }

    @Test
    fun `isActive es verdadero para queries de 2 o mas caracteres`() {
        assertTrue(SettingsSearch.isActive("ab"))
        assertTrue(SettingsSearch.isActive(" ab "))
    }

    @Test
    fun `matchea por titulo sin importar mayusculas`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("RADAR", entries)
        assertEquals(listOf("radares_velocidad"), result.map { it.id })
    }

    @Test
    fun `matchea por titulo ignorando acentos en la query`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("diesel", entries)
        assertEquals(listOf("combustible"), result.map { it.id })
    }

    @Test
    fun `matchea keyword con tilde usando query sin tilde`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("camara", entries)
        assertEquals(listOf("radares_velocidad"), result.map { it.id })
    }

    @Test
    fun `matchea keyword sin tilde usando query con tilde`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("cámara", entries)
        assertEquals(listOf("radares_velocidad"), result.map { it.id })
    }

    @Test
    fun `matchea solo por keyword aunque titulo y subtitulo no contengan la query`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("fotomulta", entries)
        assertEquals(listOf("radares_velocidad"), result.map { it.id })
    }

    @Test
    fun `matchea solo por subtitulo aunque titulo y keywords no contengan la query`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("gasolina", entries)
        assertEquals(listOf("combustible"), result.map { it.id })
    }

    @Test
    fun `english crash search finds crash detection`() {
        val resolverEn = XmlStringResolver.en(R::class.java)
        val entry = SettingsEntryMeta(
            id = "deteccion_caida",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = resolverEn.get(R.string.settings_entry_crash_detection_title),
            subtitle = resolverEn.get(R.string.settings_entry_crash_detection_subtitle),
            keywords = resolverEn.get(R.string.settings_search_keywords_crash_detection).split('|'),
        )

        assertEquals(listOf(entry), SettingsSearch.filter("crash", listOf(entry)))
        assertEquals("Crash detection", entry.title)
    }

    @Test
    fun `entrada con subtitulo en blanco no matchea por subtitulo vacio`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("xyz zz", entries)
        assertTrue(result.none { it.id == "servidor_mcp" })
    }

    @Test
    fun `sin coincidencias devuelve lista vacia`() {
        val entries = sampleEntries()
        val result = SettingsSearch.filter("noexisteestapalabra", entries)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `deep link key null no resuelve seccion`() {
        assertEquals(null, settingsSectionFromDeepLinkKey(null))
    }

    @Test
    fun `deep link key en blanco no resuelve seccion`() {
        assertEquals(null, settingsSectionFromDeepLinkKey("   "))
    }

    @Test
    fun `deep link key sin match devuelve null`() {
        assertEquals(null, settingsSectionFromDeepLinkKey("no_existe"))
    }

    @Test
    fun `deep link key en minusculas resuelve la seccion`() {
        assertEquals(SettingsSectionId.MAPA, settingsSectionFromDeepLinkKey("mapa"))
    }

    @Test
    fun `deep link key ignora mayusculas y minusculas`() {
        assertEquals(SettingsSectionId.MAPA, settingsSectionFromDeepLinkKey("MaPa"))
    }

    @Test
    fun `deep link key resuelve otra seccion por nombre exacto del enum`() {
        assertEquals(
            SettingsSectionId.AVANZADO_DIAGNOSTICO,
            settingsSectionFromDeepLinkKey("AVANZADO_DIAGNOSTICO"),
        )
    }
}
