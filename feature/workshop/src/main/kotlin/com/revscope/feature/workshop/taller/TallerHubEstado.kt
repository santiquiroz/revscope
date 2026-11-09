package com.revscope.feature.workshop.taller

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.taller.sesion.EstadoSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import com.revscope.core.obd.taller.pid.CapacidadesEcu
import java.time.ZoneId
import java.util.Locale

sealed interface EstadoAdaptador {
    data class Conectado(val dispositivo: String) : EstadoAdaptador
    data object Conectando : EstadoAdaptador
    data object Desconectado : EstadoAdaptador
    data class ConError(val mensaje: String) : EstadoAdaptador

    val conectado: Boolean get() = this is Conectado
}

data class TarjetaSesionAbierta(
    val id: Long,
    val titulo: String,
    val desde: String,
    val codigos: List<String>,
    val eventos: Int,
    val ultimoEvento: String?,
)

data class FilaSesionAnterior(val id: Long, val titulo: String, val fecha: String)

data class TallerHubEstado(
    val cargando: Boolean = true,
    val vehiculo: String? = null,
    val esMoto: Boolean = true,
    val adaptador: EstadoAdaptador = EstadoAdaptador.Desconectado,
    val sesionAbierta: TarjetaSesionAbierta? = null,
    val anteriores: List<FilaSesionAnterior> = emptyList(),
    val verTodas: Boolean = false,
    val capacidades: CapacidadesEcu = CapacidadesEcu.SIN_DATOS,
) {
    val anterioresVisibles: List<FilaSesionAnterior>
        get() = if (verTodas) anteriores else anteriores.take(ANTERIORES_EN_RESUMEN)

    val hayMasAnteriores: Boolean get() = anteriores.size > ANTERIORES_EN_RESUMEN

    companion object {
        const val ANTERIORES_EN_RESUMEN = 3
    }
}

internal data class DatosHub(
    val vehiculo: VehiculoTaller?,
    val sesiones: List<SesionTaller>,
    val eventosAbierta: List<EventoTaller>,
)

internal object ResumenHub {

    fun de(
        datos: DatosHub,
        conexion: ConnectionState,
        verTodas: Boolean,
        zona: ZoneId,
        locale: Locale,
        capacidades: CapacidadesEcu = CapacidadesEcu.SIN_DATOS,
    ): TallerHubEstado {
        val abierta = datos.sesiones.firstOrNull { it.abierta }
        return TallerHubEstado(
            cargando = false,
            vehiculo = datos.vehiculo?.nombre,
            esMoto = datos.vehiculo?.tipo?.let { it == VehicleType.MOTORCYCLE } ?: true,
            adaptador = adaptador(conexion),
            sesionAbierta = abierta?.let { tarjeta(it, datos.eventosAbierta, zona, locale) },
            anteriores = datos.sesiones.filterNot { it.abierta }.sortedByDescending { it.inicio }.map { fila(it, zona, locale) },
            verTodas = verTodas,
            capacidades = capacidades,
        )
    }

    fun adaptador(conexion: ConnectionState): EstadoAdaptador = when (conexion) {
        is ConnectionState.Connected -> EstadoAdaptador.Conectado(conexion.deviceName)
        ConnectionState.Connecting -> EstadoAdaptador.Conectando
        ConnectionState.Disconnected -> EstadoAdaptador.Desconectado
        is ConnectionState.Error -> EstadoAdaptador.ConError(conexion.message)
    }

    private fun tarjeta(sesion: SesionTaller, eventos: List<EventoTaller>, zona: ZoneId, locale: Locale): TarjetaSesionAbierta {
        val ultimo = LineaDeTiempo.ordenar(eventos).lastOrNull()
        return TarjetaSesionAbierta(
            id = sesion.id,
            titulo = sesion.titulo,
            desde = FechasSesion.fechaHora(sesion.inicio, zona, locale),
            codigos = EstadoSesion.codigosActuales(eventos),
            eventos = eventos.size,
            ultimoEvento = ultimo?.let { "${FechasSesion.hora(it.instante, zona, locale)} · ${it.titulo}" },
        )
    }

    private fun fila(sesion: SesionTaller, zona: ZoneId, locale: Locale): FilaSesionAnterior {
        val cierre = sesion.cierre?.let { " – ${FechasSesion.hora(it, zona, locale)}" }.orEmpty()
        return FilaSesionAnterior(sesion.id, sesion.titulo, "${FechasSesion.fechaHora(sesion.inicio, zona, locale)}$cierre")
    }
}

internal object LineaDeTiempo {
    fun ordenar(eventos: List<EventoTaller>): List<EventoTaller> =
        eventos.sortedWith(compareBy<EventoTaller> { it.instante }.thenBy { it.id })
}
