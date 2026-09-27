package com.revscope.feature.workshop

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.export.CsvShare
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.DtcLectura
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.EstadoSoporte
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HealthCheckViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val reportDao: HealthReportDao,
    private val telemetryDao: com.revscope.core.data.db.dao.TelemetryDao,
    private val registro: RegistroTaller,
    private val registry: PidRegistry,
) : ViewModel() {

    sealed interface UiState {
        data object Idle : UiState
        data class Running(val paso: String) : UiState
        data class Done(
            val items: List<DiagnosticRules.Diagnosis>,
            val dtcCodes: List<String>,
            val timestamp: Long,
            val noDisponibles: List<String> = emptyList(),
        ) : UiState
        data class Error(val mensaje: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { reportDao.latest() }.getOrNull()?.let { last ->
                _state.value = UiState.Done(HealthReportFormato.leer(last.resultsJson).items, emptyList(), last.timestamp)
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
                val dtcScan = readAllDtcs()
                items += buildDtcDiagnosis(dtcScan)

                _state.value = UiState.Running("Consultando monitores de readiness…")
                val readiness = readReadiness()
                items += DiagnosticosChequeo.readiness(registry.estadoSoporte(DiagnosticosChequeo.READINESS_PID), readiness)

                _state.value = UiState.Running("Muestreando mezcla y sensores ($SAMPLE_SECONDS s)…")
                val mezcla = sampleMixture()
                items += mezcla.diagnosticos

                _state.value = UiState.Running("Verificando odómetro…")
                items += odometerDiagnoses()

                _state.value = UiState.Running("Analizando tendencia de batería…")
                batteryTrendDiagnosis()?.let { items += it }

                val now = System.currentTimeMillis()
                val metricas = MetricasChequeo.desde(mezcla.lecturas, readiness, dtcScan.codigosLeidos())
                val reportId = persist(items, metricas, now)
                _state.value = UiState.Done(items, dtcScan.codes, now, parametrosNoDisponibles())
                registro.anotarChequeo(reportId, items, metricas, dtcScan.scan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "HealthCheck failed")
                _state.value = UiState.Error("Falló el chequeo: ${e.message}")
            }
        }
    }

    fun share(context: Context) {
        val done = _state.value as? UiState.Done ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                HealthReportCard.render(
                    context, done.items, done.dtcCodes,
                    sessionManager.activeProfile.value?.name ?: "Mi vehículo", done.timestamp,
                )
            } ?: return@launch
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir informe"))
        }
    }

    fun exportCsv(context: Context) {
        val done = _state.value as? UiState.Done ?: return
        if (done.items.isEmpty()) return
        val timestampIso = CsvShare.isoTimestamp(done.timestamp)
        viewModelScope.launch {
            CsvShare.shareCsv(
                context = context,
                tipo = "healthcheck",
                header = listOf("area", "nivel", "titulo", "causa", "timestamp"),
                rows = done.items.asSequence().map { item ->
                    listOf(item.area, item.nivel.name, item.titulo, item.causaProbable, timestampIso)
                },
            )
        }
    }

    /** Distinguishes a genuinely clean scan from one where a mode failed to answer mid-scan. */
    /** Tendencia de voltaje entre sesiones — null cuando no hay historial suficiente. */
    private suspend fun batteryTrendDiagnosis(): DiagnosticRules.Diagnosis? {
        val voltages = runCatching { telemetryDao.recentSessionVoltages() }.getOrNull() ?: return null
        val result = com.revscope.core.obd.workshop.BatteryTrendAnalyzer.analyze(voltages.map { it.avgVolts })
        return when (result.verdict) {
            com.revscope.core.obd.workshop.BatteryTrendAnalyzer.Verdict.SIN_DATOS -> null
            com.revscope.core.obd.workshop.BatteryTrendAnalyzer.Verdict.OK -> DiagnosticRules.Diagnosis(
                DiagnosticRules.Nivel.OK, "Batería (tendencia)", "Carga estable entre viajes", result.detalle,
            )
            com.revscope.core.obd.workshop.BatteryTrendAnalyzer.Verdict.DEGRADANDO -> DiagnosticRules.Diagnosis(
                DiagnosticRules.Nivel.ATENCION, "Batería (tendencia)", "Voltaje de carga en descenso", result.detalle,
            )
            com.revscope.core.obd.workshop.BatteryTrendAnalyzer.Verdict.CARGA_DEBIL -> DiagnosticRules.Diagnosis(
                DiagnosticRules.Nivel.ATENCION, "Batería (tendencia)", "Carga débil sostenida", result.detalle,
            )
        }
    }

    private data class DtcScanResult(val codes: List<String>, val readFailed: Boolean, val scan: DtcScan?) {
        fun codigosLeidos(): List<String>? = scan?.takeUnless { readFailed }?.todos?.map { it.code }
    }

    private data class MuestreoMezcla(
        val diagnosticos: List<DiagnosticRules.Diagnosis>,
        val lecturas: Map<String, ObdReading>,
    )

    private fun buildDtcDiagnosis(scan: DtcScanResult): DiagnosticRules.Diagnosis = when {
        scan.readFailed -> DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.ATENCION, "DTC", "Lectura de códigos incompleta",
            "Se perdió el enlace durante el escaneo — repite el chequeo",
        )
        scan.codes.isEmpty() -> DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.OK, "DTC", "Sin códigos de falla", "Memoria de fallas limpia",
        )
        else -> DiagnosticRules.Diagnosis(
            DiagnosticRules.Nivel.FALLA, "DTC",
            "${scan.codes.size} códigos: ${scan.codes.joinToString()}",
            "Ábrelos en Códigos de falla para explicación con IA",
        )
    }

    private suspend fun readReadiness(): ReadinessParser.ReadinessStatus? {
        if (registry.estadoSoporte(DiagnosticosChequeo.READINESS_PID) == EstadoSoporte.NoSoportado) return null
        return sessionManager.rawExchange("01 01\r").getOrNull()?.let { ReadinessParser.parse(it) }
    }

    private suspend fun sampleMixture(): MuestreoMezcla {
        sessionManager.setWorkshopMode(true)
        try {
            val o2Samples = collectO2Samples()
            val readings = sessionManager.readings.value
            return MuestreoMezcla(
                DiagnosticosChequeo.mezcla(readings, o2Samples, registry::estadoSoporte),
                readings,
            )
        } finally {
            sessionManager.setWorkshopMode(false)
        }
    }

    /**
     * Empty when the ECU doesn't support PID 01 A6. When it does but the read still failed
     * (transient link issue), surfaces an ATENCION item instead of silently dropping it.
     */
    private suspend fun odometerDiagnoses(): List<DiagnosticRules.Diagnosis> {
        val diagnosis = sessionManager.checkOdometerNow()?.diagnosis
        if (diagnosis != null) return listOf(diagnosis)
        if (sessionManager.odometerSupported.value != true) return emptyList()
        return listOf(odometerLecturaNoDisponibleDiagnosis())
    }

    private fun odometerLecturaNoDisponibleDiagnosis(): DiagnosticRules.Diagnosis = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.ATENCION, "Odómetro", "Lectura no disponible",
        "No se pudo leer el odómetro — repite el chequeo",
    )

    private suspend fun collectO2Samples(): List<Double> {
        if (registry.estadoSoporte(DiagnosticosChequeo.O2_SENSOR_B1S1_PID) == EstadoSoporte.NoSoportado) {
            return emptyList()
        }
        val samples = mutableListOf<Double>()
        // 10 s window at 250 ms/sample = 40 samples, clears DiagnosticRules.O2_MIN_MUESTRAS (30)
        repeat(O2_SAMPLE_COUNT) {
            delay(O2_SAMPLE_INTERVAL_MS)
            sessionManager.readings.value[DiagnosticosChequeo.O2_SENSOR_B1S1_PID]?.let { samples += it.value }
        }
        return samples
    }

    private suspend fun readAllDtcs(): DtcScanResult {
        val scan = sessionManager.leerDtcCompleto("ui:chequeo", DtcLectura(freezeFrame = false)).getOrNull()
            ?: return DtcScanResult(emptyList(), readFailed = true, scan = null)
        val codes = scan.activos.map { it.code } +
            scan.pendientes.map { "${it.code} (pendiente)" } +
            scan.permanentes.map { "${it.code} (permanente)" }
        return DtcScanResult(codes, readFailed = scan.enlacePerdido, scan = scan)
    }

    private suspend fun persist(
        items: List<DiagnosticRules.Diagnosis>,
        metricas: MetricasChequeo,
        timestamp: Long,
    ): Long? = runCatching {
        reportDao.insert(
            HealthReportEntity(
                vehicleProfileId = sessionManager.activeProfile.value?.id ?: 0L,
                timestamp = timestamp,
                resultsJson = HealthReportFormato.escribir(items, metricas),
            ),
        )
    }.onFailure { Timber.w(it, "HealthCheck: persist failed") }.getOrNull()

    companion object {
        private const val SAMPLE_SECONDS = 10
        private const val O2_SAMPLE_INTERVAL_MS = 250L
        private const val O2_SAMPLE_COUNT = 40
    }

    private fun parametrosNoDisponibles(): List<String> =
        DiagnosticosChequeo.parametrosNoDisponibles(registry::estadoSoporte)
}

internal object DiagnosticosChequeo {
    const val READINESS_PID = "01"
    const val O2_SENSOR_B1S1_PID = "14"

    private const val ENGINE_RUNNING_RPM = 400.0
    private const val SHORT_TRIM_B1_PID = "06"
    private const val LONG_TRIM_B1_PID = "07"
    private const val LONG_TRIM_B2_PID = "09"
    private const val RPM_PID = "0C"
    private const val COOLANT_TEMP_PID = "05"
    private val PARAMETROS = linkedMapOf(
        READINESS_PID to "Monitores de readiness",
        COOLANT_TEMP_PID to "Temperatura del motor",
        SHORT_TRIM_B1_PID to "Ajuste corto de combustible B1",
        LONG_TRIM_B1_PID to "Ajuste largo de combustible B1",
        LONG_TRIM_B2_PID to "Ajuste largo de combustible B2",
        RPM_PID to "RPM del motor",
        O2_SENSOR_B1S1_PID to "Sensor O2 B1S1",
    )

    fun readiness(
        soporte: EstadoSoporte,
        status: ReadinessParser.ReadinessStatus?,
    ): List<DiagnosticRules.Diagnosis> = when {
        soporte == EstadoSoporte.NoSoportado -> emptyList()
        status != null -> DiagnosticRules.evaluarReadiness(status)
        else -> listOf(readinessNoDisponible())
    }

    fun mezcla(
        lecturas: Map<String, ObdReading>,
        muestrasO2: List<Double>,
        estadoSoporte: (String) -> EstadoSoporte,
    ): List<DiagnosticRules.Diagnosis> = buildList {
        val largoB1 = lecturaSoportada(lecturas, LONG_TRIM_B1_PID, estadoSoporte)
        val largoB2 = lecturaSoportada(lecturas, LONG_TRIM_B2_PID, estadoSoporte)
        largoB1?.let { add(DiagnosticRules.evaluarFuelTrimLargo(it.value)) }
        largoB2?.let { add(DiagnosticRules.evaluarFuelTrimLargo(it.value)) }
        val cortoB1 = lecturaSoportada(lecturas, SHORT_TRIM_B1_PID, estadoSoporte)
        if (cortoB1 != null && largoB1 != null) {
            add(DiagnosticRules.evaluarTrimCombinado(cortoB1.value, largoB1.value))
        }
        if (estadoSoporte(O2_SENSOR_B1S1_PID) != EstadoSoporte.NoSoportado) {
            add(DiagnosticRules.evaluarO2(muestrasO2))
        }
        agregarVoltajeSiHayRpm(lecturas, estadoSoporte)
        lecturaSoportada(lecturas, COOLANT_TEMP_PID, estadoSoporte)
            ?.let { add(DiagnosticRules.evaluarTemperatura(it.value)) }
    }

    fun parametrosNoDisponibles(estadoSoporte: (String) -> EstadoSoporte): List<String> =
        PARAMETROS.mapNotNull { (pid, nombre) ->
            nombre.takeIf { estadoSoporte(pid) == EstadoSoporte.NoSoportado }
        }

    private fun MutableList<DiagnosticRules.Diagnosis>.agregarVoltajeSiHayRpm(
        lecturas: Map<String, ObdReading>,
        estadoSoporte: (String) -> EstadoSoporte,
    ) {
        if (estadoSoporte(RPM_PID) == EstadoSoporte.NoSoportado) return
        val rpm = lecturas[RPM_PID]?.value ?: return
        val voltaje = lecturas[ObdSessionManager.VBAT_PID]?.value ?: return
        add(DiagnosticRules.evaluarVoltaje(voltaje, rpm > ENGINE_RUNNING_RPM))
    }

    private fun lecturaSoportada(
        lecturas: Map<String, ObdReading>,
        pid: String,
        estadoSoporte: (String) -> EstadoSoporte,
    ): ObdReading? = lecturas[pid].takeUnless { estadoSoporte(pid) == EstadoSoporte.NoSoportado }

    private fun readinessNoDisponible() = DiagnosticRules.Diagnosis(
        DiagnosticRules.Nivel.ATENCION,
        "Readiness",
        "Readiness no disponible",
        "Se perdió el enlace durante el escaneo — repite el chequeo",
    )
}
