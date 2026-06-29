package com.revscope.core.obd.telemetry

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Polls OBD-II PIDs in priority-based coroutine groups.
 *
 * Priority 1 → every 100 ms
 * Priority 2 → every 500 ms
 * Priority 3 → every 2 000 ms
 *
 * ELM327 is half-duplex — [transportMutex] serializes all send/receive pairs
 * so the three coroutine groups never interleave on the wire.
 */
class PidScheduler(
    private val transport: Transport,
    private val registry: PidRegistry,
) {
    private val excludedPids = mutableSetOf<String>()

    @Volatile
    private var intervalMultiplier: Double = 1.0

    private val transportMutex = Mutex()

    fun observeReadings(): Flow<ObdReading> = channelFlow {
        val producer = this
        coroutineScope {
            launch { pollGroup(1, 100L) { producer.trySend(it) } }
            launch { pollGroup(2, 500L) { producer.trySend(it) } }
            launch { pollGroup(3, 2_000L) { producer.trySend(it) } }
        }
    }

    private suspend fun pollGroup(
        priority: Int,
        baseIntervalMs: Long,
        emit: (ObdReading) -> Unit,
    ) {
        while (true) {
            val intervalMs = (baseIntervalMs * intervalMultiplier).toLong()
            registry.definitionsForPriority(priority)
                .filterNot { it.pid in excludedPids }
                .forEach { def ->
                    transportMutex.withLock { requestPid(def.pid) }?.let { emit(it) }
                }
            delay(intervalMs)
        }
    }

    private suspend fun requestPid(pid: String): ObdReading? {
        val def = registry.getDefinition(pid) ?: return null
        val command = "${def.mode} ${def.pid}\r"
        return try {
            transport.send(command)
            handleResponse(pid, transport.receive())
        } catch (e: Exception) {
            Timber.w(e, "PidScheduler: timeout on $pid, retrying once")
            retryOnce(pid)
        }
    }

    private suspend fun retryOnce(pid: String): ObdReading? {
        val def = registry.getDefinition(pid) ?: return null
        val command = "${def.mode} ${def.pid}\r"
        return try {
            transport.send(command)
            handleResponse(pid, transport.receive())
        } catch (e: Exception) {
            Timber.e(e, "PidScheduler: retry failed for $pid")
            null
        }
    }

    private fun handleResponse(pid: String, response: String): ObdReading? = when {
        ResponseParser.isNoData(response) -> {
            excludedPids += pid
            Timber.i("PidScheduler: excluded $pid — NO DATA")
            null
        }
        ResponseParser.isBufferFull(response) -> {
            intervalMultiplier = (intervalMultiplier * 2.0).coerceAtMost(8.0)
            Timber.w("PidScheduler: BUFFER FULL — multiplier now $intervalMultiplier")
            null
        }
        ResponseParser.isErrorResponse(response) -> {
            Timber.w("PidScheduler: error for $pid — $response")
            null
        }
        else -> registry.parseAndEvaluate(pid, response)
    }
}
