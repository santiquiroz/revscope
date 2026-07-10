package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiRequest
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.trip.MaintenanceCalculator
import com.revscope.core.obd.workshop.DiagnosticRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import timber.log.Timber
import javax.inject.Inject

sealed class MechanicChatMessage {
    data class User(val text: String) : MechanicChatMessage()
    data class Assistant(val text: String) : MechanicChatMessage()
    data class SystemError(val text: String) : MechanicChatMessage()
}

data class MechanicChatUiState(
    val messages: List<MechanicChatMessage> = emptyList(),
    val isSending: Boolean = false,
    /** Null while the initial provider check is in flight. */
    val hasProvider: Boolean? = null,
)

@HiltViewModel
class MechanicChatViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val aiProviderFactory: AiProviderFactory,
    private val healthReportDao: HealthReportDao,
    private val sessionDao: SessionDao,
    private val maintenanceDao: MaintenanceDao,
) : ViewModel() {

    private val _state = MutableStateFlow(MechanicChatUiState())
    val state: StateFlow<MechanicChatUiState> = _state.asStateFlow()

    /** Built once per screen open — see class doc on MechanicChatContextBuilder. */
    private var systemPrompt: String? = null
    private val history = mutableListOf<MechanicChatContextBuilder.ChatTurn>()

    init {
        viewModelScope.launch { prepareContext() }
    }

    fun sendMessage(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty() || _state.value.isSending) return
        val prompt = systemPrompt ?: return

        _state.value = _state.value.copy(
            messages = _state.value.messages + MechanicChatMessage.User(trimmed),
            isSending = true,
        )
        viewModelScope.launch { completeMessage(prompt, trimmed) }
    }

    private suspend fun prepareContext() {
        val provider = currentProviderOrNull()
        if (provider == null) {
            _state.value = _state.value.copy(hasProvider = false)
            return
        }
        systemPrompt = MechanicChatContextBuilder.buildSystemPrompt(buildVehicleContext())
        _state.value = _state.value.copy(hasProvider = true)
    }

    private suspend fun completeMessage(systemPrompt: String, question: String) {
        val provider = currentProviderOrNull()
        if (provider == null) {
            appendSystemError()
            return
        }
        val userMessage = MechanicChatContextBuilder.buildUserMessage(history, question)
        val request = AiRequest(system = systemPrompt, user = userMessage, maxTokens = MAX_TOKENS, needsWebSearch = false)
        val result = try {
            provider.complete(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result.fold(
            onSuccess = { text -> onAnswer(question, text.trim()) },
            onFailure = { e ->
                Timber.e(e, "MechanicChatViewModel: completion failed")
                appendSystemError()
            },
        )
    }

    private fun onAnswer(question: String, answer: String) {
        history += MechanicChatContextBuilder.ChatTurn(question, answer)
        _state.value = _state.value.copy(
            messages = _state.value.messages + MechanicChatMessage.Assistant(answer),
            isSending = false,
        )
    }

    private fun appendSystemError() {
        _state.value = _state.value.copy(
            messages = _state.value.messages + MechanicChatMessage.SystemError(ERROR_MESSAGE),
            isSending = false,
        )
    }

    private suspend fun currentProviderOrNull() = try {
        aiProviderFactory.current()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "MechanicChatViewModel: failed to resolve AI provider")
        null
    }

    private suspend fun buildVehicleContext(): MechanicChatContextBuilder.VehicleContext {
        val profile = sessionManager.activeProfile.value
        val odometro = profile?.let { odometroActualPara(it) }
        return MechanicChatContextBuilder.VehicleContext(
            profileName = profile?.name,
            profileType = profile?.type,
            fuelType = profile?.fuelType,
            odometroKm = odometro,
            conectado = sessionManager.connectionState.value is ConnectionState.Connected,
            lecturas = sessionManager.readings.value,
            ultimoChequeo = ultimoChequeo(),
            ultimosViajes = ultimosViajes(profile),
            mantenimientoProximo = profile?.let { mantenimientoProximoTexto(it, odometro) },
        )
    }

    private suspend fun odometroActualPara(profile: VehicleProfileEntity): Double {
        val suma = safeCall(0.0) { sessionDao.observeSumDistanceKmForProfile(profile.id).first() }
        return MaintenanceCalculator.odometroActual(profile.odometerBaseKm, suma)
    }

    private suspend fun ultimoChequeo(): List<MechanicChatContextBuilder.ChequeoItem> {
        val report = safeCall(null) { healthReportDao.latest() } ?: return emptyList()
        return parseChequeoItems(report.resultsJson)
    }

    private suspend fun ultimosViajes(profile: VehicleProfileEntity?): List<MechanicChatContextBuilder.ViajeResumen> {
        if (profile == null) return emptyList()
        val sesiones = safeCall(emptyList()) { sessionDao.getRecentForProfile(profile.id, RECENT_TRIPS_LIMIT) }
        return sesiones.map {
            MechanicChatContextBuilder.ViajeResumen(
                startedAtEpochMs = it.startedAt,
                distanceKm = it.distanceKm,
                ecoScore = it.ecoScore,
            )
        }
    }

    private suspend fun mantenimientoProximoTexto(profile: VehicleProfileEntity, odometroYaCalculado: Double?): String? {
        val items = safeCall(emptyList()) { maintenanceDao.listForProfile(profile.id) }
        if (items.isEmpty()) return null
        val odometro = odometroYaCalculado ?: odometroActualPara(profile)
        val estados = MaintenanceCalculator.calculate(odometro, items)
        return MaintenanceCalculator.detalleTexto(estados)
    }

    /** Rethrows [CancellationException] instead of swallowing it, unlike [kotlin.runCatching]. */
    private suspend fun <T> safeCall(default: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "MechanicChatViewModel: context fetch failed")
        default
    }

    private fun parseChequeoItems(json: String): List<MechanicChatContextBuilder.ChequeoItem> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            MechanicChatContextBuilder.ChequeoItem(
                titulo = o.getString("titulo"),
                nivel = DiagnosticRules.Nivel.valueOf(o.getString("nivel")),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    private companion object {
        const val MAX_TOKENS = 800
        const val RECENT_TRIPS_LIMIT = 3
        const val ERROR_MESSAGE = "No se pudo consultar — revisa tu proveedor de IA en Ajustes"
    }
}
