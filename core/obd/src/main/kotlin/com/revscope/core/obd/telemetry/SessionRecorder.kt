package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

private const val FLUSH_INTERVAL_MS = 500L

/**
 * Persists [ObdReading] items to Room in batches every [FLUSH_INTERVAL_MS].
 *
 * Call [record] with the active session ID and the combined raw+derived flow.
 * Suspends until the flow completes (session ends) and flushes any remaining buffer.
 */
class SessionRecorder(private val telemetryDao: TelemetryDao) {

    suspend fun record(sessionId: Long, readings: Flow<ObdReading>) {
        val buffer = mutableListOf<TelemetryPointEntity>()
        var lastFlushMs = System.currentTimeMillis()

        readings.collect { reading ->
            buffer += reading.toEntity(sessionId)
            val now = System.currentTimeMillis()
            if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
                flushBuffer(buffer)
                lastFlushMs = now
            }
        }

        if (buffer.isNotEmpty()) flushBuffer(buffer)
    }

    private suspend fun flushBuffer(buffer: MutableList<TelemetryPointEntity>) {
        val snapshot = buffer.toList()
        buffer.clear()
        try {
            telemetryDao.insertAll(snapshot)
            Timber.d("SessionRecorder: flushed ${snapshot.size} points")
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
