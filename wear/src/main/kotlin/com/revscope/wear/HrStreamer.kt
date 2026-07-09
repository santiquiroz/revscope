package com.revscope.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val TAG = "HrStreamer"
private const val HR_MESSAGE_PATH = "/revscope/hr"

/**
 * Measures heart rate via Health Services and streams each reading to every
 * paired node (the phone) over the Data Layer. Payload: "timestampMs;bpm".
 */
class HrStreamer(private val context: Context) {

    private var scope: CoroutineScope? = null

    private val _latestBpm = MutableStateFlow<Double?>(null)
    val latestBpm: StateFlow<Double?> = _latestBpm.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _sentCount = MutableStateFlow(0)
    val sentCount: StateFlow<Int> = _sentCount.asStateFlow()

    private val measureClient by lazy { HealthServices.getClient(context).measureClient }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.i(TAG, "availability: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val bpm = data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.value ?: return
            if (bpm <= 0) return
            _latestBpm.value = bpm
            send(bpm)
        }
    }

    fun start() {
        if (_isStreaming.value) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        _isStreaming.value = true
        _sentCount.value = 0
        Log.i(TAG, "HR streaming started")
    }

    fun stop() {
        if (!_isStreaming.value) return
        runCatching { measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback) }
        scope?.cancel()
        scope = null
        _isStreaming.value = false
        Log.i(TAG, "HR streaming stopped")
    }

    private fun send(bpm: Double) {
        val activeScope = scope ?: return
        activeScope.launch {
            try {
                val payload = "${System.currentTimeMillis()};$bpm".toByteArray(Charsets.UTF_8)
                nodeClient.connectedNodes.await().forEach { node ->
                    messageClient.sendMessage(node.id, HR_MESSAGE_PATH, payload).await()
                }
                _sentCount.value = _sentCount.value + 1
            } catch (e: Exception) {
                Log.w(TAG, "send failed", e)
            }
        }
    }
}
