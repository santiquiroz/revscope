package com.revscope.feature.workshop

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.trip.MaintenanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToLong

private const val DEFAULT_ACEITE_KM = 3_000.0
private const val DEFAULT_LLANTAS_KM = 10_000.0
private const val DEFAULT_BATERIA_KM = 20_000.0
private const val DEFAULT_KIT_ARRASTRE_KM = 15_000.0
private const val DEFAULT_REFRIGERANTE_KM = 20_000.0
private const val TYPE_MOTORCYCLE = "MOTORCYCLE"

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MaintenanceViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val maintenanceDao: MaintenanceDao,
    private val sessionDao: SessionDao,
    private val vehicleProfileDao: VehicleProfileDao,
) : ViewModel() {

    data class SaveResult(val success: Boolean, val message: String)

    val activeProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    private val profileIdFlow: Flow<Long?> = activeProfile.map { it?.id }

    val items: StateFlow<List<MaintenanceItemEntity>> = profileIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else maintenanceDao.observeForProfile(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val sumaSesionesFlow: Flow<Double> = profileIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(0.0) else sessionDao.observeSumDistanceKmForProfile(id) }

    /** Sobrescribe el odómetro calculado justo después de guardarlo, hasta que cambie de perfil. */
    private val _odometroOverrideKm = MutableStateFlow<Double?>(null)

    val odometroActual: StateFlow<Double> =
        combine(activeProfile, sumaSesionesFlow, _odometroOverrideKm) { profile, suma, override ->
            override ?: MaintenanceCalculator.odometroActual(profile?.odometerBaseKm ?: 0.0, suma)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, 0.0)

    val estados: StateFlow<List<MaintenanceCalculator.ItemStatus>> =
        combine(odometroActual, items) { odometro, items -> MaintenanceCalculator.calculate(odometro, items) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _saveResult = MutableStateFlow<SaveResult?>(null)
    val saveResult: StateFlow<SaveResult?> = _saveResult.asStateFlow()

    private var defaultsCreatedForProfileId: Long? = null

    init {
        viewModelScope.launch {
            activeProfile.collect { profile ->
                _odometroOverrideKm.value = null
                if (profile != null) ensureDefaults(profile)
            }
        }
    }

    fun dismissSaveResult() {
        _saveResult.value = null
    }

    private suspend fun ensureDefaults(profile: VehicleProfileEntity) {
        if (defaultsCreatedForProfileId == profile.id) return
        try {
            val existentes = maintenanceDao.listForProfile(profile.id)
            if (existentes.isNotEmpty()) {
                defaultsCreatedForProfileId = profile.id
                return
            }
            val odometro = odometroReal(profile)
            // Sin kilometraje real todavía no hay línea base confiable para los defaults
            if (odometro <= 0.0) return
            crearItemsPorDefecto(profile, odometro)
            defaultsCreatedForProfileId = profile.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "MaintenanceViewModel: failed to create default items")
        }
    }

    private suspend fun odometroReal(profile: VehicleProfileEntity): Double {
        val suma = sessionDao.observeSumDistanceKmForProfile(profile.id).first()
        return MaintenanceCalculator.odometroActual(profile.odometerBaseKm, suma)
    }

    private suspend fun crearItemsPorDefecto(profile: VehicleProfileEntity, odometro: Double) {
        val items = defaultItemDefinitions(profile.type).map { (nombre, intervaloKm) ->
            MaintenanceItemEntity(
                vehicleProfileId = profile.id,
                nombre = nombre,
                intervaloKm = intervaloKm,
                ultimoServicioKm = odometro,
            )
        }
        maintenanceDao.insertAll(items)
    }

    private fun defaultItemDefinitions(type: String): List<Pair<String, Double>> {
        val base = listOf(
            "Aceite" to DEFAULT_ACEITE_KM,
            "Llantas (revisión)" to DEFAULT_LLANTAS_KM,
            "Batería (revisión)" to DEFAULT_BATERIA_KM,
        )
        if (type != TYPE_MOTORCYCLE) return base
        return base + listOf(
            "Kit de arrastre" to DEFAULT_KIT_ARRASTRE_KM,
            "Refrigerante" to DEFAULT_REFRIGERANTE_KM,
        )
    }

    fun registrarServicio(item: MaintenanceItemEntity) {
        viewModelScope.launch {
            try {
                maintenanceDao.update(item.copy(ultimoServicioKm = odometroActual.value))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "MaintenanceViewModel: failed to register service")
            }
        }
    }

    fun actualizarIntervalo(item: MaintenanceItemEntity, nuevoIntervaloKm: Double) {
        if (nuevoIntervaloKm <= 0) return
        viewModelScope.launch {
            try {
                maintenanceDao.update(item.copy(intervaloKm = nuevoIntervaloKm))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "MaintenanceViewModel: failed to update interval")
            }
        }
    }

    fun agregarItem(nombre: String, intervaloKm: Double) {
        val nombreTrim = nombre.trim()
        val profileId = activeProfile.value?.id ?: return
        if (nombreTrim.isEmpty() || intervaloKm <= 0) return
        viewModelScope.launch {
            try {
                maintenanceDao.insert(
                    MaintenanceItemEntity(
                        vehicleProfileId = profileId,
                        nombre = nombreTrim,
                        intervaloKm = intervaloKm,
                        ultimoServicioKm = odometroActual.value,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "MaintenanceViewModel: failed to add item")
            }
        }
    }

    fun actualizarOdometro(kilometrajeIngresado: Double) {
        val profile = activeProfile.value ?: return
        if (kilometrajeIngresado < 0) return
        viewModelScope.launch {
            try {
                val suma = sessionDao.observeSumDistanceKmForProfile(profile.id).first()
                if (kilometrajeIngresado < suma) {
                    _saveResult.value = SaveResult(
                        false,
                        "El kilometraje no puede ser menor que la distancia ya registrada (${suma.roundToLong()} km)",
                    )
                    return@launch
                }
                val actualizado = profile.copy(odometerBaseKm = kilometrajeIngresado - suma)
                vehicleProfileDao.update(actualizado)
                sessionManager.notifyProfileUpdated(actualizado)
                _odometroOverrideKm.value = kilometrajeIngresado
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "MaintenanceViewModel: failed to update odometer")
            }
        }
    }
}
