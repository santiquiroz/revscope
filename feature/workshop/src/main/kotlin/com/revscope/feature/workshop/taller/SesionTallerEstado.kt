package com.revscope.feature.workshop.taller

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.FilaComparacion
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.sesion.etiqueta
import com.revscope.feature.workshop.R
import java.io.File
import java.time.ZoneId

data class EventoUi(
    val id: Long,
    val hora: String,
    val tipo: TipoEvento,
    val titulo: String,
    val resumen: String,
    val veredicto: Veredicto,
    val detalle: List<String>,
)

data class PruebaSugeridaUi(val titulo: String, val motivo: String)

sealed interface TarjetaComparacion {
    data object SinBase : TarjetaComparacion
    data class SinChequeoAhora(val fechaBase: String) : TarjetaComparacion
    data class Filas(val fechaBase: String, val filas: List<FilaComparacion>) : TarjetaComparacion
}

sealed interface DisponibilidadAgregar {
    data object Disponible : DisponibilidadAgregar
    data object SesionCerrada : DisponibilidadAgregar
    data class OtroVehiculo(val mensaje: String) : DisponibilidadAgregar
}

enum class DialogoSesion { CERRAR, ELIMINAR, NOTA }

data class LoQueSeBorra(val eventos: Int, val adjuntos: Int)

data class UiSesion(
    val comparacionExpandida: Boolean = true,
    val eventosAbiertos: Set<Long> = emptySet(),
    val hojaAgregar: Boolean = false,
    val menu: Boolean = false,
    val dialogo: DialogoSesion? = null,
    val nota: String = "",
    val mensaje: String? = null,
)

data class SesionTallerEstado(
    val cargando: Boolean = true,
    val eliminada: Boolean = false,
    val titulo: String = "",
    val subtitulo: String = "",
    val abierta: Boolean = false,
    val estadoTexto: String = "",
    val sintomas: List<String> = emptyList(),
    val sintomasTexto: String = "",
    val notas: String = "",
    val odometro: String? = null,
    val codigos: List<String> = emptyList(),
    val comparacion: TarjetaComparacion = TarjetaComparacion.SinBase,
    val sugeridas: List<PruebaSugeridaUi> = emptyList(),
    val eventos: List<EventoUi> = emptyList(),
    val agregar: DisponibilidadAgregar = DisponibilidadAgregar.SesionCerrada,
    val adaptadorConectado: Boolean = false,
    val loQueSeBorra: LoQueSeBorra = LoQueSeBorra(0, 0),
    val ui: UiSesion = UiSesion(),
)

internal data class DatosSesion(
    val sesion: SesionTaller,
    val eventos: List<EventoTaller>,
    val analisis: AnalisisSesion,
    val vehiculoActivo: VehiculoTaller?,
    val conectado: Boolean,
)

internal object EstadoPantallaSesion {

    const val MAX_SUGERIDAS = 3

    fun de(datos: DatosSesion, ui: UiSesion, zona: ZoneId, resolver: StringResolver): SesionTallerEstado {
        val sesion = datos.sesion
        val ordenados = LineaDeTiempo.ordenar(datos.eventos)
        return SesionTallerEstado(
            cargando = false,
            titulo = sesion.titulo,
            subtitulo = subtitulo(sesion, datos.vehiculoActivo, zona, resolver),
            abierta = sesion.abierta,
            estadoTexto = estadoTexto(sesion, zona, resolver),
            sintomas = sesion.sintomas.sortedBy { it.ordinal }.map { it.etiqueta(resolver) },
            sintomasTexto = sesion.sintomasTexto,
            notas = sesion.notas,
            odometro = sesion.odometroKm?.let { resolver.get(R.string.workshop_session_odometer_value, Miles.agrupar(it)) },
            codigos = datos.analisis.codigos,
            comparacion = comparacion(datos.analisis, zona, resolver),
            sugeridas = datos.analisis.sugeridas.take(MAX_SUGERIDAS).map { PruebaSugeridaUi(it.accion.etiqueta(resolver), it.motivo) },
            eventos = ordenados.map { evento(it, sesion.inicio, zona, resolver) },
            agregar = disponibilidad(sesion, datos.vehiculoActivo, resolver),
            adaptadorConectado = datos.conectado,
            loQueSeBorra = LoQueSeBorra(datos.eventos.size, datos.eventos.count { it.adjunto != null }),
            ui = ui,
        )
    }

    fun disponibilidad(sesion: SesionTaller, activo: VehiculoTaller?, resolver: StringResolver): DisponibilidadAgregar = when {
        !sesion.abierta -> DisponibilidadAgregar.SesionCerrada
        activo?.id != sesion.vehiculoId -> DisponibilidadAgregar.OtroVehiculo(
            resolver.get(R.string.workshop_session_other_vehicle),
        )
        else -> DisponibilidadAgregar.Disponible
    }

    private fun subtitulo(sesion: SesionTaller, activo: VehiculoTaller?, zona: ZoneId, resolver: StringResolver): String {
        val fecha = FechasSesion.fecha(sesion.inicio, zona, resolver.locale)
        return if (activo?.id == sesion.vehiculoId) "${activo.nombre} · $fecha" else fecha
    }

    private fun estadoTexto(sesion: SesionTaller, zona: ZoneId, resolver: StringResolver): String {
        val cierre = sesion.cierre
            ?: return resolver.get(R.string.workshop_session_opened_at, FechasSesion.hora(sesion.inicio, zona, resolver.locale))
        return resolver.get(R.string.workshop_session_closed_at, FechasSesion.fechaHora(cierre, zona, resolver.locale))
    }

    private fun comparacion(analisis: AnalisisSesion, zona: ZoneId, resolver: StringResolver): TarjetaComparacion {
        val base = analisis.chequeoBase ?: return TarjetaComparacion.SinBase
        val fecha = FechasSesion.fecha(base.instante, zona, resolver.locale)
        if (analisis.comparacion.isEmpty()) return TarjetaComparacion.SinChequeoAhora(fecha)
        return TarjetaComparacion.Filas(fecha, analisis.comparacion)
    }

    private fun evento(evento: EventoTaller, inicio: Long, zona: ZoneId, resolver: StringResolver): EventoUi {
        return EventoUi(
            id = evento.id,
            hora = horaEvento(evento.instante, inicio, zona, resolver),
            tipo = evento.tipo,
            titulo = evento.titulo,
            resumen = evento.resumen,
            veredicto = evento.veredicto,
            detalle = detalle(evento, zona, resolver),
        )
    }

    private fun horaEvento(instante: Long, inicio: Long, zona: ZoneId, resolver: StringResolver): String {
        val hora = FechasSesion.hora(instante, zona, resolver.locale)
        return if (FechasTaller.mismoDia(instante, inicio, zona)) hora else "${FechasSesion.dia(instante, zona, resolver.locale)} · $hora"
    }

    private fun detalle(evento: EventoTaller, zona: ZoneId, resolver: StringResolver): List<String> = listOfNotNull(
        resolver.get(
            R.string.workshop_session_exact_time,
            FechasSesion.fecha(evento.instante, zona, resolver.locale),
            FechasSesion.horaExacta(evento.instante, zona, resolver.locale),
        ),
        resolver.get(if (evento.origen == OrigenEvento.MCP) R.string.workshop_session_added_from_pc else R.string.workshop_session_added_from_app),
        evento.adjunto?.let { resolver.get(R.string.workshop_session_saved_file, File(it).name) },
    )
}

internal object Miles {
    // Espacio fino no separable: el número no se parte en dos líneas con la letra grande.
    private const val SEPARADOR = ' '

    fun agrupar(valor: Double): String =
        Math.round(valor).toString().reversed().chunked(3).joinToString(SEPARADOR.toString()).reversed()
}
