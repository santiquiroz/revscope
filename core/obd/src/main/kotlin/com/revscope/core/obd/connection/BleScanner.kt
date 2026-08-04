package com.revscope.core.obd.connection

import android.content.Context
import com.welie.blessed.BluetoothCentralManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_SCAN_DURATION_MS = 12_000L

/**
 * Escaneo BLE bajo demanda para descubrir adaptadores ELM327 BLE — estos NO aparecen
 * en la lista de emparejados (la mayoría ni siquiera soporta bonding clásico).
 * Sin filtro de servicio: muchos clones no anuncian su UUID de servicio en el
 * advertisement, así que se listan todos los dispositivos con nombre y el usuario elige.
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Device(val name: String, val address: String, val rssi: Int)

    private val central by lazy { BluetoothCentralManager(context) }

    private val _results = MutableStateFlow<List<Device>>(emptyList())
    val results: StateFlow<List<Device>> = _results.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var stopJob: Job? = null

    fun start(scope: CoroutineScope, durationMs: Long = DEFAULT_SCAN_DURATION_MS) {
        if (_scanning.value) return
        _results.value = emptyList()
        runCatching {
            central.scanForPeripherals(
                { peripheral, scanResult ->
                    val name = peripheral.name
                    if (name.isBlank()) return@scanForPeripherals
                    val device = Device(name, peripheral.address, scanResult.rssi)
                    _results.value = (_results.value.filterNot { it.address == device.address } + device)
                        .sortedByDescending { it.rssi }
                },
                { failure ->
                    Timber.w("BleScanner: scan failed with $failure")
                    _scanning.value = false
                },
            )
            _scanning.value = true
            stopJob?.cancel()
            stopJob = scope.launch {
                delay(durationMs)
                stop()
            }
        }.onFailure { Timber.w(it, "BleScanner: could not start scan") }
    }

    fun stop() {
        runCatching { central.stopScan() }
        _scanning.value = false
        stopJob?.cancel()
        stopJob = null
    }
}
