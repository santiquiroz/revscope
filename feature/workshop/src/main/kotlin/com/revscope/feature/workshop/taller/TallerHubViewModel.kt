package com.revscope.feature.workshop.taller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TallerHubViewModel @Inject constructor(
    private val repositorio: TallerRepository,
    private val entorno: EntornoTaller,
) : ViewModel() {

    private val verTodas = MutableStateFlow(false)

    val estado: StateFlow<TallerHubEstado> =
        combine(
            entorno.vehiculo.flatMapLatest(::datosDe),
            entorno.conexion,
            verTodas,
            entorno.capacidades,
        ) { datos, conexion, todas, capacidades ->
            ResumenHub.de(datos, conexion, todas, entorno.zona, capacidades)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(DETENER_TRAS_MS), TallerHubEstado())

    fun alternarVerTodas() {
        verTodas.value = !verTodas.value
    }

    private fun datosDe(vehiculo: VehiculoTaller?): Flow<DatosHub> {
        if (vehiculo == null) return flowOf(DatosHub(null, emptyList(), emptyList()))
        return repositorio.observarSesiones(vehiculo.id).flatMapLatest { sesiones ->
            eventosDeLaAbierta(sesiones).map { eventos -> DatosHub(vehiculo, sesiones, eventos) }
        }
    }

    private fun eventosDeLaAbierta(sesiones: List<SesionTaller>) =
        sesiones.firstOrNull { it.abierta }?.let { repositorio.observarEventos(it.id) } ?: flowOf(emptyList())

    private companion object {
        const val DETENER_TRAS_MS = 5_000L
    }
}
