package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.MetricasChequeo
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SolicitudSesion(
    val sintomas: Set<Sintoma> = emptySet(),
    val sintomasTexto: String = "",
    val notas: String = "",
    val titulo: String? = null,
    val odometroKm: Double? = null,
    val origen: OrigenEvento = OrigenEvento.APP,
    val chequeoBase: ChequeoBase = ChequeoBase.Ultimo,
)

sealed interface ChequeoBase {
    data object Ultimo : ChequeoBase
    data object Ninguno : ChequeoBase
    data class Elegido(val id: Long) : ChequeoBase
}

// Fachada de escritura del Taller: sin sesión abierta del vehículo activo no escribe nada, y un fallo
// al anotar nunca rompe la lectura, el borrado o la captura que lo originó.
@Singleton
class RegistroTaller(
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val historial: HistorialChequeos,
    private val adjuntos: AdjuntosTaller,
    private val registry: PidRegistry,
    private val reloj: () -> Long,
) {

    @Inject
    constructor(
        repositorio: TallerRepository,
        vehiculo: VehiculoActivo,
        historial: HistorialChequeos,
        adjuntos: AdjuntosTaller,
        registry: PidRegistry,
    ) : this(repositorio, vehiculo, historial, adjuntos, registry, System::currentTimeMillis)

    suspend fun sesionAbierta(): SesionTaller? = vehiculo.actual()?.let { repositorio.sesionAbierta(it.id) }

    suspend fun abrirSesion(solicitud: SolicitudSesion): Result<SesionTaller> {
        val actual = vehiculo.actual()
            ?: return Result.failure(IllegalStateException("Elige el vehículo activo antes de abrir una sesión"))
        val ahora = reloj()
        val sesion = SesionTaller(
            vehiculoId = actual.id,
            claveModelo = actual.claveModelo,
            inicio = ahora,
            titulo = TituloSesion.de(solicitud),
            sintomas = solicitud.sintomas,
            sintomasTexto = solicitud.sintomasTexto.trim(),
            notas = solicitud.notas.trim(),
            odometroKm = solicitud.odometroKm,
            chequeoBaseId = chequeoBaseId(solicitud.chequeoBase, actual.id, ahora),
        )
        val id = repositorio.abrirSesion(sesion)
        val abierta = sesion.copy(id = id)
        if (sesion.sintomas.isNotEmpty() || sesion.sintomasTexto.isNotEmpty()) {
            guardar(abierta, EventosTaller.sintomas(sesion.sintomas, sesion.sintomasTexto, solicitud.origen))
        }
        return Result.success(abierta)
    }

    suspend fun cerrarSesion(id: Long): Boolean = repositorio.cerrarSesion(id, reloj())

    suspend fun eliminarSesion(id: Long): Boolean {
        val eliminada = repositorio.eliminarSesion(id)
        if (eliminada) seguro { adjuntos.borrar(id) }
        return eliminada
    }

    suspend fun anotar(evento: NuevoEvento): Long? = seguro {
        val sesion = sesionAbierta() ?: return@seguro null
        guardar(sesion, evento)
    }

    suspend fun anotarLecturaDtc(scan: DtcScan, origen: OrigenEvento = OrigenEvento.APP): Long? =
        anotar(EventosTaller.lecturaDtc(scan, origen, ::nombrePid))

    suspend fun anotarBorradoDtc(borrado: BorradoDtc, origen: OrigenEvento = OrigenEvento.APP): Long? =
        anotar(EventosTaller.borradoDtc(borrado, origen, ::nombrePid))

    suspend fun anotarChequeo(
        chequeoId: Long?,
        items: List<DiagnosticRules.Diagnosis>,
        metricas: MetricasChequeo,
        dtc: DtcScan?,
    ): Long? = anotar(EventosTaller.chequeo(chequeoId, items, metricas, dtc, ::nombrePid))

    suspend fun anotarCaptura(resumen: ResumenCaptura, muestras: List<MuestraCaptura>): Long? =
        anotar(EventoCaptura.de(resumen, muestras, registry::getDefinition))

    suspend fun anotarNota(texto: String, origen: OrigenEvento = OrigenEvento.APP): Long? {
        if (texto.isBlank()) return null
        return anotar(EventosTaller.nota(texto, origen))
    }

    suspend fun anotarInstantanea(lecturas: Map<String, ObdReading>): Long? =
        anotar(EventosTaller.instantanea(lecturas, reloj(), ::nombrePid))

    suspend fun marcarPaso(clave: String, marcado: Boolean): SesionTaller? = seguro {
        val sesion = sesionAbierta() ?: return@seguro null
        val pasos = if (marcado) sesion.pasosMarcados + clave else sesion.pasosMarcados - clave
        sesion.copy(pasosMarcados = pasos).also { repositorio.actualizarSesion(it) }
    }

    private suspend fun chequeoBaseId(eleccion: ChequeoBase, vehiculoId: Long, ahora: Long): Long? = when (eleccion) {
        ChequeoBase.Ultimo -> historial.ultimoAntesDe(vehiculoId, ahora)?.id
        ChequeoBase.Ninguno -> null
        is ChequeoBase.Elegido -> eleccion.id
    }

    private suspend fun guardar(sesion: SesionTaller, evento: NuevoEvento): Long {
        val adjunto = evento.adjuntoOrigen?.let { adjuntos.copiar(sesion.id, File(it)) }
        return repositorio.agregarEvento(
            EventoTaller(
                sesionId = sesion.id,
                instante = reloj(),
                tipo = evento.tipo,
                origen = evento.origen,
                titulo = evento.titulo,
                resumen = evento.resumen,
                veredicto = evento.veredicto,
                payloadJson = LimitePayload.ajustar(conAdjunto(evento.payload, adjunto)),
                adjunto = adjunto,
            ),
        )
    }

    private fun conAdjunto(payload: JSONObject, adjunto: String?): JSONObject =
        if (adjunto == null) payload else JSONObject(payload.toString()).put("csvEnSesion", adjunto)

    private fun nombrePid(pid: String): String? = registry.getDefinition(pid)?.nameEs

    private suspend fun <T> seguro(bloque: suspend () -> T?): T? = try {
        bloque()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Taller: no se pudo anotar en la sesión")
        null
    }
}
