package com.revscope.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val profileDao: VehicleProfileDao,
    private val registry: PidRegistry,
) : ViewModel() {

    val profiles: StateFlow<List<VehicleProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availablePids: List<PidDefinition> = registry.allDefinitions().sortedBy { it.nameEs }

    // Form state for new/edit profile
    private val _formName = MutableStateFlow("")
    val formName: StateFlow<String> = _formName.asStateFlow()

    private val _formType = MutableStateFlow("CAR")
    val formType: StateFlow<String> = _formType.asStateFlow()

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

    private val _vinStatus = MutableStateFlow<String?>(null)
    val vinStatus: StateFlow<String?> = _vinStatus.asStateFlow()

    fun setName(v: String) { _formName.value = v }
    fun setType(v: String) { _formType.value = v }
    fun setVin(v: String) { _formVin.value = v }
    fun setMaxRpm(v: String) { _formMaxRpm.value = v }
    fun setRedlineRpm(v: String) { _formRedlineRpm.value = v }

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

    /** Loads an existing profile into the form; [saveProfile] then updates instead of inserting. */
    fun startEditing(profile: VehicleProfileEntity) {
        _editingProfile.value = profile
        _formName.value = profile.name
        _formType.value = profile.type
        _formVin.value = profile.vin.orEmpty()
        _formEnabledPids.value = parseEnabledPids(profile.enabledPids)
        _formMaxRpm.value = profile.maxRpm.toString()
        _formRedlineRpm.value = profile.redlineRpm.toString()
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
        viewModelScope.launch {
            val editing = _editingProfile.value
            if (editing != null) {
                profileDao.update(
                    editing.copy(
                        name = name,
                        type = _formType.value,
                        vin = _formVin.value.trim().ifEmpty { null },
                        enabledPids = JSONArray(_formEnabledPids.value.toList()).toString(),
                        maxRpm = maxRpm,
                        redlineRpm = redline,
                    )
                )
            } else {
                profileDao.insert(
                    VehicleProfileEntity(
                        name = name,
                        type = _formType.value,
                        vin = _formVin.value.trim().ifEmpty { null },
                        enabledPids = JSONArray(_formEnabledPids.value.toList()).toString(),
                        gearRatios = null,
                        createdAt = System.currentTimeMillis(),
                        maxRpm = maxRpm,
                        redlineRpm = redline,
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
        _formVin.value = ""
        _formEnabledPids.value = DEFAULT_PIDS
        _formMaxRpm.value = "8000"
        _formRedlineRpm.value = "6500"
        _vinStatus.value = null
    }

    private companion object {
        val DEFAULT_PIDS = setOf("0C", "0D", "05")
    }
}
