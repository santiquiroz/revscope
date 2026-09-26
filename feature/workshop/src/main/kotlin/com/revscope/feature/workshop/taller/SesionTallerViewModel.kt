package com.revscope.feature.workshop.taller

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.sesion.AnalizadorSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SesionTallerViewModel @Inject constructor(
    guardado: SavedStateHandle,
    private val repositorio: TallerRepository,
    private val registro: RegistroTaller,
    private val analizador: AnalizadorSesion,
    private val entorno: EntornoTaller,
) : ViewModel() {

    private val sesionId: Long = checkNotNull(guardado.get<Long>(ARG_SESION)) { "Falta el argumento $ARG_SESION" }
    private val ui = MutableStateFlow(UiSesion())
    private val salidas = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = salidas.receiveAsFlow()

    val estado: StateFlow<SesionTallerEstado> = combine(datos(), ui) { datos, ui ->
        datos?.let { EstadoPantallaSesion.de(it, ui, entorno.zona) } ?: SesionTallerEstado(cargando = false, eliminada = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(DETENER_TRAS_MS), SesionTallerEstado())

    fun alternarComparacion() = ui.update { it.copy(comparacionExpandida = !it.comparacionExpandida) }

    fun alternarEvento(id: Long) = ui.update {
        it.copy(eventosAbiertos = if (id in it.eventosAbiertos) it.eventosAbiertos - id else it.eventosAbiertos + id)
    }

    fun mostrarHojaAgregar(visible: Boolean) = ui.update { it.copy(hojaAgregar = visible) }

    fun mostrarMenu(visible: Boolean) = ui.update { it.copy(menu = visible) }

    fun pedirCierre() = abrirDialogo(DialogoSesion.CERRAR)

    fun pedirEliminacion() = abrirDialogo(DialogoSesion.ELIMINAR)

    fun pedirNota() = abrirDialogo(DialogoSesion.NOTA)

    fun cambiarNota(texto: String) = ui.update { it.copy(nota = texto) }

    fun cancelarDialogo() = ui.update { it.copy(dialogo = null) }

    fun mensajeMostrado() = ui.update { it.copy(mensaje = null) }

    fun confirmarCierre() = accion {
        val cerrada = registro.cerrarSesion(sesionId)
        avisar(if (cerrada) "Sesión cerrada: las lecturas nuevas ya no se anotan en ella" else "La sesión ya estaba cerrada")
    }

    fun confirmarEliminacion() = accion {
        if (registro.eliminarSesion(sesionId)) salidas.send(Unit) else avisar("No se pudo eliminar la sesión")
    }

    fun guardarNota() = accion {
        val texto = ui.value.nota
        if (!sePuedeAgregar() || texto.isBlank()) return@accion
        val guardada = registro.anotarNota(texto) != null
        if (guardada) ui.update { it.copy(nota = "") }
        avisar(if (guardada) "Nota agregada a la sesión" else "No se pudo guardar la nota")
    }

    fun tomarInstantanea() = accion {
        if (!sePuedeAgregar()) return@accion
        val guardada = registro.anotarInstantanea(entorno.lecturas()) != null
        avisar(if (guardada) "Instantánea de sensores agregada" else "No se pudo guardar la instantánea")
    }

    private fun abrirDialogo(dialogo: DialogoSesion) = ui.update { it.copy(dialogo = dialogo, menu = false, hojaAgregar = false) }

    private fun accion(bloque: suspend () -> Unit) {
        ui.update { it.copy(dialogo = null, hojaAgregar = false, menu = false) }
        viewModelScope.launch { bloque() }
    }

    private fun avisar(mensaje: String) = ui.update { it.copy(mensaje = mensaje) }

    private suspend fun sePuedeAgregar(): Boolean {
        val sesion = repositorio.sesion(sesionId) ?: return false
        val activo = registro.sesionAbierta()
        return sesion.abierta && activo?.id == sesionId
    }

    private fun datos(): Flow<DatosSesion?> =
        combine(sesionObservada(), repositorio.observarEventos(sesionId), entorno.vehiculo, entorno.conexion, ::Entrada)
            .mapLatest { it.analizar() }

    // Se observa la lista del vehículo porque el repositorio no expone una sesión sola como Flow: al cerrarla o
    // eliminarla, la pantalla se entera sin volver a consultar.
    private fun sesionObservada(): Flow<SesionTaller?> =
        flow { emit(repositorio.sesion(sesionId)) }.flatMapLatest { sesion ->
            if (sesion == null) flowOf(null) else sesionEnSuVehiculo(sesion.vehiculoId)
        }

    private fun sesionEnSuVehiculo(vehiculoId: Long): Flow<SesionTaller?> =
        repositorio.observarSesiones(vehiculoId).map { lista -> lista.firstOrNull { it.id == sesionId } }

    private suspend fun Entrada.analizar(): DatosSesion? {
        val actual = sesion ?: return null
        val tipo = vehiculo?.takeIf { it.id == actual.vehiculoId }?.tipo ?: VehicleType.MOTORCYCLE
        val analisis = analizador.analizar(actual, eventos, tipo)
        return DatosSesion(actual, eventos, analisis, vehiculo, conexion is ConnectionState.Connected)
    }

    private data class Entrada(
        val sesion: SesionTaller?,
        val eventos: List<EventoTaller>,
        val vehiculo: VehiculoTaller?,
        val conexion: ConnectionState,
    )

    companion object {
        const val ARG_SESION = "sesionId"
        private const val DETENER_TRAS_MS = 5_000L
    }
}
