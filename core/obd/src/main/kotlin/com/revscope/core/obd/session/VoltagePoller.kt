package com.revscope.core.obd.session

import com.revscope.core.obd.connection.ClassicBtTransport
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Polls adapter battery voltage via "AT RV" — answered by the ELM itself, no ECU
 * involved. Emitted as pseudo-PID VBAT so gauges and alerts consume it like any PID.
 */
class VoltagePoller {

    private var job: Job? = null

    fun start(scope: CoroutineScope, bt: ClassicBtTransport, onReading: (ObdReading) -> Unit) {
        job?.cancel()
        job = scope.launch {
            while (true) {
                try {
                    val raw = bt.exchange("AT RV\r", VOLTAGE_TIMEOUT_MS)
                    parseVoltage(raw)?.let { volts ->
                        onReading(ObdReading(pid = ObdSessionManager.VBAT_PID, value = volts, unit = "V"))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "VoltagePoller: voltage poll failed")
                }
                delay(VOLTAGE_POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    companion object {
        private const val VOLTAGE_TIMEOUT_MS = 2_000L
        private const val VOLTAGE_POLL_INTERVAL_MS = 10_000L
        private val VOLTAGE_REGEX = Regex("""(\d{1,2}(?:\.\d{1,2})?)V""")

        fun parseVoltage(raw: String): Double? =
            VOLTAGE_REGEX.find(raw.uppercase())?.groupValues?.get(1)?.toDoubleOrNull()
    }
}
