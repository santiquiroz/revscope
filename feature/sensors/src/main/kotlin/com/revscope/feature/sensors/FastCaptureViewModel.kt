package com.revscope.feature.sensors

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.export.CsvShare
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.terminadasDesdeAhora
import com.revscope.core.obd.telemetry.captura.CapturaCsv
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.EstadisticasCaptura
import com.revscope.core.obd.telemetry.captura.EstadoCaptura
import com.revscope.core.obd.telemetry.captura.LimitesCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.SeleccionPids
import com.revscope.core.obd.telemetry.captura.VentanaCaptura
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/** Taller → Sensores → Captura rápida: elegir PIDs, iniciar/detener, gráfica de 10 s, tasa medida y CSV. */
@HiltViewModel
class FastCaptureViewModel @Inject constructor(
    private val manager: ObdSessionManager,
    private val registry: PidRegistry,
    private val settings: DataStore<Preferences>,
    private val registro: RegistroTaller,
) : ViewModel() {

    private val captura = manager.captura

    val estado: StateFlow<EstadoCaptura> = captura.estado
    val estadisticas: StateFlow<EstadisticasCaptura?> = captura.estadisticas
    val ultimoResumen: StateFlow<ResumenCaptura?> = captura.ultimoResumen
    val conectado: StateFlow<Boolean> = manager.connectionState
        .map { it is ConnectionState.Connected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, manager.connectionState.value is ConnectionState.Connected)

    private val _seleccion = MutableStateFlow(SeleccionPids.PEDAL_Y_MARIPOSA)
    val seleccion: StateFlow<List<String>> = _seleccion.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    private val ventana = VentanaCaptura()
    private val _series = MutableStateFlow<Map<String, List<Pair<Long, Double>>>>(emptyMap())
    val series: StateFlow<Map<String, List<Pair<Long, Double>>>> = _series.asStateFlow()

    private var refresco: Job? = null

    init {
        viewModelScope.launch { cargarSeleccion() }
        viewModelScope.launch { estado.collect(::alCambiarEstado) }
        viewModelScope.launch { anotarCapturasTerminadas() }
    }

    /** PIDs de modo 01 que el vehículo soporta (todos antes de conectar), por número de PID. */
    fun candidatos(): List<PidDefinition> =
        registry.allDefinitions().filter { it.mode == "01" && registry.isSupported(it.pid) }.sortedBy { it.pid }

    fun nombreDe(pid: String): String = registry.getDefinition(pid)?.nameEs ?: pid

    fun alternar(pid: String) = guardarSeleccion(SeleccionPids.alternar(_seleccion.value, pid))

    fun elegirPedalYMariposa() = guardarSeleccion(SeleccionPids.PEDAL_Y_MARIPOSA)

    fun iniciar() {
        viewModelScope.launch {
            val minutos = settings.data.first()[PreferencesKeys.FAST_CAPTURE_MAX_MIN] ?: DURACION_DEFAULT_MIN
            val config = ConfigCaptura(_seleccion.value, minutos.coerceIn(1, 30) * 60_000L)
            captura.iniciar(config)
                .onSuccess { inicio ->
                    _mensaje.value = inicio.pidsNoSoportados.takeIf { it.isNotEmpty() }
                        ?.let { "No soportados por este vehículo: ${it.joinToString()}" }
                }
                .onFailure { _mensaje.value = it.message }
        }
    }

    fun detener() {
        viewModelScope.launch { captura.detener() }
    }

    fun descartarMensaje() {
        _mensaje.value = null
    }

    suspend fun exportarLargo(context: Context) {
        val ruta = ultimoResumen.value?.rutaCsv ?: return
        CsvShare.shareFile(context, File(ruta))
    }

    suspend fun exportarAncho(context: Context) {
        val inicio = captura.capturaEnMemoria() ?: return
        val inicioEpochMs = captura.inicioEpochMs(inicio.id) ?: return
        val muestras = captura.muestrasActuales()
        if (muestras.isEmpty()) return
        CsvShare.shareCsv(
            context = context,
            tipo = "ancho-captura",
            header = CapturaCsv.cabeceraAncha(inicio.pidsAceptados),
            rows = CapturaCsv.filasAnchas(muestras, inicio.pidsAceptados, inicioEpochMs).asSequence(),
            comment = "revscope-captura v1 (ancho); pids=${inicio.pidsAceptados.joinToString(",")}",
        )
    }

    private suspend fun cargarSeleccion() {
        runCatching { settings.data.first()[PreferencesKeys.FAST_CAPTURE_PIDS] }
            .onSuccess { _seleccion.value = SeleccionPids.desdeCsv(it) }
            .onFailure { Timber.w(it, "FastCapture: no se pudo leer la selección") }
    }

    private fun guardarSeleccion(pids: List<String>) {
        _seleccion.value = pids
        viewModelScope.launch {
            runCatching { settings.edit { it[PreferencesKeys.FAST_CAPTURE_PIDS] = SeleccionPids.aCsv(pids) } }
                .onFailure { Timber.w(it, "FastCapture: no se pudo guardar la selección") }
        }
    }

    private suspend fun anotarCapturasTerminadas() {
        ultimoResumen.terminadasDesdeAhora().collect { resumen ->
            registro.anotarCaptura(resumen, captura.muestrasActuales())
        }
    }

    private fun alCambiarEstado(nuevo: EstadoCaptura) {
        refresco?.cancel()
        if (nuevo !is EstadoCaptura.Activa) return
        ventana.reiniciar()
        refresco = viewModelScope.launch { refrescarGrafica(nuevo.inicio.id) }
    }

    private suspend fun refrescarGrafica(id: String) {
        while (true) {
            captura.pagina(id, ventana.cursor, MAX_POR_REFRESCO, pids = null)?.let(ventana::agregar)
            _series.value = ventana.series()
            delay(REFRESCO_GRAFICA_MS)
        }
    }

    companion object {
        const val MAX_PIDS = LimitesCaptura.MAX_PIDS
        private const val DURACION_DEFAULT_MIN = 5
        private const val REFRESCO_GRAFICA_MS = 150L
        private const val MAX_POR_REFRESCO = 20_000
    }
}
