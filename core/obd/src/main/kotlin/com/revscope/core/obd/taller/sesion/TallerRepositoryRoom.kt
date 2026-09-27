package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.dao.DiagSessionDao
import com.revscope.core.data.db.dao.VehicleKnowledgeDao
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.CodecConocimiento
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.EstadoSemilla
import com.revscope.core.obd.taller.modelo.ModeloSemilla
import com.revscope.core.obd.taller.modelo.ResumenModelo
import com.revscope.core.obd.taller.modelo.aConocimiento
import com.revscope.core.obd.taller.modelo.aEntidad
import com.revscope.core.obd.taller.modelo.aResumen
import com.revscope.core.obd.taller.multimetro.ResolutorPlantilla
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TallerRepositoryRoom @Inject constructor(
    private val sesiones: DiagSessionDao,
    private val conocimientos: VehicleKnowledgeDao,
) : TallerRepository {

    override fun observarSesionAbierta(vehiculoId: Long): Flow<SesionTaller?> =
        sesiones.observeOpenSession(vehiculoId).map { it?.aSesion() }

    override suspend fun sesionAbierta(vehiculoId: Long): SesionTaller? =
        sesiones.getOpenSession(vehiculoId)?.aSesion()

    override suspend fun sesion(id: Long): SesionTaller? = sesiones.getSession(id)?.aSesion()

    override fun observarSesiones(vehiculoId: Long): Flow<List<SesionTaller>> =
        sesiones.observeSessions(vehiculoId).map { lista -> lista.map { it.aSesion() } }

    override suspend fun abrirSesion(sesion: SesionTaller): Long =
        sesiones.openClosingPrevious(sesion.copy(id = 0, cierre = null).aEntidad())

    override suspend fun actualizarSesion(sesion: SesionTaller) = sesiones.updateSession(sesion.aEntidad())

    override suspend fun cerrarSesion(id: Long, instante: Long): Boolean = sesiones.closeSession(id, instante) > 0

    override suspend fun eliminarSesion(id: Long): Boolean = sesiones.deleteSession(id) > 0

    override suspend fun agregarEvento(evento: EventoTaller): Long =
        sesiones.insertEvent(evento.copy(id = 0).aEntidad())

    override suspend fun eventos(sesionId: Long): List<EventoTaller> =
        sesiones.getEvents(sesionId).map { it.aEvento() }

    override fun observarEventos(sesionId: Long): Flow<List<EventoTaller>> =
        sesiones.observeEvents(sesionId).map { lista -> lista.map { it.aEvento() } }

    override suspend fun conocimiento(clave: String): ConocimientoModelo? {
        val entidad = conocimientos.getKnowledge(clave) ?: return null
        return entidad.aConocimiento(conocimientos.getParts(clave), conocimientos.getBands(clave))
    }

    override fun observarModelos(): Flow<List<ResumenModelo>> =
        conocimientos.observeAll().map { lista -> lista.map { it.aResumen() } }

    override suspend fun estadoSemilla(clave: String): EstadoSemilla? =
        conocimientos.getKnowledge(clave)?.let { EstadoSemilla(it.seedVersion, it.userEdited) }

    override suspend fun aplicarSemilla(semilla: ModeloSemilla) {
        val modelo = semilla.conocimiento.copy(editadoPorUsuario = false)
        conocimientos.replaceSeed(
            knowledge = modelo.aEntidad(semilla.version),
            parts = modelo.repuestos.map { it.aEntidad(modelo.clave) },
            bands = modelo.bandas.map { it.aEntidad(modelo.clave) },
            seedBandOrigin = OrigenBanda.FUENTE.name,
        )
    }

    override suspend fun guardarCableado(claveModelo: String, cableado: CableadoSensor): Boolean {
        val entidad = conocimientos.getKnowledge(claveModelo) ?: return false
        val nuevo = ResolutorPlantilla.reemplazar(CodecConocimiento.cableadoDesdeJson(entidad.wiringJson), cableado)
        return conocimientos.updateWiring(claveModelo, CodecConocimiento.cableadoAJson(nuevo)) > 0
    }

    override suspend fun guardarBandaUsuario(claveModelo: String, banda: BandaReferencia) =
        conocimientos.replaceBand(banda.copy(origen = OrigenBanda.USUARIO, fuente = "").aEntidad(claveModelo))

    override suspend fun restablecerBanda(claveModelo: String, claveBanda: String): Boolean =
        conocimientos.deleteBand(claveModelo, claveBanda) > 0

    override suspend fun bandasResueltas(claveModelo: String?, tipo: VehicleType): Map<String, BandaReferencia> {
        val bandasModelo = claveModelo?.let { conocimiento(it)?.bandas }.orEmpty()
        return ResolutorBandas.resolverTodas(tipo, bandasModelo)
    }
}
