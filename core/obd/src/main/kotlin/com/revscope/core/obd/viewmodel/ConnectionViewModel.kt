package com.revscope.core.obd.viewmodel

import androidx.lifecycle.ViewModel
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Thin UI facade over [ObdSessionManager]. The manager owns the connection at
 * application scope — this ViewModel only adapts it to Compose screens, so screen
 * navigation and Activity death never touch the Bluetooth link.
 */
@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val manager: ObdSessionManager,
    alertsEngine: AlertsEngine,
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = manager.connectionState
    val readings: StateFlow<Map<String, ObdReading>> = manager.readings
    val lastAdapterAddress: StateFlow<String?> = manager.lastAdapterAddress
    val activeProfile: StateFlow<VehicleProfileEntity?> = manager.activeProfile
    val lastReadVin: StateFlow<String?> = manager.lastReadVin
    val alerts: SharedFlow<AlertsEngine.ObdAlert> = alertsEngine.alerts
    val launchResults = manager.launchResults
    val isGpsTripActive: StateFlow<Boolean> = manager.isGpsSessionActive

    fun setActiveProfile(profile: VehicleProfileEntity?) = manager.setActiveProfile(profile)

    fun setGearTable(table: List<Pair<Int, Double>>) = manager.setGearTable(table)

    fun connectToDevice(deviceAddress: String) = manager.connectToDevice(deviceAddress)

    fun reconnectToLast() = manager.reconnectToLast()

    fun disconnect() = manager.disconnect()

    /** Starts a GPS-only trip ("viaje sin adaptador") — no Bluetooth link required. */
    fun startGpsTrip() = manager.startGpsSession()

    fun stopGpsTrip() = manager.stopGpsSession()

    suspend fun readActiveDtc(): Result<List<DtcCode>> = manager.readActiveDtc()

    suspend fun clearDtcCodes(): Result<Unit> = manager.clearDtcCodes()

    suspend fun rawExchange(command: String, timeoutMs: Long = 5_000L): Result<String> =
        manager.rawExchange(command, timeoutMs)

    suspend fun probeModule(
        requestHeader: String,
        request: String,
        timeoutMs: Long = 1_500L,
    ): Result<String> = manager.probeModule(requestHeader, request, timeoutMs)

    suspend fun protocolNumber(): String? = manager.currentProtocolNumber()
}
