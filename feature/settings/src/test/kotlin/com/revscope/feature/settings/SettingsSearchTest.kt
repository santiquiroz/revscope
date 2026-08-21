package com.revscope.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun sampleEntries(): List<SettingsEntryMeta> = listOf(
        SettingsEntryMeta(
            id = "radares_velocidad",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = "Radares de velocidad",
            subtitle = "Descarga y radio de aviso de radares",
            keywords = listOf("radar", "camara", "cámara", "fotomulta"),
        ),
        SettingsEntryMeta(
            id = "servidor_mcp",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Servidor MCP (red local)",
            subtitle = "",
            keywords = listOf("mcp", "claude desktop", "token"),
        ),
        SettingsEntryMeta(
            id = "combustible",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = "Combustible",
            subtitle = "Precios de gasolina, extra y diésel",
            keywords = emptyList(),
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
}
