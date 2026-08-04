package com.revscope.core.obd.session

import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.ReadinessParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Watches the MIL (check-engine light) while driving. First check 15s after connect
 * (readiness monitors need the ECU settled), then every 120s — cheap enough to run
 * alongside the main telemetry pipeline without starving it.
 */
class MilWatcher(private val alertsEngine: AlertsEngine) {

    private var job: Job? = null

    fun start(scope: CoroutineScope, bt: Transport, onMilReading: (ObdReading) -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(MIL_WATCH_FIRST_DELAY_MS)
            while (true) {
                try {
                    checkMilStatus(bt, onMilReading)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "MilWatcher: MIL watch failed")
                }
                delay(MIL_WATCH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun checkMilStatus(bt: Transport, onMilReading: (ObdReading) -> Unit) {
        val raw = probe(bt, "01 01\r", DTC_TIMEOUT_MS) ?: return
        val status = ReadinessParser.parse(raw) ?: return
        if (!status.milOn) return
        alertsEngine.announceMilOn()
        onMilReading(ObdReading(pid = ObdSessionManager.MIL_PID, value = 1.0, unit = ""))
    }

    /** Best-effort probe that still honors coroutine cancellation. */
    private suspend fun probe(bt: Transport, command: String, timeoutMs: Long): String? =
        try {
            bt.exchange(command, timeoutMs)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    companion object {
        private const val DTC_TIMEOUT_MS = 5_000L
        private const val MIL_WATCH_FIRST_DELAY_MS = 15_000L
        private const val MIL_WATCH_INTERVAL_MS = 120_000L
    }
}
