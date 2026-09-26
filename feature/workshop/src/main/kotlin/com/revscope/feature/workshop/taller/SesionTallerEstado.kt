package com.revscope.feature.workshop.taller

import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.FilaComparacion
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.core.obd.taller.sesion.VehiculoTaller
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

    fun de(datos: DatosSesion, ui: UiSesion, zona: ZoneId): SesionTallerEstado {
        val sesion = datos.sesion
        val ordenados = LineaDeTiempo.ordenar(datos.eventos)
        return SesionTallerEstado(
            cargando = false,
            titulo = sesion.titulo,
            subtitulo = subtitulo(sesion, datos.vehiculoActivo, zona),
            abierta = sesion.abierta,
            estadoTexto = estadoTexto(sesion, zona),
            sintomas = sesion.sintomas.sortedBy { it.ordinal }.map { it.etiqueta },
            sintomasTexto = sesion.sintomasTexto,
            notas = sesion.notas,
            odometro = sesion.odometroKm?.let { "${Miles.agrupar(it)} km" },
            codigos = datos.analisis.codigos,
            comparacion = comparacion(datos.analisis, zona),
            sugeridas = datos.analisis.sugeridas.take(MAX_SUGERIDAS).map { PruebaSugeridaUi(it.accion.etiqueta, it.motivo) },
            eventos = ordenados.map { evento(it, sesion.inicio, zona) },
            agregar = disponibilidad(sesion, datos.vehiculoActivo),
            adaptadorConectado = datos.conectado,
            loQueSeBorra = LoQueSeBorra(datos.eventos.size, datos.eventos.count { it.adjunto != null }),
            ui = ui,
        )
    }

    fun disponibilidad(sesion: SesionTaller, activo: VehiculoTaller?): DisponibilidadAgregar = when {
        !sesion.abierta -> DisponibilidadAgregar.SesionCerrada
        activo?.id != sesion.vehiculoId -> DisponibilidadAgregar.OtroVehiculo(
            "Esta sesión es de otro vehículo. Elígelo como vehículo activo para agregarle lecturas.",
        )
        else -> DisponibilidadAgregar.Disponible
    }

    private fun subtitulo(sesion: SesionTaller, activo: VehiculoTaller?, zona: ZoneId): String {
        val fecha = FechasTaller.fecha(sesion.inicio, zona)
        return if (activo?.id == sesion.vehiculoId) "${activo.nombre} · $fecha" else fecha
    }

    private fun estadoTexto(sesion: SesionTaller, zona: ZoneId): String {
        val cierre = sesion.cierre ?: return "Abierta desde las ${FechasTaller.hora(sesion.inicio, zona)}"
        return "Cerrada el ${FechasTaller.fechaHora(cierre, zona)}"
    }

    private fun comparacion(analisis: AnalisisSesion, zona: ZoneId): TarjetaComparacion {
        val base = analisis.chequeoBase ?: return TarjetaComparacion.SinBase
        val fecha = FechasTaller.fecha(base.instante, zona)
        if (analisis.comparacion.isEmpty()) return TarjetaComparacion.SinChequeoAhora(fecha)
        return TarjetaComparacion.Filas(fecha, analisis.comparacion)
    }

    private fun evento(evento: EventoTaller, inicio: Long, zona: ZoneId): EventoUi {
        return EventoUi(
            id = evento.id,
            hora = horaEvento(evento.instante, inicio, zona),
            tipo = evento.tipo,
            titulo = evento.titulo,
            resumen = evento.resumen,
            veredicto = evento.veredicto,
            detalle = detalle(evento, zona),
        )
    }

    private fun horaEvento(instante: Long, inicio: Long, zona: ZoneId): String {
        val hora = FechasTaller.hora(instante, zona)
        return if (FechasTaller.mismoDia(instante, inicio, zona)) hora else "${FechasTaller.dia(instante, zona)} · $hora"
    }

    private fun detalle(evento: EventoTaller, zona: ZoneId): List<String> = listOfNotNull(
        "Hora exacta: ${FechasTaller.fecha(evento.instante, zona)}, ${FechasTaller.horaExacta(evento.instante, zona)}",
        if (evento.origen == OrigenEvento.MCP) "Anotado desde el PC (MCP)" else "Anotado por la app",
        evento.adjunto?.let { "Archivo guardado en la sesión: ${File(it).name}" },
    )
}

internal object Miles {
    // Espacio fino no separable: el número no se parte en dos líneas con la letra grande.
    private const val SEPARADOR = ' '

    fun agrupar(valor: Double): String =
        Math.round(valor).toString().reversed().chunked(3).joinToString(SEPARADOR.toString()).reversed()
}
