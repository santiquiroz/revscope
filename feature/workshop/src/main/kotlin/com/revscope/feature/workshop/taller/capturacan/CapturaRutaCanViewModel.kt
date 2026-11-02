package com.revscope.feature.workshop.taller.capturacan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.escucha.CapturaRutaCan
import com.revscope.core.obd.escucha.EstadoCapturaRuta
import com.revscope.core.obd.escucha.MuestreadorGpsCaptura
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CapturaRutaCanViewModel @Inject constructor(
    private val captura: CapturaRutaCan,
    sessionManager: ObdSessionManager,
    gps: MuestreadorGpsCaptura,
) : ViewModel() {

    private val etiqueta = MutableStateFlow(CapturaRutaCan.ETIQUETA_POR_DEFECTO)
    private val permisoGps = gps.tienePermiso()

    val estado: StateFlow<CapturaRutaUi> = combine(
        captura.estado,
        sessionManager.connectionState,
        etiqueta,
    ) { estado, conexion, texto -> CapturaRutaUi(estado, conexion is ConnectionState.Connected, texto, permisoGps) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CapturaRutaUi(EstadoCapturaRuta.Inactiva, false, etiqueta.value, permisoGps),
        )

    fun cambiarEtiqueta(texto: String) {
        etiqueta.value = texto.take(MAX_ETIQUETA)
    }

    fun iniciar() {
        captura.iniciar(etiqueta.value)
    }

    fun detener() = captura.detener()

    private companion object {
        const val MAX_ETIQUETA = 64
    }
}
