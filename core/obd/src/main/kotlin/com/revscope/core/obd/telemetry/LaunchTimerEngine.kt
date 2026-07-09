package com.revscope.core.obd.telemetry

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

private const val STANDSTILL_SPEED_KMH = 0.5
private const val ARM_AFTER_STANDSTILL_MS = 700L
private const val TARGET_60 = 60.0
private const val TARGET_100 = 100.0

// A real launch reaches 60 in seconds. Crawling through traffic and touching
// 60 km/h five minutes later is NOT a run — field data showed "0-60" of 53-98 s.
private const val MAX_RUN_TO_60_MS = 15_000L
private const val MAX_RUN_TOTAL_MS = 35_000L

/**
 * Automatic 0–60 / 0–100 km/h timer. Feed it speed readings (PID 0D); it arms
 * itself at standstill, starts on launch, and interpolates the crossing instant
 * between samples so ±200 ms polling still yields believable times.
 */
class LaunchTimerEngine {

    data class LaunchResult(val to60Ms: Long?, val to100Ms: Long?)

    enum class State { IDLE, ARMED, RUNNING }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _results = MutableSharedFlow<LaunchResult>(extraBufferCapacity = 4)
    val results: SharedFlow<LaunchResult> = _results.asSharedFlow()

    // -1 sentinels: a real timestamp of 0 is valid input (tests use relative clocks)
    private var standstillSince = -1L
    private var launchStartMs = 0L
    private var prevSpeed = 0.0
    private var prevTimestamp = -1L
    private var to60Ms: Long? = null

    fun process(reading: ObdReading) {
        if (reading.pid != "0D") return
        val speed = reading.value
        val now = reading.timestamp

        when (_state.value) {
            State.IDLE -> if (speed <= STANDSTILL_SPEED_KMH) {
                if (standstillSince < 0) standstillSince = now
                if (now - standstillSince >= ARM_AFTER_STANDSTILL_MS) {
                    _state.value = State.ARMED
                    Timber.d("LaunchTimer: armed")
                }
            } else {
                standstillSince = -1L
            }

            State.ARMED -> if (speed > STANDSTILL_SPEED_KMH) {
                // Launch! The clock starts at the last standstill sample, not this one —
                // the vehicle began moving somewhere in between.
                launchStartMs = prevTimestamp.takeIf { it >= 0 } ?: now
                to60Ms = null
                _state.value = State.RUNNING
                Timber.i("LaunchTimer: running")
            }

            State.RUNNING -> {
                val elapsed = now - launchStartMs
                if (to60Ms == null && elapsed > MAX_RUN_TO_60_MS) {
                    reset() // too slow to 60 — traffic crawl, not a launch
                    return storePrev(speed, now)
                }
                if (elapsed > MAX_RUN_TOTAL_MS) {
                    if (to60Ms != null) finish(LaunchResult(to60Ms, null)) else reset()
                    return storePrev(speed, now)
                }
                if (to60Ms == null && speed >= TARGET_60) {
                    to60Ms = interpolateCrossing(TARGET_60, now, speed)
                }
                if (speed >= TARGET_100) {
                    finish(LaunchResult(to60Ms, interpolateCrossing(TARGET_100, now, speed)))
                } else if (speed <= STANDSTILL_SPEED_KMH) {
                    // Aborted run — report a partial only if 0–60 was achieved
                    if (to60Ms != null) finish(LaunchResult(to60Ms, null)) else reset()
                }
            }
        }

        storePrev(speed, now)
    }

    private fun storePrev(speed: Double, now: Long) {
        prevSpeed = speed
        prevTimestamp = now
    }

    fun reset() {
        _state.value = State.IDLE
        standstillSince = -1L
        to60Ms = null
    }

    private fun finish(result: LaunchResult) {
        Timber.i("LaunchTimer: result $result")
        _results.tryEmit(result)
        reset()
    }

    /** Linear interpolation of the instant [target] km/h was crossed between the last two samples. */
    private fun interpolateCrossing(target: Double, now: Long, speed: Double): Long {
        val dv = speed - prevSpeed
        val crossingTime = if (dv <= 0 || prevTimestamp < 0) {
            now
        } else {
            prevTimestamp + ((target - prevSpeed) / dv * (now - prevTimestamp)).toLong()
        }
        return (crossingTime - launchStartMs).coerceAtLeast(0)
    }
}
