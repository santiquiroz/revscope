package com.revscope.feature.settings

import java.text.Normalizer

/** Agrupación temática de las cards de Ajustes — cada una es una sección colapsable en la UI. */
internal enum class SettingsSectionId(val title: String) {
    VEHICULO_GARAGE("Vehículo y garage"),
    RADARES_ALERTAS("Radares y alertas"),
    MAPA("Mapa"),
    INTELIGENCIA_ARTIFICIAL("Inteligencia artificial"),
    SALA_SOCIAL("Sala y social"),
    NOTIFICACIONES_SEGUNDO_PLANO("Notificaciones y segundo plano"),
    AVANZADO_DIAGNOSTICO("Avanzado y diagnóstico"),
}

/**
 * Metadatos buscables de una card de Ajustes, separados de su contenido @Composable
 * para que el filtro de búsqueda sea una función pura, sin dependencia del runtime de Compose.
 */
internal data class SettingsEntryMeta(
    val id: String,
    val section: SettingsSectionId,
    val title: String,
    val subtitle: String = "",
    val keywords: List<String> = emptyList(),
)

internal object SettingsSearch {
    const val MIN_QUERY_LENGTH = 2

    private val diacriticsRegex = Regex("\\p{Mn}+")

    fun isActive(query: String): Boolean = normalize(query).length >= MIN_QUERY_LENGTH

    fun filter(query: String, entries: List<SettingsEntryMeta>): List<SettingsEntryMeta> {
        if (!isActive(query)) return entries
        val normalizedQuery = normalize(query)
        return entries.filter { it.matchesQuery(normalizedQuery) }
    }

    private fun SettingsEntryMeta.matchesQuery(normalizedQuery: String): Boolean {
        if (normalize(title).contains(normalizedQuery)) return true
        if (subtitle.isNotBlank() && normalize(subtitle).contains(normalizedQuery)) return true
        return keywords.any { normalize(it).contains(normalizedQuery) }
    }

    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        return diacriticsRegex.replace(decomposed, "").lowercase()
    }
}

/**
 * Resuelve la sección a expandir cuando SettingsScreen se abre por deep-link (ej. desde el
 * diálogo de descarga de mapa: `settings?expand=mapa`). [key] es el nombre del enum
 * [SettingsSectionId] sin importar mayúsculas/acentos — `null` o sin match devuelve `null`.
 */
internal fun settingsSectionFromDeepLinkKey(key: String?): SettingsSectionId? {
    if (key.isNullOrBlank()) return null
    return SettingsSectionId.entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
}
