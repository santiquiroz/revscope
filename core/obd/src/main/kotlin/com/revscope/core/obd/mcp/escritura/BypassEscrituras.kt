package com.revscope.core.obd.mcp.escritura

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Solo en memoria a propósito: el bypass muere con el servidor MCP o con el proceso, nunca sobrevive a un reinicio.
@Singleton
class BypassEscrituras @Inject constructor() {

    private val _activo = MutableStateFlow(false)
    val activo: StateFlow<Boolean> = _activo.asStateFlow()

    fun encender() {
        _activo.value = true
    }

    fun apagar() {
        _activo.value = false
    }
}
