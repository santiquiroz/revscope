package com.revscope.feature.vehicle.perfilcan

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.stateInSafe
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.perfilcan.ClavesSenal
import com.revscope.core.obd.perfilcan.EstadoSenal
import com.revscope.core.obd.perfilcan.FuenteSenal
import com.revscope.core.obd.perfilcan.ModoFuente
import com.revscope.core.obd.perfilcan.PerfilCan
import com.revscope.core.obd.perfilcan.PerfilesCanRepositorio
import com.revscope.core.obd.perfilcan.SenalCan
import com.revscope.core.obd.perfilcan.fuentesPorSenalDe
import com.revscope.core.obd.perfilcan.modoFuenteDe
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.feature.vehicle.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class OpcionPerfil(val id: String, val nombre: String)

data class SenalBusUi(
    val clave: String,
    val nombre: String,
    val verificada: Boolean,
    val fuente: FuenteSenal,
    val opciones: List<FuenteSenal>,
)

data class DatosBusCanUi(
    val perfiles: List<OpcionPerfil>,
    val perfilId: String?,
    val modo: ModoFuente,
    val senales: List<SenalBusUi>,
    val motivoSinPrueba: String?,
)

data class PruebaEnVivoUi(
    val clave: String,
    val nombre: String,
    val instruccion: String,
    val detalle: String,
    val valor: String?,
    val disponible: Boolean,
    val motivoSinPrueba: String?,
    val coincide: Boolean? = null,
)

@HiltViewModel
class PerfilCanViewModel internal constructor(
    private val profileDao: VehicleProfileDao,
    private val repositorio: PerfilesCanRepositorio,
    private val sessionManager: ObdSessionManager,
    private val settings: DataStore<Preferences>,
    private val despachadorDisco: CoroutineDispatcher,
    private val resolver: StringResolver,
) : ViewModel() {

    @Inject
    constructor(
        profileDao: VehicleProfileDao,
        repositorio: PerfilesCanRepositorio,
        sessionManager: ObdSessionManager,
        settings: DataStore<Preferences>,
        resolver: StringResolver,
    ) : this(profileDao, repositorio, sessionManager, settings, Dispatchers.IO, resolver)

    private val vehiculo = MutableStateFlow<VehicleProfileEntity?>(null)
    private val velocidadGps = settings.data.map { it[PreferencesKeys.SPEED_SOURCE_GPS] ?: false }
    private val motivoSinPrueba = combine(sessionManager.connectionState, sessionManager.capacidadesEcu) { estado, _ ->
        motivoSinPrueba(estado)
    }

    val datos: StateFlow<DatosBusCanUi?> =
        combine(vehiculo, repositorio.cambios, velocidadGps, motivoSinPrueba) { v, _, gps, motivo ->
            v?.let { armarDatos(it, gps, motivo) }
        }.flowOn(despachadorDisco).stateInSafe(viewModelScope, null, started = SharingStarted.Eagerly)

    private val _prueba = MutableStateFlow<PruebaEnVivoUi?>(null)
    val prueba: StateFlow<PruebaEnVivoUi?> = _prueba.asStateFlow()
    private var trabajoPrueba: Job? = null

    fun cargar(vehiculoId: Long) {
        viewModelScope.launch { vehiculo.value = profileDao.getById(vehiculoId) }
    }

    fun elegirPerfil(id: String?) = actualizar { v ->
        if (id == null) {
            v.copy(perfilCanId = null, fuenteDatos = ModoFuente.ESTANDAR.name)
        } else {
            repositorio.adoptar(id)
            v.copy(perfilCanId = id, fuenteDatos = modoAlElegirPerfil(v).name)
        }
    }

    fun elegirModo(modo: ModoFuente) = actualizar { it.copy(fuenteDatos = modo.name) }

    fun elegirFuente(clave: String, fuente: FuenteSenal) {
        actualizar { it.copy(fuentesPorSenal = conFuente(it.fuentesPorSenal, clave, fuente)) }
        // El interruptor GPS/PID del tablero sigue leyendo esta preferencia.
        if (clave == ClavesSenal.VELOCIDAD) {
            viewModelScope.launch { settings.edit { it[PreferencesKeys.SPEED_SOURCE_GPS] = fuente == FuenteSenal.GPS } }
        }
    }

    fun abrirPrueba(vehiculoId: Long, clave: String) {
        trabajoPrueba?.cancel()
        trabajoPrueba = viewModelScope.launch {
            val v = profileDao.getById(vehiculoId) ?: return@launch
            vehiculo.value = v
            val senal = perfilDe(v)?.senales?.firstOrNull { it.clave == clave } ?: return@launch
            val motivo = motivoSinPrueba(sessionManager.connectionState.value)
            _prueba.value = pruebaInicial(senal, motivo)
            if (motivo != null) return@launch
            sessionManager.probarSenal(senal).collect { valor -> _prueba.update { it?.copy(valor = valor) } }
        }
    }

    fun responderPrueba(coincide: Boolean) {
        val prueba = _prueba.value?.takeIf { it.disponible } ?: return
        val perfilId = vehiculo.value?.perfilCanId ?: return
        trabajoPrueba?.cancel()
        val estado = if (coincide) EstadoSenal.VERIFICADA else EstadoSenal.SIN_VERIFICAR
        repositorio.marcarEstado(perfilId, prueba.clave, estado)
        _prueba.value = prueba.copy(coincide = coincide)
    }

    override fun onCleared() {
        trabajoPrueba?.cancel()
    }

    private fun actualizar(cambio: (VehicleProfileEntity) -> VehicleProfileEntity) {
        val id = vehiculo.value?.id ?: return
        viewModelScope.launch {
            val actual = profileDao.getById(id) ?: return@launch
            val nuevo = cambio(actual)
            profileDao.update(nuevo)
            sessionManager.notifyProfileUpdated(nuevo)
            vehiculo.value = nuevo
        }
    }

    private fun motivoSinPrueba(estado: ConnectionState): String? = when {
        sessionManager.puedeProbarBus() -> null
        estado is ConnectionState.Connected -> resolver.get(R.string.vehicle_can_no_bus)
        else -> resolver.get(R.string.vehicle_can_no_connection)
    }

    private fun perfilDe(v: VehicleProfileEntity): PerfilCan? = v.perfilCanId?.let(repositorio::leer)

    private fun armarDatos(v: VehicleProfileEntity, gps: Boolean, motivo: String?): DatosBusCanUi {
        val preferencias = fuentesPorSenalDe(v.fuentesPorSenal)
        return DatosBusCanUi(
            perfiles = repositorio.disponibles().map { OpcionPerfil(it.id, nombrePerfil(it)) },
            perfilId = v.perfilCanId,
            modo = modoFuenteDe(v.fuenteDatos),
            senales = perfilDe(v)?.senales.orEmpty().map { senalUi(it, preferencias, gps) },
            motivoSinPrueba = motivo,
        )
    }

    private fun senalUi(senal: SenalCan, preferencias: Map<String, FuenteSenal>, gps: Boolean) = SenalBusUi(
        clave = senal.clave,
        nombre = senal.nombre,
        verificada = senal.estado == EstadoSenal.VERIFICADA,
        fuente = preferencias[senal.clave] ?: fuentePorDefecto(senal.clave, gps),
        opciones = TextosBusCan.opciones(senal.clave),
    )

    private fun fuentePorDefecto(clave: String, gps: Boolean): FuenteSenal =
        if (clave == ClavesSenal.VELOCIDAD && gps) FuenteSenal.GPS else FuenteSenal.AUTO

    private fun pruebaInicial(senal: SenalCan, motivo: String?) = PruebaEnVivoUi(
        clave = senal.clave,
        nombre = senal.nombre,
        instruccion = TextosBusCan.instruccion(senal.clave, resolver),
        detalle = TextosBusCan.detalle(senal, resolver),
        valor = null,
        disponible = motivo == null,
        motivoSinPrueba = motivo,
    )

    private companion object {
        fun modoAlElegirPerfil(v: VehicleProfileEntity): ModoFuente =
            modoFuenteDe(v.fuenteDatos).takeIf { it != ModoFuente.ESTANDAR } ?: ModoFuente.COMBINADO

        fun nombrePerfil(perfil: PerfilCan): String =
            listOf(perfil.vehiculo.marca, perfil.vehiculo.modelo, perfil.vehiculo.anios)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { perfil.id }

        fun conFuente(json: String, clave: String, fuente: FuenteSenal): String {
            val fuentes = fuentesPorSenalDe(json).toMutableMap()
            if (fuente == FuenteSenal.AUTO) fuentes.remove(clave) else fuentes[clave] = fuente
            return JSONObject(fuentes.mapValues { it.value.name }).toString()
        }
    }
}
