package com.revscope.feature.workshop.taller

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.taller.modelo.CableadoSensor
import com.revscope.core.obd.taller.modelo.ConocimientoModelo
import com.revscope.core.obd.taller.modelo.EstadoSemilla
import com.revscope.core.obd.taller.modelo.ModeloSemilla
import com.revscope.core.obd.taller.modelo.ResumenModelo
import com.revscope.core.obd.taller.multimetro.ResolutorPlantilla
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ResolutorBandas
import com.revscope.core.obd.taller.sesion.AdjuntosTaller
import com.revscope.core.obd.taller.sesion.AnalizadorSesion
import com.revscope.core.obd.taller.sesion.ChequeoRegistrado
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.HistorialChequeos
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.SugeridorPruebas
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoTaller
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.ZoneId
import java.time.ZoneOffset

val BENELLI = VehiculoTaller(id = 7, nombre = "Benelli TNT 150i", claveModelo = "benelli-tnt150i-2022", tipo = VehicleType.MOTORCYCLE)
val MAZDA = VehiculoTaller(id = 8, nombre = "Mazda CX-30", claveModelo = null, tipo = VehicleType.CAR)

// 25-sep-2026 20:00 en Colombia (UTC-5).
const val SEP25_20H = 1_790_384_400_000L
val BOGOTA: ZoneId = ZoneOffset.ofHours(-5)

class RepositorioEnMemoria : TallerRepository {

    val sesiones = MutableStateFlow<List<SesionTaller>>(emptyList())
    val eventos = MutableStateFlow<List<EventoTaller>>(emptyList())

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
        val id = (eventos.value.maxOfOrNull { it.id } ?: 0) + 1
        eventos.value = eventos.value + evento.copy(id = id)
        return id
    }

    override suspend fun eventos(sesionId: Long): List<EventoTaller> = eventos.value.filter { it.sesionId == sesionId }

    // Sin ordenar a propósito: la pantalla no debe depender del orden en que la base devuelva los eventos.
    override fun observarEventos(sesionId: Long): Flow<List<EventoTaller>> =
        eventos.map { lista -> lista.filter { it.sesionId == sesionId } }

    val modelos = mutableMapOf<String, ConocimientoModelo>()

    override suspend fun conocimiento(clave: String): ConocimientoModelo? = modelos[clave]

    override suspend fun guardarCableado(claveModelo: String, cableado: CableadoSensor): Boolean {
        val modelo = modelos[claveModelo] ?: return false
        modelos[claveModelo] = modelo.copy(cableado = ResolutorPlantilla.reemplazar(modelo.cableado, cableado), editadoPorUsuario = true)
        return true
    }

    override fun observarModelos(): Flow<List<ResumenModelo>> = MutableStateFlow(emptyList())

    override suspend fun estadoSemilla(clave: String): EstadoSemilla? = null

    override suspend fun aplicarSemilla(semilla: ModeloSemilla) = Unit

    override suspend fun guardarBandaUsuario(claveModelo: String, banda: BandaReferencia) = Unit

    override suspend fun restablecerBanda(claveModelo: String, claveBanda: String): Boolean = false

    override suspend fun bandasResueltas(claveModelo: String?, tipo: VehicleType): Map<String, BandaReferencia> =
        ResolutorBandas.resolverTodas(tipo, claveModelo?.let { modelos[it]?.bandas }.orEmpty())
}

class HistorialEnMemoria(private val chequeos: List<ChequeoRegistrado> = emptyList()) : HistorialChequeos {

    override suspend fun ultimoAntesDe(vehiculoId: Long, instante: Long): ChequeoRegistrado? =
        recientesAntesDe(vehiculoId, instante, 1).firstOrNull()

    override suspend fun porId(id: Long): ChequeoRegistrado? = chequeos.firstOrNull { it.id == id }

    override suspend fun recientesAntesDe(vehiculoId: Long, instante: Long, limite: Int): List<ChequeoRegistrado> =
        chequeos.filter { it.vehiculoId == vehiculoId && it.instante < instante }.sortedByDescending { it.instante }.take(limite)
}

class EntornoDePrueba(
    vehiculo: VehiculoTaller? = BENELLI,
    conexion: ConnectionState = ConnectionState.Connected("vLinker FS"),
    var reloj: Long = SEP25_20H,
    var odometro: Double? = null,
    var lecturasActuales: Map<String, ObdReading> = emptyMap(),
) : EntornoTaller {
    val vehiculoFlujo = MutableStateFlow(vehiculo)
    val conexionFlujo = MutableStateFlow(conexion)

    override val vehiculo: Flow<VehiculoTaller?> = vehiculoFlujo
    override val conexion: Flow<ConnectionState> = conexionFlujo
    override val zona: ZoneId = BOGOTA

    override fun ahora(): Long = reloj

    override fun odometroEcuKm(): Double? = odometro

    override fun lecturas(): Map<String, ObdReading> = lecturasActuales
}

class TallerDePrueba(carpeta: File, chequeos: List<ChequeoRegistrado> = emptyList()) {
    val repositorio = RepositorioEnMemoria()
    val historial = HistorialEnMemoria(chequeos)
    val entorno = EntornoDePrueba()
    private val activo: VehiculoTaller? get() = entorno.vehiculoFlujo.value

    val registro = RegistroTaller(
        repositorio = repositorio,
        vehiculo = { activo },
        historial = historial,
        adjuntos = AdjuntosTaller(File(carpeta, "taller")),
        registry = PidRegistry("[]"),
        reloj = { entorno.reloj },
    )

    val analizador = AnalizadorSesion(repositorio, historial, SugeridorPruebas { emptyList() })
}
