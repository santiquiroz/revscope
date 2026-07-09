package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber

private const val FLUSH_INTERVAL_MS = 500L
private const val DEDUPE_KEEPALIVE_MS = 4_000L

/**
 * Persists [ObdReading] items to Room in batches every [FLUSH_INTERVAL_MS].
 *
 * Call [record] with the active session ID and the combined raw+derived flow.
 * Suspends until the flow completes (session ends) and flushes any remaining buffer.
 */
class SessionRecorder(private val telemetryDao: TelemetryDao) {

    suspend fun record(sessionId: Long, readings: Flow<ObdReading>) {
        val buffer = mutableListOf<TelemetryPointEntity>()
        val lastValueByPid = mutableMapOf<String, Double>()
        val lastStoredMsByPid = mutableMapOf<String, Long>()
        var lastFlushMs = System.currentTimeMillis()

        try {
            readings.collect { reading ->
                // Derived PIDs (GEAR) re-emit on every raw update — field data showed
                // 111k GEAR rows (2× the raw PIDs). Skip identical consecutive values,
                // but keep a heartbeat row every 4 s so time-integration (distance uses
                // a 5 s max gap) never sees artificial holes during constant cruising.
                val unchanged = lastValueByPid[reading.pid] == reading.value
                val freshEnough =
                    reading.timestamp - (lastStoredMsByPid[reading.pid] ?: 0L) < DEDUPE_KEEPALIVE_MS
                if (unchanged && freshEnough) return@collect
                lastValueByPid[reading.pid] = reading.value
                lastStoredMsByPid[reading.pid] = reading.timestamp
                buffer += reading.toEntity(sessionId)
                val now = System.currentTimeMillis()
                if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
                    flushBuffer(buffer)
                    lastFlushMs = now
                }
            }
        } finally {
            // Sessions end by cancellation (disconnect/onCleared) or link loss — the
            // final flush must survive both, or the last 500 ms of every trip is lost
            // and the aggregates computed right after read an incomplete table.
            if (buffer.isNotEmpty()) {
                withContext(NonCancellable) { flushBuffer(buffer) }
            }
        }
    }

    private suspend fun flushBuffer(buffer: MutableList<TelemetryPointEntity>) {
        val snapshot = buffer.toList()
        buffer.clear()
        try {
            telemetryDao.insertAll(snapshot)
            Timber.d("SessionRecorder: flushed ${snapshot.size} points")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SessionRecorder: flush failed (${snapshot.size} points lost)")
        }
    }

    private fun ObdReading.toEntity(sessionId: Long) = TelemetryPointEntity(
        sessionId = sessionId,
        timestamp = timestamp,
        pid = pid,
        value = value.toFloat(),
    )
}
