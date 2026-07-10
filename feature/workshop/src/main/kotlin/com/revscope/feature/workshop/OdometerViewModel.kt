package com.revscope.feature.workshop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.export.CsvShare
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.OdometerChecker
import com.revscope.core.obd.workshop.OdometerVerifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val NO_SOPORTADO_MENSAJE =
    "Tu vehículo no expone el odómetro por OBD (PID 01 A6) — usa el escáner Mode 22 " +
        "para buscar el DID del fabricante"
private const val LECTURA_FALLIDA_MENSAJE = "No se pudo leer el odómetro — intenta de nuevo"

@HiltViewModel
class OdometerViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = sessionManager.connectionState
    val odometerSupported: StateFlow<Boolean?> = sessionManager.odometerSupported
    val lastCheck: StateFlow<OdometerChecker.Result?> = sessionManager.odometerCheck

    private val _historial = MutableStateFlow<List<OdometerVerifier.Reading>>(emptyList())
    val historial: StateFlow<List<OdometerVerifier.Reading>> = _historial.asStateFlow()

    private val _leyendoAhora = MutableStateFlow(false)
    val leyendoAhora: StateFlow<Boolean> = _leyendoAhora.asStateFlow()

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.activeProfile.collect { refrescarHistorial(it?.id) }
        }
        viewModelScope.launch {
            sessionManager.odometerCheck.collect { refrescarHistorial(sessionManager.activeProfile.value?.id) }
        }
    }

    fun leerAhora() {
        if (_leyendoAhora.value) return
        if (sessionManager.connectionState.value !is ConnectionState.Connected) {
            _mensaje.value = "Conecta el adaptador primero"
            return
        }
        viewModelScope.launch {
            _leyendoAhora.value = true
            try {
                val result = sessionManager.checkOdometerNow()
                if (result == null) _mensaje.value = mensajeDeFalloDeLectura()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "OdometerViewModel: lectura falló")
                _mensaje.value = LECTURA_FALLIDA_MENSAJE
            } finally {
                _leyendoAhora.value = false
            }
        }
    }

    fun dismissMensaje() {
        _mensaje.value = null
    }

    fun exportCsv(context: Context) {
        val rows = _historial.value
        if (rows.isEmpty()) return
        viewModelScope.launch {
            CsvShare.shareCsv(
                context = context,
                tipo = "odometro",
                header = listOf("timestamp_iso", "epoch_ms", "km"),
                rows = rows.asSequence().map { listOf(CsvShare.isoTimestamp(it.epochMs), it.epochMs, it.km) },
            )
        }
    }

    private fun mensajeDeFalloDeLectura(): String =
        if (sessionManager.odometerSupported.value == false) NO_SOPORTADO_MENSAJE else LECTURA_FALLIDA_MENSAJE

    private suspend fun refrescarHistorial(profileId: Long?) {
        _historial.value = profileId?.let { sessionManager.odometerHistoryFor(it) } ?: emptyList()
    }
}
