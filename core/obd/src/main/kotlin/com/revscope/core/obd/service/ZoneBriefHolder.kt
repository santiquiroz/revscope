package com.revscope.core.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Último brief de conducción de zona generado por IA al llegar a un lugar nuevo.
 * Lo escribe ZoneBriefAlerter (:core:intelligence) y lo lee la UI (mismo patrón que
 * LiveRouteHolder: dato vivo compartido sin acoplar módulos de feature).
 */
@Singleton
class ZoneBriefHolder @Inject constructor() {

    enum class Source { COMMUNITY, AI }

    data class ZoneBrief(val place: String, val body: String, val source: Source, val atMs: Long)

    private val _brief = MutableStateFlow<ZoneBrief?>(null)
    val brief: StateFlow<ZoneBrief?> = _brief.asStateFlow()

    fun publish(place: String, body: String, source: Source) {
        _brief.value = ZoneBrief(place, body, source, System.currentTimeMillis())
    }

    fun clear() {
        _brief.value = null
    }
}
