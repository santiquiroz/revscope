package com.revscope.feature.workshop.taller

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.sesion.ChequeoBase
import com.revscope.core.obd.taller.sesion.HistorialChequeos
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NuevaSesionViewModel @Inject constructor(
    private val registro: RegistroTaller,
    private val repositorio: TallerRepository,
    private val historial: HistorialChequeos,
    private val entorno: EntornoTaller,
) : ViewModel() {

    private val _estado = MutableStateFlow(NuevaSesionEstado())
    val estado: StateFlow<NuevaSesionEstado> = _estado.asStateFlow()

    private val abiertas = Channel<Long>(Channel.BUFFERED)
    val sesionAbierta: Flow<Long> = abiertas.receiveAsFlow()

    private var vehiculo: VehiculoTaller? = null

    init {
        viewModelScope.launch { cargar() }
    }

    fun alternarSintoma(sintoma: Sintoma) = _estado.update {
        it.copy(sintomas = if (sintoma in it.sintomas) it.sintomas - sintoma else it.sintomas + sintoma)
    }

    fun cambiarSintomasTexto(texto: String) = _estado.update { it.copy(sintomasTexto = texto) }

    fun cambiarOdometro(texto: String) = _estado.update { it.copy(odometro = texto, odometroDeEcu = false, errorOdometro = null) }

    fun cambiarNotas(texto: String) = _estado.update { it.copy(notas = texto) }

    fun elegirBase(id: Long?) = _estado.update { it.copy(baseElegida = id) }

    fun abrir() {
        val actual = vehiculo ?: return
        val odometro = OdometroTexto.leer(_estado.value.odometro)
        if (odometro.isFailure) {
            _estado.update { it.copy(errorOdometro = OdometroTexto.ERROR) }
            return
        }
        viewModelScope.launch { abrirOPedirCierre(actual, odometro.getOrNull()) }
    }

    fun confirmarCierreYAbrir() {
        _estado.update { it.copy(porCerrar = null) }
        viewModelScope.launch { crear(OdometroTexto.leer(_estado.value.odometro).getOrNull()) }
    }

    fun cancelarCierre() = _estado.update { it.copy(porCerrar = null) }

    private suspend fun cargar() {
        val actual = entorno.vehiculo.first()
        vehiculo = actual
        val bases = actual?.let { historial.recientesAntesDe(it.id, entorno.ahora(), MAX_BASES) }.orEmpty()
        val km = entorno.odometroEcuKm()
        _estado.update {
            it.copy(
                cargando = false,
                vehiculo = actual?.nombre,
                bases = bases.map { chequeo -> OpcionesChequeoBase.de(chequeo, entorno.zona) },
                baseElegida = bases.firstOrNull()?.id,
                odometro = km?.let(OdometroTexto::escribir).orEmpty(),
                odometroDeEcu = km != null,
            )
        }
    }

    private suspend fun abrirOPedirCierre(actual: VehiculoTaller, odometroKm: Double?) {
        val anterior = repositorio.sesionAbierta(actual.id)
        if (anterior == null) {
            crear(odometroKm)
            return
        }
        _estado.update {
            it.copy(porCerrar = SesionPorCerrar(anterior.titulo, FechasTaller.fechaHora(anterior.inicio, entorno.zona)))
        }
    }

    private suspend fun crear(odometroKm: Double?) {
        _estado.update { it.copy(abriendo = true, error = null) }
        registro.abrirSesion(solicitud(odometroKm))
            .onSuccess { abiertas.send(it.id) }
            .onFailure { e -> _estado.update { it.copy(error = e.message ?: "No se pudo abrir la sesión") } }
        _estado.update { it.copy(abriendo = false) }
    }

    private fun solicitud(odometroKm: Double?): SolicitudSesion {
        val e = _estado.value
        return SolicitudSesion(
            sintomas = e.sintomas,
            sintomasTexto = e.sintomasTexto,
            notas = e.notas,
            odometroKm = odometroKm,
            chequeoBase = e.baseElegida?.let(ChequeoBase::Elegido) ?: ChequeoBase.Ninguno,
        )
    }

    private companion object {
        const val MAX_BASES = 5
    }
}
