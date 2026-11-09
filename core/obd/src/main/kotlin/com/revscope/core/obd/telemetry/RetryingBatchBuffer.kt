package com.revscope.core.obd.telemetry

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_BUFFER_ROWS = 10_000

internal class RetryingBatchBuffer<T>(
    private val onDrop: (Int) -> Unit,
    private val onFailure: (Exception, Int) -> Unit,
    private val onSuccess: (Int) -> Unit = {},
) {

    private val lock = Any()
    private val flushMutex = Mutex()
    private val rows = mutableListOf<T>()

    fun add(row: T) {
        val dropped = synchronized(lock) {
            rows += row
            trimOldestRows()
        }
        if (dropped > 0) onDrop(dropped)
    }

    suspend fun flush(insert: suspend (List<T>) -> Unit) {
        flushMutex.withLock {
            val snapshot = synchronized(lock) {
                if (rows.isEmpty()) return
                rows.toList().also { rows.clear() }
            }
            try {
                insert(snapshot)
            } catch (e: CancellationException) {
                restore(snapshot)
                throw e
            } catch (e: Exception) {
                restore(snapshot)
                onFailure(e, snapshot.size)
                return@withLock
            }
            onSuccess(snapshot.size)
        }
    }

    private fun restore(snapshot: List<T>) {
        val dropped = synchronized(lock) {
            rows.addAll(0, snapshot)
            trimOldestRows()
        }
        if (dropped > 0) onDrop(dropped)
    }

    private fun trimOldestRows(): Int {
        val excess = (rows.size - MAX_BUFFER_ROWS).coerceAtLeast(0)
        if (excess > 0) rows.subList(0, excess).clear()
        return excess
    }
}
