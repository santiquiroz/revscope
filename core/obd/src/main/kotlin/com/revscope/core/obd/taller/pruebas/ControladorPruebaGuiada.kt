package com.revscope.core.obd.taller.pruebas

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.telemetry.captura.CapturaRapida
import com.revscope.core.obd.telemetry.captura.ConfigCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class OpcionesPrueba(
    val voz: Boolean = true,
    val origen: OrigenEvento = OrigenEvento.APP,
    val vref: ReferenciaVoltaje = ReferenciaVoltaje.TIPICA,
)

// Una prueba guiada a la vez, compartida por la UI y el MCP, sobre la captura rápida: los límites de cada
// paso se marcan con el reloj de la captura, así los segmentos caen exactos sobre las muestras.
@Singleton
class ControladorPruebaGuiada(
    private val captura: CapturaPrueba,
    private val enlace: EnlacePrueba,
    private val registro: RegistroTaller,
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val anunciador: AnunciadorTaller,
    private val scope: CoroutineScope,
    private val relojMs: () -> Long,
    private val definiciones: (TipoPrueba) -> DefinicionPrueba? = CatalogoPruebas::definicion,
    private val rafagaVoltaje: CapturaPrueba? = null,
    private val fuentes: FuentesAnalisis = FuentesAnalisis.NINGUNA,
) {

    @Inject
    constructor(
        captura: CapturaPrueba,
        enlace: EnlacePrueba,
        registro: RegistroTaller,
        repositorio: TallerRepository,
        vehiculo: VehiculoActivo,
        anunciador: AnunciadorTaller,
        @RafagaVoltaje rafagaVoltaje: CapturaPrueba,
        fuentes: FuentesAnalisis,
    ) : this(
        captura, enlace, registro, repositorio, vehiculo, anunciador,
        CoroutineScope(SupervisorJob() + Dispatchers.Default), System::currentTimeMillis,
        CatalogoPruebas::definicion, rafagaVoltaje, fuentes,
    )

    private class Ejecucion(
        val id: String,
        val definicion: DefinicionPrueba,
        val opciones: OpcionesPrueba,
        val inicioEpochMs: Long,
        val captura: CapturaPrueba,
    ) {
        var progreso = Progreso(0, FasePaso.POSICIONANDO, 0)
        var ultimoT = 0L
        var voz = opciones.voz
        val tipo: TipoPrueba get() = definicion.tipo
        val paso: PasoPrueba get() = definicion.pasos[progreso.indice]
    }

    private data class Progreso(
        val indice: Int,
        val fase: FasePaso,
        val faseDesdeMs: Long,
        val segmentos: List<SegmentoPaso> = emptyList(),
    )

    private val candado = Mutex()
    private var ejecucion: Ejecucion? = null
    private var ultimaSolicitud: Pair<TipoPrueba, OpcionesPrueba>? = null
    private var ultimoEvento: NuevoEvento? = null

    private val _estado = MutableStateFlow<EstadoPrueba>(EstadoPrueba.Inactiva)
    val estado: StateFlow<EstadoPrueba> = _estado.asStateFlow()

    @Volatile var idPrueba: String? = null
        private set

    fun enCurso(): Boolean = estado.value.enCurso

    suspend fun iniciar(tipo: TipoPrueba, opciones: OpcionesPrueba = OpcionesPrueba()): Result<EstadoPrueba> =
        candado.withLock {
            rechazoAlIniciar(tipo)?.let { return@withLock Result.failure(IllegalStateException(it)) }
            val definicion = checkNotNull(definiciones(tipo))
            ultimaSolicitud = tipo to opciones
            val verificacion = EstadoPrueba.Verificando(tipo, definicion.precondiciones.map { it.evaluar(contexto()) })
            if (!verificacion.listas) return@withLock Result.success(publicar(verificacion))
            Result.success(arrancar(definicion, opciones))
        }

    suspend fun reintentar(): Result<EstadoPrueba> {
        val (tipo, opciones) = ultimaSolicitud ?: return fallo("No hay una prueba anterior que repetir")
        return iniciar(tipo, opciones)
    }

    // «Listo» en un paso sostenido; «Terminar» en uno que graba hasta que el técnico diga.
    suspend fun avanzar(): Result<EstadoPrueba> = candado.withLock {
        val e = ejecucion ?: return@withLock fallo(SIN_PRUEBA)
        val t = e.captura.transcurridoMs() ?: return@withLock fallo(SIN_CAPTURA)
        when {
            e.progreso.fase == FasePaso.POSICIONANDO -> empezarASostener(e, t)
            e.paso.modo.terminaConToque -> cerrarPaso(e, t)
            else -> return@withLock fallo("Este paso graba solo: espera a que termine la cuenta")
        }
        Result.success(estado.value)
    }

    suspend fun repetirPaso(): Result<EstadoPrueba> = candado.withLock {
        val e = ejecucion ?: return@withLock fallo(SIN_PRUEBA)
        val t = e.captura.transcurridoMs() ?: return@withLock fallo(SIN_CAPTURA)
        e.progreso = inicioPaso(e, e.progreso.indice, t, e.progreso.segmentos)
        anunciarPaso(e)
        Result.success(publicarPaso(e, t))
    }

    suspend fun cancelar(motivo: String = MOTIVO_USUARIO): EstadoPrueba = candado.withLock {
        val e = ejecucion ?: return@withLock descartarSinEjecucion()
        terminarPor(e, EstadoPrueba.Cancelada(e.tipo, motivo))
    }

    // Con las manos en el acelerador la voz ayuda; en un taller ruidoso o de noche, se apaga a mitad de prueba.
    suspend fun cambiarVoz(activa: Boolean) = candado.withLock {
        ejecucion?.voz = activa
        ultimaSolicitud = ultimaSolicitud?.let { (tipo, opciones) -> tipo to opciones.copy(voz = activa) }
    }

    // Vuelve a Inactiva tras leer el resultado, la cancelación o las precondiciones.
    suspend fun cerrar(): EstadoPrueba = candado.withLock { descartarSinEjecucion() }

    // Sin sesión abierta al terminar: el técnico abre una y guarda ahí el último resultado.
    suspend fun guardarResultadoEnSesion(): Long? = candado.withLock {
        val terminada = estado.value as? EstadoPrueba.Terminada ?: return@withLock null
        terminada.eventoId?.let { return@withLock it }
        val evento = ultimoEvento ?: return@withLock null
        registro.anotar(evento)?.also { publicar(terminada.copy(eventoId = it)) }
    }

    // ── Arranque ────────────────────────────────────────────────────────────

    private fun rechazoAlIniciar(tipo: TipoPrueba): String? {
        val definicion = definiciones(tipo)
        return when {
            ejecucion != null -> "Ya hay una prueba guiada en curso: termínala o cancélala"
            captura.activa() || rafagaVoltaje?.activa() == true -> "Detén la captura rápida primero"
            definicion == null || capturaPara(definicion) == null -> "La prueba «${tipo.titulo}» todavía no está disponible"
            else -> null
        }
    }

    private fun capturaPara(definicion: DefinicionPrueba): CapturaPrueba? = when (definicion.fuente) {
        FuenteMuestras.PIDS -> captura
        FuenteMuestras.VOLTAJE_ADAPTADOR -> rafagaVoltaje
    }

    private fun pidsPara(definicion: DefinicionPrueba): List<String> = when (definicion.fuente) {
        FuenteMuestras.PIDS -> definicion.pids + definicion.pidsOpcionales.filter(enlace::soportado)
        FuenteMuestras.VOLTAJE_ADAPTADOR -> listOf(AnalizadorBateria.PID_VOLTAJE)
    }

    private suspend fun arrancar(definicion: DefinicionPrueba, opciones: OpcionesPrueba): EstadoPrueba {
        val c = checkNotNull(capturaPara(definicion))
        val config = ConfigCaptura(pidsPara(definicion), definicion.duracionMaximaMs, vigilar = listOf(PID_VELOCIDAD), guiada = true)
        c.iniciar(config).onFailure {
            return publicar(EstadoPrueba.Fallida(definicion.tipo, "No se pudo iniciar la captura: ${it.message}", reintentable = true))
        }
        val ahora = relojMs()
        val e = Ejecucion("prueba-$ahora", definicion, opciones, ahora, c)
        ejecucion = e
        idPrueba = e.id
        ultimoEvento = null
        val t = c.transcurridoMs() ?: 0L
        e.progreso = inicioPaso(e, 0, t, emptyList())
        anunciarPaso(e)
        lanzarBucle(e)
        return publicarPaso(e, t)
    }

    private fun lanzarBucle(e: Ejecucion) {
        scope.launch {
            while (true) {
                delay(TICK_MS)
                val sigue = candado.withLock {
                    if (ejecucion === e) evaluar(e)
                    ejecucion === e
                }
                if (!sigue) break
            }
        }
    }

    // ── Cada tick ───────────────────────────────────────────────────────────

    private suspend fun evaluar(e: Ejecucion) {
        interrupcion(e)?.let {
            if (seAnalizaElCorte(e, it)) analizarCorte(e) else terminarPor(e, it)
            return
        }
        val t = e.captura.transcurridoMs() ?: return
        e.ultimoT = t
        if (pasoCumplido(e, t)) cerrarPaso(e, t) else publicarPaso(e, t)
    }

    private fun interrupcion(e: Ejecucion): EstadoPrueba? {
        if (!enlace.conectado()) return EstadoPrueba.Fallida(e.tipo, MOTIVO_ENLACE_PERDIDO, reintentable = true)
        if (!e.captura.activa()) return porFinDeCaptura(e.tipo, e.captura.ultimoResumen.value?.motivoFin)
        val kmh = velocidadDesde(e.inicioEpochMs)?.takeIf { it > 0.0 } ?: return null
        return EstadoPrueba.Cancelada(
            e.tipo,
            "Moto en movimiento (${FormatoTaller.numero(kmh, 0)} km/h): la prueba se hace con la moto detenida",
        )
    }

    private fun porFinDeCaptura(tipo: TipoPrueba, motivo: String?): EstadoPrueba = when {
        motivo == null || motivo.startsWith(CapturaRapida.MOTIVO_ENLACE) ->
            EstadoPrueba.Fallida(tipo, MOTIVO_ENLACE_PERDIDO, reintentable = true)
        motivo.startsWith(PREFIJO_DETENIDA) ->
            EstadoPrueba.Cancelada(tipo, "Se detuvo la captura rápida desde otra pantalla o el MCP")
        else -> EstadoPrueba.Fallida(tipo, "La captura se detuvo: $motivo", reintentable = true)
    }

    private fun velocidadDesde(desdeEpochMs: Long): Double? {
        val lecturas = enlace.lecturas()
        return listOf(PID_VELOCIDAD, ObdSessionManager.GPS_SPEED_PID)
            .mapNotNull { pid -> lecturas[pid]?.takeIf { it.timestamp >= desdeEpochMs }?.value }
            .maxOrNull()
    }

    private fun pasoCumplido(e: Ejecucion, t: Long): Boolean {
        val p = e.progreso
        if (p.fase == FasePaso.POSICIONANDO) return false
        if (t - p.faseDesdeMs >= e.paso.modo.limiteMs) return true
        val criterio = e.paso.terminarCuando ?: return false
        return criterio.cumplido(muestrasDesde(e, p.faseDesdeMs))
    }

    private fun muestrasDesde(e: Ejecucion, desdeMs: Long): List<MuestraCaptura> =
        e.captura.muestrasActuales().filter { it.tMicros / 1_000 >= desdeMs }

    // ── Pasos ───────────────────────────────────────────────────────────────

    private fun inicioPaso(e: Ejecucion, indice: Int, t: Long, segmentos: List<SegmentoPaso>): Progreso {
        val modo = e.definicion.pasos[indice].modo
        val fase = if (modo is ModoPaso.Sostener) FasePaso.POSICIONANDO else FasePaso.GRABANDO
        return Progreso(indice, fase, t, segmentos)
    }

    private fun empezarASostener(e: Ejecucion, t: Long) {
        e.progreso = e.progreso.copy(fase = FasePaso.SOSTENIENDO, faseDesdeMs = t)
        anunciar(e, "Sostén ${segundos(e.paso.modo.limiteMs)} segundos")
        publicarPaso(e, t)
    }

    private suspend fun cerrarPaso(e: Ejecucion, t: Long) {
        val p = e.progreso
        val segmentos = p.segmentos + SegmentoPaso(e.paso.clave, p.faseDesdeMs, t, e.paso.descartarInicioMs)
        val siguiente = p.indice + 1
        if (siguiente >= e.definicion.pasos.size) return finalizar(e, segmentos)
        e.progreso = inicioPaso(e, siguiente, t, segmentos)
        anunciarPaso(e)
        publicarPaso(e, t)
    }

    private fun publicarPaso(e: Ejecucion, t: Long): EstadoPrueba {
        val p = e.progreso
        val restante = if (p.fase == FasePaso.POSICIONANDO) null else (e.paso.modo.limiteMs - (t - p.faseDesdeMs)).coerceAtLeast(0)
        return publicar(EstadoPrueba.EnPaso(e.tipo, e.paso, p.indice, e.definicion.pasos.size, p.fase, restante))
    }

    // ── Cierre ──────────────────────────────────────────────────────────────

    // El enlace se cayó en un paso donde eso es un hallazgo: se cierra el paso donde llegó y se analiza.
    private fun seAnalizaElCorte(e: Ejecucion, final: EstadoPrueba): Boolean =
        final is EstadoPrueba.Fallida && final.motivo == MOTIVO_ENLACE_PERDIDO && e.paso.clave in e.definicion.analizarSiSeCortaEn &&
            e.progreso.fase != FasePaso.POSICIONANDO

    private suspend fun analizarCorte(e: Ejecucion) {
        val p = e.progreso
        val ultimaMuestraMs = e.captura.muestrasActuales().maxOfOrNull { it.tMicros / 1_000 } ?: 0L
        val fin = maxOf(e.ultimoT, ultimaMuestraMs, p.faseDesdeMs)
        finalizar(e, p.segmentos + SegmentoPaso(e.paso.clave, p.faseDesdeMs, fin, e.paso.descartarInicioMs), enlacePerdidoEn = e.paso.clave)
    }

    private suspend fun finalizar(e: Ejecucion, segmentos: List<SegmentoPaso>, enlacePerdidoEn: String? = null) {
        publicar(EstadoPrueba.Analizando(e.tipo))
        ejecucion = null
        val resumen = e.captura.detener(MOTIVO_FIN_CAPTURA) ?: e.captura.ultimoResumen.value
        val actual = vehiculo.actual()
        val datos = DatosPrueba(e.tipo, e.captura.muestrasActuales(), segmentos, e.opciones.vref, contextoAnalisis(e, actual, enlacePerdidoEn))
        val resultado = analizar(e.definicion, datos, bandas(actual))
            ?: return anotarFallida(e, EstadoPrueba.Fallida(e.tipo, "No se pudo analizar la prueba", true), segmentos, resumen?.rutaCsv)
        val evento = EventoPrueba.terminada(resultado, datos, resumen?.rutaCsv, e.opciones.origen)
        ultimoEvento = evento
        val eventoId = registro.anotar(evento)
        anunciar(e, "Prueba terminada. ${resultado.titulo}")
        publicar(EstadoPrueba.Terminada(resultado, eventoId, datos))
    }

    private suspend fun terminarPor(e: Ejecucion, final: EstadoPrueba): EstadoPrueba {
        ejecucion = null
        val resumen = e.captura.detener(MOTIVO_FIN_CAPTURA) ?: e.captura.ultimoResumen.value
        if (final is EstadoPrueba.Fallida) {
            anotarFallida(e, final, e.progreso.segmentos, resumen?.rutaCsv)
        } else {
            anunciar(e, "Prueba cancelada")
        }
        return publicar(final)
    }

    private suspend fun anotarFallida(e: Ejecucion, fallida: EstadoPrueba.Fallida, segmentos: List<SegmentoPaso>, rutaCsv: String?) {
        val datos = DatosPrueba(e.tipo, e.captura.muestrasActuales(), segmentos, e.opciones.vref)
        registro.anotar(EventoPrueba.sinTerminar(fallida, datos, e.definicion.pasos.size, rutaCsv, e.opciones.origen))
        anunciar(e, "La prueba no terminó. ${fallida.motivo}")
        publicar(fallida)
    }

    private fun analizar(definicion: DefinicionPrueba, datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba? =
        runCatching { definicion.analizar(datos, bandas) }
            .onFailure { Timber.w(it, "Prueba guiada: el analizador de ${definicion.tipo} falló") }
            .getOrNull()

    private suspend fun contextoAnalisis(e: Ejecucion, actual: VehiculoTaller?, enlacePerdidoEn: String?) = ContextoAnalisis(
        tipoVehiculo = actual?.tipo ?: VehicleType.MOTORCYCLE,
        ambiente = if (e.definicion.usaAmbiente) leerFuente("el ambiente del teléfono") { fuentes.ambiente() } else null,
        desfaseVoltaje = desfasePara(e.definicion),
        enlacePerdidoEn = enlacePerdidoEn,
    )

    private suspend fun desfasePara(definicion: DefinicionPrueba): DesfaseVoltaje {
        if (definicion.fuente != FuenteMuestras.VOLTAJE_ADAPTADOR) return DesfaseVoltaje.SIN_CALIBRAR
        return leerFuente("el desfase del voltaje") { fuentes.desfaseVoltaje() } ?: DesfaseVoltaje.SIN_CALIBRAR
    }

    // Sin barómetro, sin ubicación o sin preferencias, la prueba se analiza igual: el resultado lo dice.
    private suspend fun <T> leerFuente(que: String, leer: suspend () -> T): T? = try {
        leer()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Prueba guiada: no se pudo leer $que")
        null
    }

    private suspend fun bandas(actual: VehiculoTaller?): Map<String, BandaReferencia> {
        val tipo = actual?.tipo ?: VehicleType.MOTORCYCLE
        return try {
            repositorio.bandasResueltas(actual?.claveModelo, tipo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Prueba guiada: sin bandas del modelo, se usan las típicas")
            ResolutorBandas.resolverTodas(tipo, emptyList())
        }
    }

    // ── Apoyo ───────────────────────────────────────────────────────────────

    private fun descartarSinEjecucion(): EstadoPrueba =
        if (estado.value.enCurso) estado.value else publicar(EstadoPrueba.Inactiva)

    private fun contexto() = ContextoPrueba(enlace.conectado(), enlace.lecturas(), enlace::soportado, relojMs())

    private fun anunciarPaso(e: Ejecucion) {
        val p = e.progreso
        val cierre = if (p.fase == FasePaso.POSICIONANDO) {
            "Cuando esté en posición, toca Listo."
        } else {
            "Grabando ${segundos(e.paso.modo.limiteMs)} segundos."
        }
        anunciar(e, "Paso ${p.indice + 1} de ${e.definicion.pasos.size}. ${e.paso.titulo}. ${e.paso.instruccion}. $cierre")
    }

    private fun anunciar(e: Ejecucion, texto: String) {
        if (e.voz) runCatching { anunciador.anunciar(texto) }.onFailure { Timber.w(it, "Prueba guiada: sin voz") }
    }

    private fun publicar(nuevo: EstadoPrueba): EstadoPrueba {
        _estado.value = nuevo
        return nuevo
    }

    private fun segundos(ms: Long): Long = ms / 1_000

    private val ModoPaso.terminaConToque: Boolean
        get() = this is ModoPaso.GrabarHasta || this is ModoPaso.Accion

    private fun <T> fallo(mensaje: String): Result<T> = Result.failure(IllegalStateException(mensaje))

    companion object {
        const val TICK_MS = 100L
        const val PID_VELOCIDAD = ContextoPrueba.PID_VELOCIDAD
        const val MOTIVO_USUARIO = "Cancelada por el usuario"
        const val MOTIVO_ENLACE_PERDIDO = "Se perdió el enlace con el adaptador"
        const val MOTIVO_FIN_CAPTURA = "prueba guiada"
        private const val PREFIJO_DETENIDA = "detenida"
        private const val SIN_PRUEBA = "No hay una prueba guiada en curso"
        private const val SIN_CAPTURA = "La captura de la prueba no está activa"
    }
}
