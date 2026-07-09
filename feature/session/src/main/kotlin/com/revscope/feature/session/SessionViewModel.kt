package com.revscope.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.entities.SessionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val sessionDao: SessionDao,
) : ViewModel() {

    val sessions: StateFlow<List<SessionEntity>> = sessionDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
}
