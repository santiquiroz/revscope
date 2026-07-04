package com.revscope.core.obd.protocol

import com.revscope.core.obd.connection.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Executes the ELM327 initialisation sequence and queries which PIDs the ECU supports.
 *
 * Sequence (from PLAN.md):
 *   1. AT Z  — reset adapter
 *   2. AT E0 — echo off
 *   3. AT L0 — line feeds off
 *   4. AT S0 — spaces off (compact hex responses)
 *   5. AT H0 — headers off
 *   6. AT SP 0 — auto-detect OBD protocol
 *   7. 01 00 — verify ECU is responsive; parse supported PIDs bitmask
 */
class ProtocolNegotiator(private val transport: Transport) {

    companion object {
        // Cheap clones need over a second to reboot after AT Z before accepting commands
        private const val RESET_SETTLE_MS = 1_000L
        private const val COMMAND_TIMEOUT_MS = 2_000L
        // First "01 00" triggers the actual protocol search ("SEARCHING...") — slowest step
        private const val ECU_VERIFY_TIMEOUT_MS = 10_000L
        private const val PID_RANGE_TIMEOUT_MS = 5_000L
    }

    data class NegotiationResult(
        val elmVersion: String,
        val supportedPids: Set<String>,
    )

    /**
     * Runs the full initialisation sequence.
     * Returns [NegotiationResult] on success or a failure wrapping the cause.
     *
     * Callers should not start data polling until this succeeds.
     */
    suspend fun initialize(): Result<NegotiationResult> = try {
        Result.success(runSequence())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private suspend fun runSequence(): NegotiationResult {
        // Step 1: Reset — response includes ELM version banner
        val resetResponse = exchange(ElmCommandBuilder.reset(), COMMAND_TIMEOUT_MS)
        Timber.d("AT Z → $resetResponse")
        delay(RESET_SETTLE_MS) // ELM327 needs time to reboot after reset

        // Step 2–5: Configure adapter output format
        exchange(ElmCommandBuilder.echoOff())
        exchange(ElmCommandBuilder.lineFeedsOff())
        exchange(ElmCommandBuilder.spacesOff())    // enables compact hex mode
        exchange(ElmCommandBuilder.headersOff())
        exchange(ElmCommandBuilder.adaptiveTiming())

        // Step 6: Auto-detect vehicle OBD protocol
        exchange(ElmCommandBuilder.autoProtocol())

        // Step 7: Verify ECU is responding — mandatory before polling.
        // This is where the ELM actually searches for the protocol, so it gets the long timeout.
        val pids00Response = exchange(ElmCommandBuilder.supportedPids00(), ECU_VERIFY_TIMEOUT_MS)
        Timber.d("01 00 → $pids00Response")

        if (ResponseParser.isUnableToConnect(pids00Response) ||
            ResponseParser.isNoData(pids00Response)
        ) {
            error("ECU not responding after AT SP 0: $pids00Response")
        }

        val supportedPids = mutableSetOf<String>()
        supportedPids += ResponseParser.parseSupportedPids(pids00Response)

        // Query extended PID ranges — failures are non-fatal (some ECUs don't support them)
        supportedPids += queryPidRange(ElmCommandBuilder.supportedPids20())
        supportedPids += queryPidRange(ElmCommandBuilder.supportedPids40())
        supportedPids += queryPidRange(ElmCommandBuilder.supportedPids60())

        Timber.i("ECU supported PIDs: $supportedPids")

        // Read firmware version for diagnostics
        val version = exchange(ElmCommandBuilder.printVersion()).let { raw ->
            ResponseParser.cleanResponse(raw).takeIf { it.isNotEmpty() } ?: "Unknown"
        }

        return NegotiationResult(
            elmVersion = version,
            supportedPids = supportedPids,
        )
    }

    private suspend fun queryPidRange(command: String): Set<String> {
        val response = try {
            exchange(command, PID_RANGE_TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "PID range query failed for ${command.trimEnd()}")
            return emptySet()
        }
        if (ResponseParser.isErrorResponse(response)) return emptySet()
        return ResponseParser.parseSupportedPids(response)
    }

    private suspend fun exchange(command: String, timeoutMs: Long = COMMAND_TIMEOUT_MS): String =
        transport.exchange(command, timeoutMs)
}
