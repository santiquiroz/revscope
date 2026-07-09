package com.revscope.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    routeHolder: LiveRouteHolder,
    private val cameraDao: SpeedCameraDao,
    sessionManager: ObdSessionManager,
) : ViewModel() {

    val route: StateFlow<List<LiveRouteHolder.RoutePoint>> = routeHolder.points

    // Centro inicial cuando no hay viaje activo (mismo patrón que SettingsViewModel)
    private val _initialCenter = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val initialCenter: StateFlow<LiveRouteHolder.RoutePoint?> = _initialCenter.asStateFlow()

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation() {
        runCatching {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()?.let {
            _initialCenter.value = LiveRouteHolder.RoutePoint(it.latitude, it.longitude)
        }
    }

    val speedKmh: StateFlow<Int?> = sessionManager.readings
        .map { it["0D"]?.value?.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cameras = MutableStateFlow<List<SpeedCameraEntity>>(emptyList())
    val cameras: StateFlow<List<SpeedCameraEntity>> = _cameras.asStateFlow()

    init {
        loadLastKnownLocation()
        viewModelScope.launch {
            runCatching { cameraDao.all() }.onSuccess { _cameras.value = it }
        }
    }
}
