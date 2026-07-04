package com.revscope.core.obd.telemetry

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val PID_READ_TIMEOUT_MS = 2_000L
private const val MAX_INTERVAL_MULTIPLIER = 8.0
private const val MAX_CONSECUTIVE_LINK_FAILURES = 3

// One CAN frame carries 7 usable payload bytes: "41" header + PID+data pairs.
// Batches are packed to fit so the ELM never needs ISO-TP multi-frame responses.
private const val SINGLE_FRAME_PAYLOAD_BYTES = 7

/**
 * Polls OBD-II PIDs in priority-based coroutine groups.
 *
 * Priority 1 → every 100 ms
 * Priority 2 → every 500 ms
 * Priority 3 → every 2 000 ms
 *
 * ELM327 is half-duplex — [Transport.exchange] serializes all send/receive pairs
 * at the transport level, so the three coroutine groups (and any external caller,
 * e.g. DTC reads) never interleave on the wire.
 */
class PidScheduler(
    private val transport: Transport,
    private val registry: PidRegistry,
) {
    // Written concurrently from the three priority-group coroutines
    private val excludedPids: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val intervalMultiplier = AtomicReference(1.0)
    private val consecutiveLinkFailures = AtomicInteger(0)

    // CAN protocols accept several PIDs per request; K-line/ISO9141 don't.
    // Flips to false on the first response that doesn't parse as a batch.
    private val batchingSupported = AtomicBoolean(true)

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
            val intervalMs = (baseIntervalMs * intervalMultiplier.get()).toLong()
            val defs = registry.definitionsForPriority(priority)
                .filterNot { it.pid in excludedPids }

            val (mode01, otherModes) = defs.partition { it.mode == "01" }
            if (batchingSupported.get() && mode01.size > 1) {
                packIntoFrames(mode01).forEach { batch ->
                    if (batch.size == 1) requestPid(batch[0].pid)?.let { emit(it) }
                    else requestBatch(batch, emit)
                }
            } else {
                mode01.forEach { def -> requestPid(def.pid)?.let { emit(it) } }
            }
            otherModes.forEach { def -> requestPid(def.pid)?.let { emit(it) } }

            delay(intervalMs)
        }
    }

    /** Greedy-packs definitions into batches whose response fits one CAN frame. */
    private fun packIntoFrames(defs: List<PidDefinition>): List<List<PidDefinition>> {
        val batches = mutableListOf<List<PidDefinition>>()
        var current = mutableListOf<PidDefinition>()
        var frameBytes = 1 // "41" response header
        for (def in defs) {
            val cost = 1 + def.bytes // PID echo byte + data bytes
            if (current.isNotEmpty() && frameBytes + cost > SINGLE_FRAME_PAYLOAD_BYTES) {
                batches += current
                current = mutableListOf()
                frameBytes = 1
            }
            current += def
            frameBytes += cost
        }
        if (current.isNotEmpty()) batches += current
        return batches
    }

    private suspend fun requestBatch(
        batch: List<PidDefinition>,
        emit: (ObdReading) -> Unit,
    ) {
        val command = "01 ${batch.joinToString(" ") { it.pid }}\r"
        val response = try {
            transport.exchange(command, PID_READ_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PidScheduler: batch request failed, polling individually")
            pollIndividually(batch, emit)
            return
        }

        if (ResponseParser.isErrorResponse(response)) {
            if (response.contains("?")) {
                // "?" = the ELM itself rejected the multi-PID syntax — don't retry batches
                Timber.i("PidScheduler: ELM rejected multi-PID syntax — batching disabled")
                batchingSupported.set(false)
            }
            // NO DATA on a batch can't be attributed to one PID — let singles decide
            pollIndividually(batch, emit)
            return
        }

        consecutiveLinkFailures.set(0)
        val parsed = ResponseParser.parseMultiPidResponse(
            response,
            batch.associate { it.pid to it.bytes },
        )
        if (parsed == null) {
            Timber.i("PidScheduler: adapter/protocol rejects multi-PID requests — batching disabled")
            batchingSupported.set(false)
            pollIndividually(batch, emit)
            return
        }

        parsed.forEach { (pid, bytes) -> registry.evaluate(pid, bytes)?.let { emit(it) } }
        // PIDs the ECU left out of the batch response get a single-shot follow-up
        batch.filter { it.pid !in parsed }.forEach { def ->
            requestPid(def.pid)?.let { emit(it) }
        }
    }

    private suspend fun pollIndividually(
        batch: List<PidDefinition>,
        emit: (ObdReading) -> Unit,
    ) {
        batch.forEach { def -> requestPid(def.pid)?.let { emit(it) } }
    }

    private suspend fun requestPid(pid: String): ObdReading? {
        val def = registry.getDefinition(pid) ?: return null
        val command = "${def.mode} ${def.pid}\r"
        return try {
            handleResponse(pid, transport.exchange(command, PID_READ_TIMEOUT_MS)).also {
                consecutiveLinkFailures.set(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PidScheduler: timeout on $pid, retrying once")
            retryOnce(pid)
        }
    }

    private suspend fun retryOnce(pid: String): ObdReading? {
        val def = registry.getDefinition(pid) ?: return null
        val command = "${def.mode} ${def.pid}\r"
        return try {
            handleResponse(pid, transport.exchange(command, PID_READ_TIMEOUT_MS)).also {
                consecutiveLinkFailures.set(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failLinkIfDead(e)
            Timber.e(e, "PidScheduler: retry failed for $pid")
            null
        }
    }

    /**
     * Circuit breaker: a request+retry pair failing means the adapter may be gone
     * (powered off, out of range). After [MAX_CONSECUTIVE_LINK_FAILURES] pairs with
     * zero successes in between, kill the polling flow so the connection layer can
     * surface a real disconnect instead of silently spamming a dead socket forever.
     */
    private fun failLinkIfDead(cause: Exception) {
        val failures = consecutiveLinkFailures.incrementAndGet()
        if (failures >= MAX_CONSECUTIVE_LINK_FAILURES) {
            throw IOException("Adapter link lost — $failures consecutive request failures", cause)
        }
    }

    private fun handleResponse(pid: String, response: String): ObdReading? = when {
        ResponseParser.isNoData(response) -> {
            excludedPids += pid
            Timber.i("PidScheduler: excluded $pid — NO DATA")
            null
        }
        ResponseParser.isBufferFull(response) -> {
            val multiplier = intervalMultiplier.updateAndGet {
                (it * 2.0).coerceAtMost(MAX_INTERVAL_MULTIPLIER)
            }
            Timber.w("PidScheduler: BUFFER FULL — multiplier now $multiplier")
            null
        }
        ResponseParser.isErrorResponse(response) -> {
            Timber.w("PidScheduler: error for $pid — $response")
            null
        }
        else -> registry.parseAndEvaluate(pid, response)
    }
}
