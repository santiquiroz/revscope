package com.revscope.feature.workshop.taller.multimetro

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.obd.taller.multimetro.AsistenteMultimetro
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.multimetro.VeredictoMultimetro
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MultimetroViewModel @Inject constructor(
    guardado: SavedStateHandle,
    private val asistente: AsistenteMultimetro,
    private val registro: RegistroTaller,
) : ViewModel() {

    private val inicial = guardado.get<String>(ARG_SENSOR)
        ?.let { nombre -> SensorMultimetro.entries.firstOrNull { it.name == nombre } } ?: SensorMultimetro.TPS

    private val local = MutableStateFlow(LocalMultimetro(sensor = inicial))

    val estado: StateFlow<MultimetroUi> = local.map(MapeoMultimetro::ui)
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapeoMultimetro.ui(local.value))

    init {
        cargar(inicial)
    }

    fun elegirSensor(sensor: SensorMultimetro) {
        if (sensor == local.value.sensor && local.value.contexto != null) return
        local.update { LocalMultimetro(sensor = sensor, mensaje = it.mensaje) }
        cargar(sensor)
    }

    fun reintentar() = cargar(local.value.sensor)

    fun cambiarValor(funcion: FuncionCable, condicion: String, texto: String) =
        local.update { it.copy(textos = it.textos + ((funcion to condicion) to texto.take(MAX_CARACTERES))) }

    fun pedirColor(funcion: FuncionCable) {
        val cable = local.value.contexto?.plantilla?.cable(funcion) ?: return
        local.update { it.copy(dialogoColor = DialogoColorUi(funcion, cable.etiqueta, it.colores[funcion].orEmpty())) }
    }

    fun cambiarColor(texto: String) = local.update { l -> l.copy(dialogoColor = l.dialogoColor?.copy(texto = texto.take(MAX_COLOR))) }

    fun aceptarColor() = local.update { l ->
        val d = l.dialogoColor ?: return@update l
        l.copy(colores = l.colores + (d.funcion to d.texto.trim().takeIf(String::isNotEmpty)), dialogoColor = null)
    }

    fun cerrarDialogo() = local.update { it.copy(dialogoColor = null) }

    fun mensajeMostrado() = local.update { it.copy(mensaje = null) }

    fun guardarEnSesion() = trabajar {
        val l = local.value
        val ctx = l.contexto ?: return@trabajar
        val plantilla = MapeoMultimetro.plantillaEfectiva(ctx, l.colores)
        val lecturas = MapeoMultimetro.lecturas(plantilla, l.textos)
        if (lecturas.isEmpty()) return@trabajar avisar("Escribe al menos una medida para guardarla")
        if (ctx.sesion == null) {
            registro.abrirSesion(SolicitudSesion(titulo = "Multímetro: ${plantilla.titulo}"))
                .onFailure { return@trabajar avisar(it.message ?: "No se pudo abrir la sesión") }
        }
        val id = asistente.guardarTabla(plantilla, VeredictoMultimetro.combinar(plantilla, lecturas, ctx.bandas, ctx.ecu))
        avisar(if (id != null) "Medición guardada en la sesión" else "No se pudo guardar la medición")
        recargarContexto()
    }

    fun guardarColores() = trabajar {
        val l = local.value
        val ctx = l.contexto ?: return@trabajar
        val nombre = ctx.modelo?.nombre ?: return@trabajar avisar("Elige el modelo de referencia del vehículo en Perfiles")
        val ok = asistente.guardarColores(ctx.plantilla, l.colores)
        avisar(if (ok) "Colores guardados en la plantilla de $nombre" else "No se pudieron guardar los colores")
        if (ok) recargarContexto()
    }

    private fun cargar(sensor: SensorMultimetro) {
        local.update { it.copy(cargando = true, error = null) }
        viewModelScope.launch {
            runCatching { asistente.contexto(sensor) }
                .onSuccess { ctx ->
                    local.update { l ->
                        if (l.sensor != sensor) l else l.copy(contexto = ctx, cargando = false, colores = ctx.plantilla.colores())
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    Timber.w(e, "Multímetro: no se pudo cargar la plantilla")
                    local.update { it.copy(cargando = false, error = "No se pudo cargar la plantilla del vehículo") }
                }
        }
    }

    // Tras guardar cambia la sesión o los colores del modelo; lo escrito se conserva.
    private suspend fun recargarContexto() {
        val sensor = local.value.sensor
        runCatching { asistente.contexto(sensor) }
            .onSuccess { ctx -> local.update { l -> if (l.sensor != sensor) l else l.copy(contexto = ctx) } }
            .onFailure { Timber.w(it, "Multímetro: no se pudo recargar") }
    }

    private fun trabajar(bloque: suspend () -> Unit) {
        if (local.value.ocupado) return
        local.update { it.copy(ocupado = true) }
        viewModelScope.launch {
            try {
                bloque()
            } finally {
                local.update { it.copy(ocupado = false) }
            }
        }
    }

    private fun avisar(texto: String) = local.update { it.copy(mensaje = texto) }

    companion object {
        const val ARG_SENSOR = "sensor"
        private const val MAX_CARACTERES = 12
        private const val MAX_COLOR = 40
    }
}
