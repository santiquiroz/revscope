package com.revscope.core.obd.mcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Qué puede hacer un cliente MCP: leer siempre; controlar, borrar DTC y escribir en módulos solo si el dueño lo activó. */
enum class McpPermiso { LECTURA, CONTROL, BORRADO, ESCRITURA }

object McpPermisos {

    const val MENSAJE_DESHABILITADO =
        "Deshabilitado: activa «Permitir control desde MCP» en Ajustes → Avanzado y diagnóstico → Servidor MCP"

    fun desdeAjustes(controlActivo: Boolean, borradoActivo: Boolean, escrituraActiva: Boolean = false): Set<McpPermiso> = buildSet {
        add(McpPermiso.LECTURA)
        if (controlActivo) add(McpPermiso.CONTROL)
        if (controlActivo && borradoActivo) add(McpPermiso.BORRADO)
        if (controlActivo && escrituraActiva) add(McpPermiso.ESCRITURA)
    }
}

/** Lee los permisos vigentes en cada llamada: apagar el interruptor surte efecto de inmediato. */
class McpPermisosProvider @Inject constructor(
    private val settings: DataStore<Preferences>,
) {
    suspend fun actuales(): Set<McpPermiso> {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return setOf(McpPermiso.LECTURA)
        return McpPermisos.desdeAjustes(
            controlActivo = prefs[PreferencesKeys.MCP_CONTROL_ENABLED] ?: false,
            borradoActivo = prefs[PreferencesKeys.MCP_CLEAR_DTC_ENABLED] ?: false,
            escrituraActiva = prefs[PreferencesKeys.MCP_WRITE_ENABLED] ?: false,
        )
    }
}
