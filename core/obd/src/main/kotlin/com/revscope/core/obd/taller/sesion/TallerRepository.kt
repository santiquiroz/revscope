package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.EstadoSemilla
import com.revscope.core.obd.taller.modelo.ModeloSemilla
import com.revscope.core.obd.taller.modelo.ResumenModelo
import com.revscope.core.obd.taller.referencia.BandaReferencia
import kotlinx.coroutines.flow.Flow

interface TallerRepository {

    fun observarSesionAbierta(vehiculoId: Long): Flow<SesionTaller?>

    suspend fun sesionAbierta(vehiculoId: Long): SesionTaller?

    suspend fun sesion(id: Long): SesionTaller?

    fun observarSesiones(vehiculoId: Long): Flow<List<SesionTaller>>

    // Cierra en la misma transacción la sesión que siguiera abierta para ese vehículo.
    suspend fun abrirSesion(sesion: SesionTaller): Long

    suspend fun actualizarSesion(sesion: SesionTaller)

    suspend fun cerrarSesion(id: Long, instante: Long): Boolean

    suspend fun eliminarSesion(id: Long): Boolean

    suspend fun agregarEvento(evento: EventoTaller): Long

    suspend fun eventos(sesionId: Long): List<EventoTaller>

    fun observarEventos(sesionId: Long): Flow<List<EventoTaller>>

    suspend fun conocimiento(clave: String): ConocimientoModelo?

    fun observarModelos(): Flow<List<ResumenModelo>>

    suspend fun estadoSemilla(clave: String): EstadoSemilla?

    suspend fun aplicarSemilla(semilla: ModeloSemilla)

    suspend fun guardarBandaUsuario(claveModelo: String, banda: BandaReferencia)

    suspend fun restablecerBanda(claveModelo: String, claveBanda: String): Boolean

    suspend fun bandasResueltas(claveModelo: String?, tipo: VehicleType): Map<String, BandaReferencia>
}
