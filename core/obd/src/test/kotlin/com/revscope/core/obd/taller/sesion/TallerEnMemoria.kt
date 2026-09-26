package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.EstadoSemilla
import com.revscope.core.obd.taller.modelo.ModeloSemilla
import com.revscope.core.obd.taller.modelo.ResumenModelo
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class TallerRepositoryEnMemoria : TallerRepository {

    private val sesiones = MutableStateFlow<List<SesionTaller>>(emptyList())
    private val eventos = MutableStateFlow<List<EventoTaller>>(emptyList())
    var fallarAlAgregar = false

    val todosLosEventos: List<EventoTaller> get() = eventos.value

    override fun observarSesionAbierta(vehiculoId: Long): Flow<SesionTaller?> =
        sesiones.map { lista -> lista.lastOrNull { it.vehiculoId == vehiculoId && it.abierta } }

    override suspend fun sesionAbierta(vehiculoId: Long): SesionTaller? =
        sesiones.value.lastOrNull { it.vehiculoId == vehiculoId && it.abierta }

    override suspend fun sesion(id: Long): SesionTaller? = sesiones.value.firstOrNull { it.id == id }

    override fun observarSesiones(vehiculoId: Long): Flow<List<SesionTaller>> =
        sesiones.map { lista -> lista.filter { it.vehiculoId == vehiculoId }.sortedByDescending { it.inicio } }

    override suspend fun abrirSesion(sesion: SesionTaller): Long {
        val id = (sesiones.value.maxOfOrNull { it.id } ?: 0) + 1
        sesiones.value = sesiones.value.map {
            if (it.vehiculoId == sesion.vehiculoId && it.abierta) it.copy(cierre = sesion.inicio) else it
        } + sesion.copy(id = id, cierre = null)
        return id
    }

    override suspend fun actualizarSesion(sesion: SesionTaller) {
        sesiones.value = sesiones.value.map { if (it.id == sesion.id) sesion else it }
    }

    override suspend fun cerrarSesion(id: Long, instante: Long): Boolean {
        val abierta = sesiones.value.firstOrNull { it.id == id && it.abierta } ?: return false
        actualizarSesion(abierta.copy(cierre = instante))
        return true
    }

    override suspend fun eliminarSesion(id: Long): Boolean {
        val antes = sesiones.value.size
        sesiones.value = sesiones.value.filterNot { it.id == id }
        eventos.value = eventos.value.filterNot { it.sesionId == id }
        return sesiones.value.size < antes
    }

    override suspend fun agregarEvento(evento: EventoTaller): Long {
        check(!fallarAlAgregar) { "disco lleno" }
        val id = (eventos.value.maxOfOrNull { it.id } ?: 0) + 1
        eventos.value = eventos.value + evento.copy(id = id)
        return id
    }

    override suspend fun eventos(sesionId: Long): List<EventoTaller> =
        eventos.value.filter { it.sesionId == sesionId }.sortedWith(compareBy({ it.instante }, { it.id }))

    override fun observarEventos(sesionId: Long): Flow<List<EventoTaller>> =
        eventos.map { lista -> lista.filter { it.sesionId == sesionId } }

    override suspend fun conocimiento(clave: String): ConocimientoModelo? = null

    override fun observarModelos(): Flow<List<ResumenModelo>> = MutableStateFlow(emptyList())

    override suspend fun estadoSemilla(clave: String): EstadoSemilla? = null

    override suspend fun aplicarSemilla(semilla: ModeloSemilla) = Unit

    override suspend fun guardarBandaUsuario(claveModelo: String, banda: BandaReferencia) = Unit

    override suspend fun restablecerBanda(claveModelo: String, claveBanda: String): Boolean = false

    override suspend fun bandasResueltas(claveModelo: String?, tipo: VehicleType): Map<String, BandaReferencia> =
        ResolutorBandas.resolverTodas(tipo, emptyList())
}

class HistorialChequeosEnMemoria(private val chequeos: List<ChequeoRegistrado> = emptyList()) : HistorialChequeos {

    override suspend fun ultimoAntesDe(vehiculoId: Long, instante: Long): ChequeoRegistrado? =
        chequeos.filter { it.vehiculoId == vehiculoId && it.instante < instante }.maxByOrNull { it.instante }

    override suspend fun porId(id: Long): ChequeoRegistrado? = chequeos.firstOrNull { it.id == id }

    override suspend fun recientesAntesDe(vehiculoId: Long, instante: Long, limite: Int): List<ChequeoRegistrado> =
        chequeos.filter { it.vehiculoId == vehiculoId && it.instante < instante }.sortedByDescending { it.instante }.take(limite)
}

val BENELLI = VehiculoTaller(id = 7, nombre = "Benelli TNT 150i", claveModelo = "benelli-tnt150i-2022", tipo = VehicleType.MOTORCYCLE)
