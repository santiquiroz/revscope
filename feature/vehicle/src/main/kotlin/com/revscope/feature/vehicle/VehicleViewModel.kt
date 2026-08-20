package com.revscope.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.common.stateInSafe
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val profileDao: VehicleProfileDao,
    private val registry: PidRegistry,
    private val sessionManager: ObdSessionManager,
) : ViewModel() {

    val profiles: StateFlow<List<VehicleProfileEntity>> = profileDao.observeAll()
        .stateInSafe(viewModelScope, emptyList(), started = SharingStarted.Eagerly)

    val availablePids: List<PidDefinition> = registry.allDefinitions().sortedBy { it.nameEs }

    // Form state for new/edit profile
    private val _formName = MutableStateFlow("")
    val formName: StateFlow<String> = _formName.asStateFlow()

    private val _formType = MutableStateFlow("CAR")
    val formType: StateFlow<String> = _formType.asStateFlow()

    /** "CORRIENTE" | "EXTRA" | "DIESEL" — ver VehicleProfileEntity.fuelType */
    private val _formFuelType = MutableStateFlow("CORRIENTE")
    val formFuelType: StateFlow<String> = _formFuelType.asStateFlow()

    private val _formVin = MutableStateFlow("")
    val formVin: StateFlow<String> = _formVin.asStateFlow()

    private val _formEnabledPids = MutableStateFlow(setOf("0C", "0D", "05"))
    val formEnabledPids: StateFlow<Set<String>> = _formEnabledPids.asStateFlow()

    private val _editingProfile = MutableStateFlow<VehicleProfileEntity?>(null)
    val editingProfile: StateFlow<VehicleProfileEntity?> = _editingProfile.asStateFlow()

    private val _formMaxRpm = MutableStateFlow("8000")
    val formMaxRpm: StateFlow<String> = _formMaxRpm.asStateFlow()

    private val _formRedlineRpm = MutableStateFlow("6500")
    val formRedlineRpm: StateFlow<String> = _formRedlineRpm.asStateFlow()

    private val _formGearCount = MutableStateFlow("6")
    val formGearCount: StateFlow<String> = _formGearCount.asStateFlow()

    private val _vinStatus = MutableStateFlow<String?>(null)
    val vinStatus: StateFlow<String?> = _vinStatus.asStateFlow()

    private val _adapterLinkStatus = MutableStateFlow<String?>(null)
    val adapterLinkStatus: StateFlow<String?> = _adapterLinkStatus.asStateFlow()

    private val _formPlate = MutableStateFlow("")
    val formPlate: StateFlow<String> = _formPlate.asStateFlow()

    /** null = sin pico y placa; ver CityRegistry.CITIES para los ids de ciudad soportados */
    private val _formPicoPlacaCity = MutableStateFlow<String?>(null)
    val formPicoPlacaCity: StateFlow<String?> = _formPicoPlacaCity.asStateFlow()

    private val _formSoatExpiresAt = MutableStateFlow<Long?>(null)
    val formSoatExpiresAt: StateFlow<Long?> = _formSoatExpiresAt.asStateFlow()

    private val _formRtmExpiresAt = MutableStateFlow<Long?>(null)
    val formRtmExpiresAt: StateFlow<Long?> = _formRtmExpiresAt.asStateFlow()

    private val _formInsuranceExpiresAt = MutableStateFlow<Long?>(null)
    val formInsuranceExpiresAt: StateFlow<Long?> = _formInsuranceExpiresAt.asStateFlow()

    fun setName(v: String) { _formName.value = v }

    fun setType(type: String) {
        _formType.value = type
        if (_editingProfile.value == null) applyTypeDefaults(type)
    }

    private fun applyTypeDefaults(type: String) {
        if (type == "MOTORCYCLE") {
            _formMaxRpm.value = "12000"
            _formRedlineRpm.value = "10500"
            _formGearCount.value = "5"
        } else {
            _formMaxRpm.value = "8000"
            _formRedlineRpm.value = "6500"
            _formGearCount.value = "6"
        }
    }

    fun setFuelType(v: String) { _formFuelType.value = v }
    fun setVin(v: String) { _formVin.value = v }
    fun setMaxRpm(v: String) { _formMaxRpm.value = v }
    fun setRedlineRpm(v: String) { _formRedlineRpm.value = v }
    fun setGearCount(v: String) { _formGearCount.value = v.filter { it.isDigit() } }
    fun setPlate(v: String) { _formPlate.value = v.uppercase() }
    fun setPicoPlacaCity(v: String?) { _formPicoPlacaCity.value = v }
    fun setSoatExpiresAt(v: Long?) { _formSoatExpiresAt.value = v }
    fun setRtmExpiresAt(v: Long?) { _formRtmExpiresAt.value = v }
    fun setInsuranceExpiresAt(v: Long?) { _formInsuranceExpiresAt.value = v }

    /** Reads the VIN from the connected vehicle (Mode 09 02) into the form. */
    fun readVinFromVehicle(connectionVm: ConnectionViewModel) {
        viewModelScope.launch {
            _vinStatus.value = "Leyendo VIN…"
            val vin = connectionVm.rawExchange("09 02\r", 4_000)
                .getOrNull()
                ?.let { ResponseParser.parseVinResponse(it) }
            if (vin != null) {
                _formVin.value = vin
                _vinStatus.value = "VIN leído"
            } else {
                _vinStatus.value = "El vehículo no reporta VIN por OBD"
            }
        }
    }

    /** Links the currently connected adapter to the profile being edited (one-adapter-one-profile). */
    fun linkConnectedAdapter() {
        val profile = _editingProfile.value ?: return
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return
        val address = sessionManager.lastAdapterAddress.value ?: return
        viewModelScope.launch {
            profileDao.clearAdapterLinkExcept(address, profile.id)
            val updated = profile.copy(adapterAddress = address)
            profileDao.update(updated)
            sessionManager.notifyProfileUpdated(updated)
            _editingProfile.value = updated
            _adapterLinkStatus.value = "Adaptador vinculado a este perfil"
        }
    }

    /** Removes the adapter link from the profile being edited. */
    fun unlinkAdapter() {
        val profile = _editingProfile.value ?: return
        if (profile.adapterAddress == null) return
        viewModelScope.launch {
            val updated = profile.copy(adapterAddress = null)
            profileDao.update(updated)
            sessionManager.notifyProfileUpdated(updated)
            _editingProfile.value = updated
            _adapterLinkStatus.value = "Adaptador desvinculado"
        }
    }

    /** Loads an existing profile into the form; [saveProfile] then updates instead of inserting. */
    fun startEditing(profile: VehicleProfileEntity) {
        _adapterLinkStatus.value = null
        _editingProfile.value = profile
        _formName.value = profile.name
        _formType.value = profile.type
        _formFuelType.value = profile.fuelType
        _formVin.value = profile.vin.orEmpty()
        _formEnabledPids.value = parseEnabledPids(profile.enabledPids)
        _formMaxRpm.value = profile.maxRpm.toString()
        _formRedlineRpm.value = profile.redlineRpm.toString()
        _formGearCount.value = profile.gearCount.toString()
        _formPlate.value = profile.plate.orEmpty()
        _formPicoPlacaCity.value = profile.picoPlacaCity
        _formSoatExpiresAt.value = profile.soatExpiresAt
        _formRtmExpiresAt.value = profile.rtmExpiresAt
        _formInsuranceExpiresAt.value = profile.insuranceExpiresAt
    }

    fun cancelEditing() = resetForm()

    private fun parseEnabledPids(json: String?): Set<String> = try {
        if (json.isNullOrBlank()) DEFAULT_PIDS
        else {
            val array = JSONArray(json)
            buildSet { for (i in 0 until array.length()) add(array.getString(i)) }
                .ifEmpty { DEFAULT_PIDS }
        }
    } catch (_: Exception) {
        DEFAULT_PIDS
    }

    fun togglePid(pid: String) {
        val current = _formEnabledPids.value
        _formEnabledPids.value = if (pid in current) current - pid else current + pid
    }

    fun saveProfile() {
        val name = _formName.value.trim()
        if (name.isEmpty()) return
        val maxRpm = (_formMaxRpm.value.toIntOrNull() ?: 8_000).coerceIn(3_000, 20_000)
        val redline = (_formRedlineRpm.value.toIntOrNull() ?: 6_500).coerceIn(2_000, maxRpm)
        val gearCount = (_formGearCount.value.toIntOrNull() ?: 6).coerceIn(3, 8)
        viewModelScope.launch {
            val editing = _editingProfile.value
            val plate = _formPlate.value.trim().ifEmpty { null }
            if (editing != null) {
                val updated = editing.copy(
                    name = name,
                    type = _formType.value,
                    fuelType = _formFuelType.value,
                    vin = _formVin.value.trim().ifEmpty { null },
                    enabledPids = JSONArray(_formEnabledPids.value.toList()).toString(),
                    maxRpm = maxRpm,
                    redlineRpm = redline,
                    gearCount = gearCount,
                    plate = plate,
                    picoPlacaCity = _formPicoPlacaCity.value,
                    soatExpiresAt = _formSoatExpiresAt.value,
                    rtmExpiresAt = _formRtmExpiresAt.value,
                    insuranceExpiresAt = _formInsuranceExpiresAt.value,
                )
                profileDao.update(updated)
                sessionManager.notifyProfileUpdated(updated)
            } else {
                profileDao.insert(
                    VehicleProfileEntity(
                        name = name,
                        type = _formType.value,
                        fuelType = _formFuelType.value,
                        vin = _formVin.value.trim().ifEmpty { null },
                        enabledPids = JSONArray(_formEnabledPids.value.toList()).toString(),
                        gearRatios = null,
                        createdAt = System.currentTimeMillis(),
                        maxRpm = maxRpm,
                        redlineRpm = redline,
                        gearCount = gearCount,
                        plate = plate,
                        picoPlacaCity = _formPicoPlacaCity.value,
                        soatExpiresAt = _formSoatExpiresAt.value,
                        rtmExpiresAt = _formRtmExpiresAt.value,
                        insuranceExpiresAt = _formInsuranceExpiresAt.value,
                    )
                )
            }
            resetForm()
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch {
            profileDao.deleteById(id)
            if (_editingProfile.value?.id == id) resetForm()
        }
    }

    private fun resetForm() {
        _editingProfile.value = null
        _formName.value = ""
        _formType.value = "CAR"
        _formFuelType.value = "CORRIENTE"
        _formVin.value = ""
        _formEnabledPids.value = DEFAULT_PIDS
        _formMaxRpm.value = "8000"
        _formRedlineRpm.value = "6500"
        _formGearCount.value = "6"
        _vinStatus.value = null
        _adapterLinkStatus.value = null
        _formPlate.value = ""
        _formPicoPlacaCity.value = null
        _formSoatExpiresAt.value = null
        _formRtmExpiresAt.value = null
        _formInsuranceExpiresAt.value = null
    }

    private companion object {
        val DEFAULT_PIDS = setOf("0C", "0D", "05")
    }
}
