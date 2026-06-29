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

    fun setName(v: String) { _formName.value = v }
    fun setType(v: String) { _formType.value = v }
    fun setVin(v: String) { _formVin.value = v }

    fun togglePid(pid: String) {
        val current = _formEnabledPids.value
        _formEnabledPids.value = if (pid in current) current - pid else current + pid
    }

    fun saveProfile() {
        val name = _formName.value.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
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
            resetForm()
        }
    }

    fun deleteProfile(id: Long) {
        viewModelScope.launch { profileDao.deleteById(id) }
    }

    private fun resetForm() {
        _formName.value = ""
        _formType.value = "CAR"
        _formVin.value = ""
        _formEnabledPids.value = setOf("0C", "0D", "05")
    }
}
