package com.revscope.feature.workshop.taller.prueba

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.FuenteVref
import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.ContextoPrueba
import com.revscope.core.obd.taller.pruebas.ControladorPruebaGuiada
import com.revscope.core.obd.taller.pruebas.EnlacePrueba
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.OpcionesPrueba
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import com.revscope.feature.workshop.taller.EntornoTaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

internal data class LocalPrueba(
    val tipo: TipoPrueba? = null,
    val voz: Boolean = true,
    val dialogo: DialogoPrueba? = null,
    val ocupado: Boolean = false,
    val mensaje: String? = null,
    val vref: EstadoVref = EstadoVref(ReferenciaVoltaje.TIPICA, null, null),
    val precondiciones: List<ResultadoPrecondicion>? = null,
    val serie: SerieVivo = emptyList(),
    val bandas: Map<String, BandaReferencia> = emptyMap(),
)

// La pantalla de la prueba guiada sobre el controlador compartido con el MCP: una prueba a la vez.
@HiltViewModel
class PruebaGuiadaViewModel @Inject constructor(
    guardado: SavedStateHandle,
    private val controlador: ControladorPruebaGuiada,
    private val enlace: EnlacePrueba,
    private val entorno: EntornoTaller,
    private val fuenteVref: FuenteVref,
    private val fuenteSerie: FuenteSerieVivo,
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val registro: RegistroTaller,
) : ViewModel() {

    private val tipoDeArgumento = guardado.get<String>(ARG_TIPO)?.let { nombre -> TipoPrueba.entries.firstOrNull { it.name == nombre } }
    private val pantalla = PantallaPrueba(CatalogoPruebas::definicion, CatalogoPruebas.disponibles)
    private val local = MutableStateFlow(LocalPrueba(tipo = tipoDeArgumento))
    private val salidas = Channel<Unit>(Channel.BUFFERED)
    val salir: Flow<Unit> = salidas.receiveAsFlow()

    val estado: StateFlow<PruebaGuiadaUi> = combine(controlador.estado, local, entorno.vehiculo) { e, l, v ->
        PruebaGuiadaUi(
            fase = pantalla.fase(EntradaPantalla(e, l.tipo, l.precondiciones, l.serie, l.vref.usada, l.bandas)),
            vehiculo = v?.nombre,
            voz = l.voz,
            referencia = l.vref,
            dialogo = l.dialogo,
            ocupado = l.ocupado,
            mensaje = l.mensaje,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(DETENER_TRAS_MS), PruebaGuiadaUi(FasePantalla.Elegir(emptyList())))

    init {
        viewModelScope.launch { cargarReferencias() }
        viewModelScope.launch { refrescarSerieDuranteLaPrueba() }
        viewModelScope.launch { vigilarPrecondiciones() }
    }

    fun elegir(tipo: TipoPrueba) = local.update { it.copy(tipo = tipo, precondiciones = null) }

    fun empezar() = accion {
        val tipo = local.value.tipo ?: return@accion
        val opciones = OpcionesPrueba(voz = local.value.voz, vref = local.value.vref.usada)
        controlador.iniciar(tipo, opciones).onFailure { avisar(it.message) }
    }

    fun avanzar() = accion { controlador.avanzar().onFailure { avisar(it.message) } }

    fun repetirPaso() = accion { controlador.repetirPaso().onFailure { avisar(it.message) } }

    fun cambiarVoz(activa: Boolean) {
        local.update { it.copy(voz = activa) }
        viewModelScope.launch { controlador.cambiarVoz(activa) }
    }

    fun pedirCancelar() {
        val paso = (estado.value.fase as? FasePantalla.Paso)?.paso
        if (paso?.confirmarAlCancelar == true) return local.update { it.copy(dialogo = DialogoPrueba.CANCELAR) }
        viewModelScope.launch { controlador.cancelar() }
    }

    fun confirmarCancelar() {
        cerrarDialogo()
        viewModelScope.launch { controlador.cancelar() }
    }

    fun cerrarDialogo() = local.update { it.copy(dialogo = null) }

    fun pedirVref() = local.update { it.copy(dialogo = DialogoPrueba.VREF) }

    fun guardarVref(voltios: Double?) {
        cerrarDialogo()
        viewModelScope.launch {
            fuenteVref.guardar(voltios)
            cargarVref()
        }
    }

    fun reintentar() = accion { controlador.reintentar().onFailure { avisar(it.message) } }

    // Salir sin cancelar nada: la flecha o «Listo» solo cierran lo que ya terminó.
    fun volver() {
        if (estado.value.fase is FasePantalla.Preparacion && tipoDeArgumento == null) {
            viewModelScope.launch { controlador.cerrar() }
            return local.update { it.copy(tipo = null, precondiciones = null) }
        }
        terminar()
    }

    fun terminar() {
        viewModelScope.launch {
            controlador.cerrar()
            salidas.send(Unit)
        }
    }

    fun atras() = if (estado.value.enCurso) pedirCancelar() else volver()

    fun guardarEnSesion() = accion {
        val tipo = estado.value.fase.tipo ?: return@accion
        if (registro.sesionAbierta() == null) {
            registro.abrirSesion(SolicitudSesion(titulo = tipo.titulo)).onFailure { return@accion avisar(it.message) }
        }
        val id = controlador.guardarResultadoEnSesion()
        avisar(if (id != null) "Resultado guardado en una sesión nueva" else "No se pudo guardar el resultado")
    }

    fun mensajeMostrado() = local.update { it.copy(mensaje = null) }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private suspend fun cargarReferencias() {
        cargarVref()
        val actual = vehiculo.actual()
        val bandas = runCatching { repositorio.bandasResueltas(actual?.claveModelo, actual?.tipo ?: VehicleType.MOTORCYCLE) }
            .onFailure { Timber.w(it, "Prueba guiada: sin bandas del vehículo") }
            .getOrDefault(emptyMap())
        local.update { it.copy(bandas = bandas) }
    }

    private suspend fun cargarVref() {
        runCatching { fuenteVref.actual() }
            .onSuccess { vref -> local.update { it.copy(vref = vref) } }
            .onFailure { Timber.w(it, "Prueba guiada: no se pudo leer la referencia") }
    }

    private suspend fun refrescarSerieDuranteLaPrueba() {
        controlador.estado.collectLatest { e ->
            if (e !is EstadoPrueba.EnPaso) return@collectLatest
            val pid = pantalla.pidVivo(e)
            while (true) {
                local.update { it.copy(serie = fuenteSerie.leer(pid)) }
                delay(REFRESCO_SERIE_MS)
            }
        }
    }

    // Se evalúan en vivo mientras se prepara: al apagar el motor, la precondición se marca sola.
    private suspend fun vigilarPrecondiciones() {
        combine(controlador.estado, local) { e, l -> l.tipo.takeIf { preparando(e) } }
            .distinctUntilChanged()
            .collectLatest { tipo ->
                val definicion = tipo?.let(CatalogoPruebas::definicion) ?: return@collectLatest
                while (true) {
                    val ctx = ContextoPrueba(enlace.conectado(), enlace.lecturas(), enlace::soportado, entorno.ahora())
                    local.update { it.copy(precondiciones = definicion.precondiciones.map { p -> p.evaluar(ctx) }) }
                    delay(REFRESCO_PRECONDICIONES_MS)
                }
            }
    }

    private fun preparando(e: EstadoPrueba) = e is EstadoPrueba.Inactiva || e is EstadoPrueba.Verificando

    private fun accion(bloque: suspend () -> Unit) {
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

    private fun avisar(mensaje: String?) = local.update { it.copy(mensaje = mensaje ?: "Algo salió mal") }

    companion object {
        const val ARG_TIPO = "tipo"
        private const val DETENER_TRAS_MS = 5_000L
        private const val REFRESCO_SERIE_MS = 150L
        private const val REFRESCO_PRECONDICIONES_MS = 1_000L
    }
}
