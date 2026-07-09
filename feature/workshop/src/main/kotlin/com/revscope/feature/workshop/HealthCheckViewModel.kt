package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.DiagnosticRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HealthCheckViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val reportDao: HealthReportDao,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Running(val paso: String) : UiState
        data class Done(
            val items: List<DiagnosticRules.Diagnosis>,
            val dtcCodes: List<String>,
            val timestamp: Long,
        ) : UiState
        data class Error(val mensaje: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { reportDao.latest() }.getOrNull()?.let { last ->
                _state.value = UiState.Done(parseStoredItems(last.resultsJson), emptyList(), last.timestamp)
            }
        }
    }

    fun runHealthCheck() {
        if (_state.value is UiState.Running) return
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            _state.value = UiState.Error("Conecta el adaptador primero")
            return
        }
        viewModelScope.launch {
            try {
                val items = mutableListOf<DiagnosticRules.Diagnosis>()

                _state.value = UiState.Running("Leyendo códigos de falla…")
                val dtcCodes = readAllDtcs()
                items += buildDtcDiagnosis(dtcCodes)

                _state.value = UiState.Running("Consultando monitores de readiness…")
                items += readReadinessDiagnoses()

                _state.value = UiState.Running("Muestreando mezcla y sensores ($SAMPLE_SECONDS s)…")
                items += sampleMixtureDiagnoses()

                val now = System.currentTimeMillis()
                persist(items, now)
                _state.value = UiState.Done(items, dtcCodes, now)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "HealthCheck failed")
                sessionManager.setWorkshopMode(false)
                _state.value = UiState.Error("Falló el chequeo: ${e.message}")
            }
        }
    }

    private fun buildDtcDiagnosis(dtcCodes: List<String>): DiagnosticRules.Diagnosis = if (dtcCodes.isEmpty()) {
        DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.OK, "DTC", "Sin códigos de falla", "Memoria de fallas limpia",
        )
    } else {
        DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.FALLA, "DTC",
            "${dtcCodes.size} códigos: ${dtcCodes.joinToString()}",
            "Ábrelos en Códigos de falla para explicación con IA",
        )
    }

    private suspend fun readReadinessDiagnoses(): List<DiagnosticRules.Diagnosis> =
        sessionManager.rawExchange("01 01\r").getOrNull()
            ?.let { ReadinessParser.parse(it) }
            ?.let { DiagnosticRules.evaluarReadiness(it) }
            .orEmpty()

    private suspend fun sampleMixtureDiagnoses(): List<DiagnosticRules.Diagnosis> {
        sessionManager.setWorkshopMode(true)
        val o2Samples = collectO2Samples()
        val readings = sessionManager.readings.value
        sessionManager.setWorkshopMode(false)

        return buildList {
            readings[LONG_TRIM_B1_PID]?.let { add(DiagnosticRules.evaluarFuelTrimLargo(it.value)) }
            readings[LONG_TRIM_B2_PID]?.let { add(DiagnosticRules.evaluarFuelTrimLargo(it.value)) }
            val shortTrimB1 = readings[SHORT_TRIM_B1_PID]
            val longTrimB1 = readings[LONG_TRIM_B1_PID]
            if (shortTrimB1 != null && longTrimB1 != null) {
                add(DiagnosticRules.evaluarTrimCombinado(shortTrimB1.value, longTrimB1.value))
            }
            add(DiagnosticRules.evaluarO2(o2Samples))
            readings[ObdSessionManager.VBAT_PID]?.let {
                val encendido = (readings[RPM_PID]?.value ?: 0.0) > ENGINE_RUNNING_RPM
                add(DiagnosticRules.evaluarVoltaje(it.value, encendido))
            }
            readings[COOLANT_TEMP_PID]?.let { add(DiagnosticRules.evaluarTemperatura(it.value)) }
        }
    }

    private suspend fun collectO2Samples(): List<Double> {
        val samples = mutableListOf<Double>()
        repeat(SAMPLE_SECONDS) {
            delay(SAMPLE_INTERVAL_MS)
            sessionManager.readings.value[O2_SENSOR_B1S1_PID]?.let { samples += it.value }
        }
        return samples
    }

    /**
     * DtcViewModel (feature/dtc) only reads active codes via [ObdSessionManager.readActiveDtc]
     * (Mode 03) — it never surfaces pending/permanent codes, so there is nothing there to reuse.
     * [ResponseParser.parseDtcResponse] (distinct from the manager's DtcCode-returning
     * companion function, which is hardcoded to prefix "43") already generalizes across the
     * 43/47/4A prefixes for Modes 03/07/0A, so it is reused directly here instead.
     */
    private suspend fun readAllDtcs(): List<String> {
        val active = readDtcMode(ACTIVE_DTC_COMMAND)
        val pending = readDtcMode(PENDING_DTC_COMMAND).map { "$it (pendiente)" }
        val permanent = readDtcMode(PERMANENT_DTC_COMMAND).map { "$it (permanente)" }
        return active + pending + permanent
    }

    private suspend fun readDtcMode(command: String): List<String> =
        sessionManager.rawExchange(command).getOrNull()
            ?.let { ResponseParser.parseDtcResponse(it) }
            .orEmpty()

    private suspend fun persist(items: List<DiagnosticRules.Diagnosis>, timestamp: Long) {
        val json = JSONArray().apply {
            items.forEach {
                put(
                    JSONObject()
                        .put("area", it.area)
                        .put("nivel", it.nivel.name)
                        .put("titulo", it.titulo)
                        .put("causa", it.causaProbable),
                )
            }
        }
        runCatching {
            reportDao.insert(
                HealthReportEntity(
                    vehicleProfileId = sessionManager.activeProfile.value?.id ?: 0L,
                    timestamp = timestamp,
                    resultsJson = json.toString(),
                ),
            )
        }.onFailure { Timber.w(it, "HealthCheck: persist failed") }
    }

    private fun parseStoredItems(json: String): List<DiagnosticRules.Diagnosis> = try {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DiagnosticRules.Diagnosis(
                nivel = DiagnosticRules.Nivel.valueOf(o.getString("nivel")),
                area = o.getString("area"),
                titulo = o.getString("titulo"),
                causaProbable = o.getString("causa"),
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val SAMPLE_SECONDS = 10
        private const val SAMPLE_INTERVAL_MS = 1_000L
        private const val ENGINE_RUNNING_RPM = 400.0

        private const val ACTIVE_DTC_COMMAND = "03\r"
        private const val PENDING_DTC_COMMAND = "07\r"
        private const val PERMANENT_DTC_COMMAND = "0A\r"

        private const val SHORT_TRIM_B1_PID = "06"
        private const val LONG_TRIM_B1_PID = "07"
        private const val LONG_TRIM_B2_PID = "09"
        private const val O2_SENSOR_B1S1_PID = "14"
        private const val RPM_PID = "0C"
        private const val COOLANT_TEMP_PID = "05"
    }
}
