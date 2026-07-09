package com.revscope.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionDao: SessionDao,
    profileDao: VehicleProfileDao,
    sessionManager: ObdSessionManager,
) : ViewModel() {

    val profiles: StateFlow<List<VehicleProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _filter = MutableStateFlow(sessionManager.activeProfile.value?.id)
    val filter: StateFlow<Long?> = _filter.asStateFlow()

    private val allSessions: StateFlow<List<SessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val sessions: StateFlow<List<SessionEntity>> = combine(allSessions, _filter) { all, selectedFilter ->
        filterSessions(all, selectedFilter)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setFilter(profileId: Long?) {
        _filter.value = profileId
    }

    private fun filterSessions(sessions: List<SessionEntity>, filter: Long?): List<SessionEntity> = when (filter) {
        null -> sessions
        NO_VEHICLE_FILTER -> sessions.filter { it.vehicleProfileId == 0L }
        else -> sessions.filter { it.vehicleProfileId == filter }
    }

    fun deleteSession(id: Long) {
        viewModelScope.launch { sessionDao.deleteById(id) }
    }

    // ── Trip comparison selection ────────────────────────────────────────────

    private val _compareCandidate = MutableStateFlow<Long?>(null)
    val compareCandidate: StateFlow<Long?> = _compareCandidate.asStateFlow()

    /**
     * First tap marks the session as run A; tapping a second session launches the
     * comparison via [onReady]; tapping the same one again deselects.
     */
    fun toggleCompare(id: Long, onReady: (Long, Long) -> Unit) {
        val current = _compareCandidate.value
        when {
            current == null -> _compareCandidate.value = id
            current == id -> _compareCandidate.value = null
            else -> {
                _compareCandidate.value = null
                onReady(current, id)
            }
        }
    }

    companion object {
        /** [filter] sentinel meaning "sessions with no vehicle assigned" (vehicleProfileId == 0L). */
        const val NO_VEHICLE_FILTER = -1L
    }
}
