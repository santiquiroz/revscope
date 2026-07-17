package com.revscope.core.obd.mcp

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.legal.RestrictionRulesSource
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Estado de "vehículo al día": SOAT, tecnomecánica, pico y placa, seguro y licencia. */
class GetDocumentosTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val settings: DataStore<Preferences>,
    private val aiRulesSource: RestrictionRulesSource,
) : McpTool {

    override val name = "get_documentos"
    override val description = "Estado de documentos del vehículo activo: SOAT, tecnomecánica, pico y placa, seguro y licencia"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        val profile = sessionManager.activeProfile.value
            ?: return JSONObject().put("error", "sin perfil de vehículo activo").toString()
        val prefs = settings.data.first()
        val license = prefs[PreferencesKeys.LICENSE_EXPIRES_AT]
        val overrideRules = prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson)
        val documents = DocumentStatusCalculator.fromProfile(profile, license)
        val now = System.currentTimeMillis()
        val aiFallback = profile.picoPlacaCity
            ?.takeIf { DocumentStatusCalculator.needsAiFallback(it, overrideRules, now) }
            ?.let { runCatching { aiRulesSource.rulesForCity(it) }.getOrNull() }
        val statuses = DocumentStatusCalculator.calculate(documents, overrideRules, now, aiFallbackRules = aiFallback)
        return JSONObject()
            .put(
                "documentos",
                JSONArray(
                    statuses.map {
                        JSONObject()
                            .put("tipo", it.tipo.name)
                            .put("nivel", it.nivel.name)
                            .put("titulo", it.titulo)
                            .put("detalle", it.detalle)
                    },
                ),
            )
            .toString()
    }
}
