package com.revscope.feature.workshop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.i18n.Region
import com.revscope.core.common.i18n.detect
import com.revscope.core.common.i18n.pricePerLiterInput
import com.revscope.core.common.stateInSafe
import com.revscope.core.data.db.dao.FuelUpDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.FuelUpEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.trip.FuelCalibration
import com.revscope.core.obd.trip.IntervaloTanqueo
import com.revscope.core.obd.trip.MotivoIgnorado
import com.revscope.core.obd.trip.ResultadoCalibracion
import com.revscope.core.obd.trip.Tanqueo
import com.revscope.core.obd.trip.ViajeCombustible
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TanqueosEstado(
    val perfil: VehicleProfileEntity?,
    val tanqueos: List<FuelUpEntity>,
    val intervalos: List<Pair<IntervaloTanqueo, MotivoIgnorado?>>,
    val calibracion: ResultadoCalibracion,
    val enGalones: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TanqueosViewModel @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val fuelUpDao: FuelUpDao,
    private val sessionDao: SessionDao,
    private val vehicleProfileDao: VehicleProfileDao,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val enGalones = !pricePerLiterInput(Region.detect(context))
    private val profileId = sessionManager.activeProfile.map { it?.id }
    private val tanqueos = profileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else fuelUpDao.observeForProfile(id)
    }
    private val viajes = profileId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else sessionDao.observeClosedForProfile(id)
    }

    val estado: StateFlow<TanqueosEstado> = combine(sessionManager.activeProfile, tanqueos, viajes) { perfil, items, sessions ->
        val tanqueosModelo = items.map { Tanqueo(it.timestamp, it.liters, it.fullTank) }
        val viajesModelo = sessions.map { ViajeCombustible(it.startedAt, it.fuelLiters, it.distanceKm.toDouble()) }
        val intervalos = FuelCalibration.intervalos(tanqueosModelo, viajesModelo)
        val calibracion = FuelCalibration.evaluar(intervalos, perfil?.fuelFactor ?: 1.0, perfil?.fuelFactorSince ?: 0)
        TanqueosEstado(
            perfil = perfil,
            tanqueos = items,
            intervalos = intervalos.map { intervalo -> intervalo to calibracion.ignorados.firstOrNull { it.first == intervalo }?.second },
            calibracion = calibracion,
            enGalones = enGalones,
        )
    }.stateInSafe(viewModelScope, TanqueosEstado(null, emptyList(), emptyList(), FuelCalibration.evaluar(emptyList(), 1.0, 0), enGalones), SharingStarted.Eagerly)

    suspend fun agregar(texto: String, lleno: Boolean): Boolean {
        val litros = FuelCalibration.parseVolumenTanqueo(texto, enGalones) ?: return false
        val perfil = sessionManager.activeProfile.value ?: return false
        fuelUpDao.insert(FuelUpEntity(vehicleProfileId = perfil.id, timestamp = System.currentTimeMillis(), liters = litros, fullTank = lleno))
        return true
    }

    fun borrar(id: Long) {
        viewModelScope.launch { fuelUpDao.deleteById(id) }
    }

    fun aplicarFactor() {
        val perfil = sessionManager.activeProfile.value ?: return
        val sugerido = estado.value.calibracion.factorSugerido ?: return
        viewModelScope.launch {
            val actualizado = perfil.copy(fuelFactor = sugerido, fuelFactorSince = System.currentTimeMillis())
            vehicleProfileDao.update(actualizado)
            sessionManager.notifyProfileUpdated(actualizado)
        }
    }
}
