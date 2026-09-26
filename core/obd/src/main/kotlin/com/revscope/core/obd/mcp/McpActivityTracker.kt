package com.revscope.core.obd.mcp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hora de la última `tools/call`. Mientras haya una en la ventana, alguien mira desde el PC y el
 * sondeo no debe estirarse aunque la pantalla del teléfono esté apagada.
 */
@Singleton
class McpActivityTracker(private val nowMs: () -> Long) {

    @Inject constructor() : this({ System.currentTimeMillis() })

    private val ultimaLlamadaMs = MutableStateFlow<Long?>(null)

    fun registrarLlamada() {
        ultimaLlamadaMs.value = nowMs()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val espectadorActivo: Flow<Boolean> = ultimaLlamadaMs
        .transformLatest { ultima ->
            if (ultima == null) {
                emit(false)
                return@transformLatest
            }
            emit(true)
            delay((ultima + VENTANA_MS - nowMs()).coerceAtLeast(0))
            emit(false)
        }
        .distinctUntilChanged()

    companion object {
        const val VENTANA_MS = 60_000L
    }
}
