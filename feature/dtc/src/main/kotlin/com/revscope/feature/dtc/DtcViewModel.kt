package com.revscope.feature.dtc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.obd.diagnostics.AvisoLecturaDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.FreezeFrame
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.diagnostics.ReglasBorradoDtc
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DtcUiState {
    object Idle : DtcUiState()
    object Reading : DtcUiState()
    object Clearing : DtcUiState()
    data class HasCodes(val codes: List<DtcCodeUi>) : DtcUiState()
    data class Borrado(val resultado: ResultadoBorradoUi) : DtcUiState()
    data class Error(val message: String) : DtcUiState()
}

data class FreezeFrameItem(val label: String, val value: String)

@HiltViewModel
class DtcViewModel @Inject constructor(
    private val orchestrator: IntelligenceOrchestrator,
    private val registry: PidRegistry,
    private val registro: RegistroTaller,
    private val guias: BaseConocimientoDtc,
) : ViewModel() {

    private val _state = MutableStateFlow<DtcUiState>(DtcUiState.Idle)
    val state: StateFlow<DtcUiState> = _state.asStateFlow()

    private val _detalle = MutableStateFlow(DetalleDtc())
    val detalle: StateFlow<DetalleDtc> = _detalle.asStateFlow()

    private val _confirmacionBorrado = MutableStateFlow<ConfirmacionBorradoDtc?>(null)
    val confirmacionBorrado: StateFlow<ConfirmacionBorradoDtc?> = _confirmacionBorrado.asStateFlow()

    private var ultimaLectura: DtcScan? = null
    private var velocidadObservada: Job? = null

    init {
        refrescarSesion()
    }

    fun readDtcCodes(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Reading
            _detalle.update { it.copy(mil = null, freezeFrame = null, lecturaEnSesion = false, avisoLectura = null) }
            connectionVm.leerDtcCompleto(LEASE_OWNER)
                .onSuccess { scan ->
                    mostrarLectura(scan)
                    anotarLectura(scan)
                }
                .onFailure { e -> _state.value = DtcUiState.Error(mensajeDeError(e)) }
        }
    }

    // Una sola observación por pantalla: la velocidad en vivo decide si el botón de borrar se habilita.
    fun observarVelocidad(connectionVm: ConnectionViewModel, reloj: () -> Long = System::currentTimeMillis) {
        if (velocidadObservada != null) return
        velocidadObservada = viewModelScope.launch {
            connectionVm.readings.collect { lecturas ->
                val motivo = motivoBorradoDeshabilitado(lecturas[ReglasBorradoDtc.PID_VELOCIDAD], reloj())
                _detalle.update { it.copy(bloqueoBorrado = motivo) }
            }
        }
    }

    fun alternarGuia(codigo: String) = _detalle.update {
        val abiertas = if (codigo in it.guiasAbiertas) it.guiasAbiertas - codigo else it.guiasAbiertas + codigo
        it.copy(guiasAbiertas = abiertas)
    }

    fun marcarPaso(clave: String, marcado: Boolean) {
        if (_detalle.value.sesion is SesionDtcUi.Ninguna) {
            _detalle.update { it.copy(pasosSinSesion = conPaso(it.pasosSinSesion, clave, marcado)) }
            return
        }
        viewModelScope.launch {
            val sesion = registro.marcarPaso(clave, marcado)
            if (sesion == null) avisar("No se pudo guardar el paso: la sesión ya no está abierta")
            _detalle.update { it.copy(sesion = sesionDtcUi(sesion)) }
        }
    }

    fun guardarEnSesionNueva() {
        val scan = ultimaLectura ?: return
        if (_detalle.value.guardandoEnSesion) return
        _detalle.update { it.copy(guardandoEnSesion = true) }
        viewModelScope.launch {
            registro.abrirSesion(solicitudDesdeLectura(scan))
                .onSuccess { sesion ->
                    val anotada = registro.anotarLecturaDtc(scan) != null
                    val conPasos = trasladarPasosSinSesion() ?: sesion
                    _detalle.update {
                        it.copy(sesion = sesionDtcUi(conPasos), lecturaEnSesion = anotada, pasosSinSesion = emptySet())
                    }
                    avisar("Lectura guardada en la sesión «${sesion.titulo}»")
                }
                .onFailure { e -> avisar(e.message ?: "No se pudo abrir la sesión") }
            _detalle.update { it.copy(guardandoEnSesion = false) }
        }
    }

    fun explicarConIa(codigo: String, connectionVm: ConnectionViewModel) {
        actualizarExplicacion(codigo, ExplicacionIa.Cargando)
        viewModelScope.launch { actualizarExplicacion(codigo, pedirExplicacion(codigo, connectionVm)) }
    }

    fun mensajeMostrado() = _detalle.update { it.copy(mensaje = null) }

    fun solicitarBorrado(connectionVm: ConnectionViewModel, ahoraMs: Long = System.currentTimeMillis()) {
        val codigos = (_state.value as? DtcUiState.HasCodes)?.codes?.map { it.codigo } ?: return
        _confirmacionBorrado.value = ConfirmacionBorradoDtc(
            codigos = codigos,
            rechazo = rechazoConfirmado(connectionVm, ahoraMs),
            freezeFrameEnSesion = _detalle.value.lecturaEnSesion,
        )
    }

    fun cancelarBorrado() {
        _confirmacionBorrado.value = null
    }

    fun confirmarBorrado(
        connectionVm: ConnectionViewModel,
        declaraDetenido: Boolean,
        ahoraMs: Long = System.currentTimeMillis(),
    ) {
        val pendiente = _confirmacionBorrado.value ?: return
        val rechazo = rechazoConfirmado(connectionVm, ahoraMs)
        if (!permiteBorrarDesdeUi(rechazo, declaraDetenido)) {
            _confirmacionBorrado.value = pendiente.copy(rechazo = rechazo)
            return
        }
        _confirmacionBorrado.value = null
        borrar(connectionVm)
    }

    private fun mostrarLectura(scan: DtcScan) {
        ultimaLectura = scan
        val codigos = agruparPorCodigo(scan.todos, guias::guia)
        _detalle.update {
            it.copy(
                mil = milUi(scan),
                freezeFrame = freezeFrameUi(scan.freezeFrame),
                guiasAbiertas = guiasAbiertasAlLeer(codigos),
                avisoLectura = AvisoLecturaDtc.de(scan),
                sinCodigos = AvisoLecturaDtc.sinCodigos(scan),
            )
        }
        _state.value = DtcUiState.HasCodes(codigos)
    }

    // Aparte de la lectura: anotar en la sesión del Taller no demora mostrar los códigos.
    private fun anotarLectura(scan: DtcScan) {
        viewModelScope.launch {
            val anotada = registro.anotarLecturaDtc(scan) != null
            val sesion = registro.sesionAbierta()
            _detalle.update { it.copy(lecturaEnSesion = anotada, sesion = sesionDtcUi(sesion)) }
        }
    }

    private fun refrescarSesion() {
        viewModelScope.launch {
            val sesion = registro.sesionAbierta()
            _detalle.update { it.copy(sesion = sesionDtcUi(sesion)) }
        }
    }

    // Las casillas marcadas antes de abrir la sesión no se pierden al guardar la lectura en ella.
    private suspend fun trasladarPasosSinSesion(): SesionTaller? {
        var ultima: SesionTaller? = null
        _detalle.value.pasosSinSesion.sorted().forEach { clave -> ultima = registro.marcarPaso(clave, marcado = true) ?: ultima }
        return ultima
    }

    private fun freezeFrameUi(freezeFrame: FreezeFrame?): FreezeFrameUi? {
        if (freezeFrame == null) return null
        return FreezeFrameUi(freezeFrame.dtcCausante, freezeFrame.valores.map(::freezeFrameItem))
    }

    private fun freezeFrameItem(reading: ObdReading): FreezeFrameItem {
        val label = registry.getDefinition(reading.pid)?.nameEs ?: reading.pid
        val value = if (reading.value % 1.0 == 0.0) "${reading.value.toInt()}" else "%.1f".format(reading.value)
        return FreezeFrameItem(label = label, value = "$value ${reading.unit}")
    }

    private fun mensajeDeError(e: Throwable, porDefecto: String = "Error leyendo los códigos"): String =
        if (e is IllegalStateException && e.message == "Not connected") "Conecta el adaptador primero"
        else e.message ?: porDefecto

    private fun rechazoConfirmado(connectionVm: ConnectionViewModel, ahoraMs: Long): RechazoBorradoDtc? =
        ReglasBorradoDtc.evaluar(
            confirmado = true,
            velocidad = connectionVm.readings.value[ReglasBorradoDtc.PID_VELOCIDAD],
            ahoraMs = ahoraMs,
        )

    private fun borrar(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _state.value = DtcUiState.Clearing
            connectionVm.borrarDtcConRelectura(LEASE_OWNER)
                .onSuccess { borrado ->
                    _state.value = DtcUiState.Borrado(resultadoBorradoUi(borrado))
                    viewModelScope.launch { registro.anotarBorradoDtc(borrado) }
                }
                .onFailure { e -> _state.value = DtcUiState.Error(mensajeDeError(e, "Error borrando los códigos")) }
        }
    }

    private suspend fun pedirExplicacion(codigo: String, connectionVm: ConnectionViewModel): ExplicacionIa {
        val contexto = connectionVm.readings.value.values.map { ObdReading(pid = it.pid, value = it.value, unit = it.unit) }
        val freezeFrame = _detalle.value.freezeFrame?.items.orEmpty().map { it.label to it.value }
        return try {
            val respuesta = orchestrator.explainDtc(codigo, contexto, freezeFrame)
            ExplicacionIa.Lista(respuesta.explanation, faltaConfigurar = respuesta.source == FUENTE_SIN_CLAVE)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ExplicacionIa.NoDisponible
        }
    }

    private fun actualizarExplicacion(codigo: String, explicacion: ExplicacionIa) {
        val actual = _state.value as? DtcUiState.HasCodes ?: return
        _state.value = actual.copy(
            codes = actual.codes.map { if (it.codigo == codigo) it.copy(explicacion = explicacion) else it },
        )
    }

    private fun avisar(mensaje: String) = _detalle.update { it.copy(mensaje = mensaje) }

    private fun conPaso(pasos: Set<String>, clave: String, marcado: Boolean): Set<String> =
        if (marcado) pasos + clave else pasos - clave

    private companion object {
        const val LEASE_OWNER = "ui:dtc"
        const val FUENTE_SIN_CLAVE = "no_key"
    }
}
