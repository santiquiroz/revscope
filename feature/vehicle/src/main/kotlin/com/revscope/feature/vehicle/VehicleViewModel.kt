package com.revscope.feature.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
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

    fun setName(v: String) { _formName.value = v }
    fun setType(v: String) { _formType.value = v }
    fun setVin(v: String) { _formVin.value = v }

    /** Loads an existing profile into the form; [saveProfile] then updates instead of inserting. */
    fun startEditing(profile: VehicleProfileEntity) {
        _editingProfile.value = profile
        _formName.value = profile.name
        _formType.value = profile.type
        _formVin.value = profile.vin.orEmpty()
        _formEnabledPids.value = parseEnabledPids(profile.enabledPids)
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
        viewModelScope.launch {
            val editing = _editingProfile.value
            if (editing != null) {
                profileDao.update(
                    editing.copy(
                        name = name,
                        type = _formType.value,
                        vin = _formVin.value.trim().ifEmpty { null },
                        enabledPids = JSONArray(_formEnabledPids.value.toList()).toString(),
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
    }

    private companion object {
        val DEFAULT_PIDS = setOf("0C", "0D", "05")
    }
}
