package com.revscope.feature.workshop.taller.modelo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.modelo.CableModelo
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.ResolutorPlantilla
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.feature.workshop.taller.EntornoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ConocimientoModeloViewModel @Inject constructor(
    private val repositorio: TallerRepository,
    entorno: EntornoTaller,
) : ViewModel() {

    private val local = MutableStateFlow(ConocimientoModeloUi())
    val estado: StateFlow<ConocimientoModeloUi> = local.asStateFlow()
    private var vehiculoActual: VehiculoTaller? = null
    private var versionCarga = 0L

    init {
        viewModelScope.launch {
            entorno.vehiculo.collectLatest { vehiculo ->
                vehiculoActual = vehiculo
                cargar(vehiculo)
            }
        }
    }

    fun reintentar() = viewModelScope.launch { cargar(vehiculoActual) }

    fun editarCable(sensor: String, funcion: FuncionCable) {
        val cableado = local.value.modelo?.cableado?.firstOrNull { it.sensor == sensor } ?: return
        val cable = cableado.cables.firstOrNull { it.funcion == funcion } ?: return
        local.update {
            it.copy(dialogoCable = EdicionCableUi(sensor, funcion, funcion.etiqueta, cable.color.orEmpty()))
        }
    }

    fun cambiarColor(color: String) = local.update { estado ->
        estado.copy(dialogoCable = estado.dialogoCable?.copy(color = color.take(MAX_COLOR)))
    }

    fun cerrarDialogo() = local.update { it.copy(dialogoCable = null) }

    fun guardarColor() {
        val modelo = local.value.modelo ?: return
        val edicion = local.value.dialogoCable ?: return
        val cableado = modelo.cableado.firstOrNull { it.sensor == edicion.sensor } ?: return
        val actualizado = cableado.conColor(edicion.funcion, edicion.color.trim().takeIf(String::isNotEmpty))
        viewModelScope.launch {
            runCatching { repositorio.guardarCableado(modelo.clave, actualizado) }
                .onSuccess { guardado ->
                    if (guardado) {
                        local.update { it.copy(dialogoCable = null, mensaje = "Cableado guardado") }
                        cargar(vehiculoActual, conservarMensaje = true)
                    } else {
                        local.update { it.copy(mensaje = "No se pudo guardar el cableado") }
                    }
                }
                .onFailure(::falloGuardado)
        }
    }

    fun mensajeMostrado() = local.update { it.copy(mensaje = null) }

    fun referenciaCopiada(referencia: String) =
        local.update { it.copy(mensaje = "Referencia $referencia copiada") }

    private suspend fun cargar(vehiculo: VehiculoTaller?, conservarMensaje: Boolean = false) {
        val version = ++versionCarga
        val mensaje = local.value.mensaje.takeIf { conservarMensaje }
        local.value = ConocimientoModeloUi(vehiculo = vehiculo?.nombre, cargando = true, mensaje = mensaje)
        val claveModelo = vehiculo?.claveModelo
        if (claveModelo == null) {
            local.value = local.value.copy(cargando = false)
            return
        }
        try {
            val modelo = repositorio.conocimiento(claveModelo)
            if (version != versionCarga) return
            local.value = local.value.copy(modelo = modelo, cargando = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (version != versionCarga) return
            Timber.w(e, "Conocimiento del modelo: no se pudo cargar")
            local.value = local.value.copy(cargando = false, error = "No se pudo cargar el conocimiento del modelo")
        }
    }

    private fun falloGuardado(error: Throwable) {
        if (error is CancellationException) throw error
        Timber.w(error, "Conocimiento del modelo: no se pudo guardar el cableado")
        local.update { it.copy(mensaje = "No se pudo guardar el cableado") }
    }

    private fun CableadoSensor.conColor(funcion: FuncionCable, color: String?) = copy(
        cables = cables.map { cable ->
            if (cable.funcion == funcion) CableModelo(funcion, color) else cable
        },
        fuente = ResolutorPlantilla.FUENTE_EDITADA,
    )

    companion object {
        private const val MAX_COLOR = 40
    }
}
