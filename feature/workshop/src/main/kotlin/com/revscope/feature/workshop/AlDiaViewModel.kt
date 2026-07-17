package com.revscope.feature.workshop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.legal.DocumentStatusCalculator
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.legal.RestrictionRulesSource
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.trip.MaintenanceCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val MINUTE_MS = 60_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AlDiaViewModel @Inject constructor(
    sessionManager: ObdSessionManager,
    private val settings: DataStore<Preferences>,
    private val maintenanceDao: MaintenanceDao,
    private val sessionDao: SessionDao,
    private val aiRulesSource: RestrictionRulesSource,
) : ViewModel() {

    val activeProfile: StateFlow<VehicleProfileEntity?> = sessionManager.activeProfile

    private val profileIdFlow: Flow<Long?> = activeProfile.map { it?.id }

    private val maintenanceItemsFlow: Flow<List<MaintenanceItemEntity>> = profileIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else maintenanceDao.observeForProfile(id)
    }

    private val sumaSesionesKmFlow: Flow<Double> = profileIdFlow.flatMapLatest { id ->
        if (id == null) flowOf(0.0) else sessionDao.observeSumDistanceKmForProfile(id)
    }

    val maintenanceEstados: StateFlow<List<MaintenanceCalculator.ItemStatus>> =
        combine(activeProfile, sumaSesionesKmFlow, maintenanceItemsFlow) { profile, suma, items ->
            val odometro = MaintenanceCalculator.odometroActual(profile?.odometerBaseKm ?: 0.0, suma)
            MaintenanceCalculator.calculate(odometro, items)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val licenseExpiresAtFlow: Flow<Long?> =
        settings.data.map { it[PreferencesKeys.LICENSE_EXPIRES_AT] }

    /** Edición manual del usuario (Ajustes → JSON); [DocumentStatusCalculator] la usa solo si su cityId coincide con el perfil activo. */
    private val overrideRulesFlow: Flow<PicoYPlacaEngine.CityRules?> = settings.data.map { prefs ->
        prefs[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson)
    }

    val licenseExpiresAt: StateFlow<Long?> =
        licenseExpiresAtFlow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Re-emits every minute so statuses re-evaluate across day/pico-y-placa boundaries. */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(MINUTE_MS)
        }
    }

    val docStatuses: StateFlow<List<DocumentStatusCalculator.DocStatus>> =
        combine(activeProfile, licenseExpiresAtFlow, overrideRulesFlow, minuteTicker) { profile, license, overrideRules, _ ->
            calculateStatuses(profile, license, overrideRules)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private suspend fun calculateStatuses(
        profile: VehicleProfileEntity?,
        license: Long?,
        overrideRules: PicoYPlacaEngine.CityRules?,
    ): List<DocumentStatusCalculator.DocStatus> {
        if (profile == null) return emptyList()
        val documents = DocumentStatusCalculator.fromProfile(profile, license)
        val now = System.currentTimeMillis()
        val aiFallback = aiFallbackFor(profile.picoPlacaCity, overrideRules, now)
        return DocumentStatusCalculator.calculate(documents, overrideRules, now, aiFallbackRules = aiFallback)
    }

    private suspend fun aiFallbackFor(
        cityId: String?,
        overrideRules: PicoYPlacaEngine.CityRules?,
        nowMs: Long,
    ): PicoYPlacaEngine.CityRules? =
        cityId
            ?.takeIf { DocumentStatusCalculator.needsAiFallback(it, overrideRules, nowMs) }
            ?.let { runCatching { aiRulesSource.rulesForCity(it) }.getOrNull() }

    fun setLicenseExpiresAt(value: Long?) {
        viewModelScope.launch {
            runCatching {
                settings.edit { prefs ->
                    if (value != null) prefs[PreferencesKeys.LICENSE_EXPIRES_AT] = value
                    else prefs.remove(PreferencesKeys.LICENSE_EXPIRES_AT)
                }
            }.onFailure { Timber.w(it, "AlDiaViewModel: failed to persist license expiry") }
        }
    }
}
