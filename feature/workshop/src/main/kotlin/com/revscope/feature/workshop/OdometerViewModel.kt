package com.revscope.feature.workshop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.export.CsvShare
import com.revscope.core.common.i18n.StringResolver
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

@HiltViewModel
class OdometerViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val resolver: StringResolver,
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
            _mensaje.value = resolver.get(R.string.workshop_odometer_error_not_connected)
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
                _mensaje.value = resolver.get(R.string.workshop_odometer_error_reading)
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
                resolver = resolver,
            )
        }
    }

    private fun mensajeDeFalloDeLectura(): String =
        resolver.get(
            if (sessionManager.odometerSupported.value == false) {
                R.string.workshop_odometer_error_unsupported
            } else {
                R.string.workshop_odometer_error_reading
            },
        )

    private suspend fun refrescarHistorial(profileId: Long?) {
        _historial.value = profileId?.let { sessionManager.odometerHistoryFor(it) } ?: emptyList()
    }
}
