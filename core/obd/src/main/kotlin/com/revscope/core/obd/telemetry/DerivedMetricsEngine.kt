package com.revscope.core.obd.telemetry

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlin.math.abs

private const val ATMOSPHERIC_KPA = 101.0
private const val POWER_DIVISOR = 9549.0
private const val MIN_RPM_FOR_GEAR = 500.0
private const val MIN_SPEED_KMH_FOR_GEAR = 3.0

private val DEFAULT_GEAR_TABLE = listOf(
    1 to 12.0,
    2 to 20.0,
    3 to 31.0,
    4 to 43.0,
    5 to 56.0,
    6 to 77.0,
)

/**
 * Derives BOOST, GEAR, and POWER readings from the raw OBD-II [Flow].
 *
 * Accumulates the latest value per PID and recalculates only the metrics
 * affected by each incoming reading.
 *
 * Emits [ObdReading] with synthetic PIDs:
 *   "BOOST"  kPa  — MAP - 101 (negative = intake vacuum on NA engines)
 *   "GEAR"   ""   — estimated gear 1–6, or 0 when stopped/neutral
 *   "POWER"  kW   — torque_ref_Nm × torque_pct% × rpm / 9549
 *
 * Call [setGearTable] to replace the static defaults with vehicle-calibrated
 * ratios from AdaptiveGearLearner once it converges.
 */
class DerivedMetricsEngine {

    @Volatile
    private var currentGearTable: List<Pair<Int, Double>> = DEFAULT_GEAR_TABLE

    /** Replace the gear ratio table with a vehicle-specific calibrated set. */
    fun setGearTable(table: List<Pair<Int, Double>>) {
        currentGearTable = table
    }

    fun observeDerived(upstream: Flow<ObdReading>): Flow<ObdReading> = channelFlow {
        val latest = mutableMapOf<String, Double>()
        val channel = this

        upstream.collect { reading ->
            latest[reading.pid] = reading.value
            buildDerived(latest, reading.pid, reading.timestamp)
                .forEach { channel.send(it) }
        }
    }

    private fun buildDerived(
        latest: Map<String, Double>,
        updatedPid: String,
        timestamp: Long,
    ): List<ObdReading> = buildList {
        when (updatedPid) {
            "0B" -> calculateBoost(latest, timestamp)?.let { add(it) }
            "0D" -> calculateGear(latest, timestamp)?.let { add(it) }
            "0C" -> {
                calculateGear(latest, timestamp)?.let { add(it) }
                calculatePower(latest, timestamp)?.let { add(it) }
            }
            "62", "63" -> calculatePower(latest, timestamp)?.let { add(it) }
        }
    }

    private fun calculateBoost(latest: Map<String, Double>, timestamp: Long): ObdReading? {
        val map = latest["0B"] ?: return null
        return ObdReading(pid = "BOOST", value = map - ATMOSPHERIC_KPA, unit = "kPa", timestamp = timestamp)
    }

    private fun calculateGear(latest: Map<String, Double>, timestamp: Long): ObdReading? {
        val rpm = latest["0C"] ?: return null
        val speed = latest["0D"] ?: return null
        val gear = if (rpm < MIN_RPM_FOR_GEAR || speed < MIN_SPEED_KMH_FOR_GEAR) {
            0
        } else {
            val ratio = speed * 1000.0 / rpm
            currentGearTable.minByOrNull { abs(it.second - ratio) }?.first ?: 0
        }
        return ObdReading(pid = "GEAR", value = gear.toDouble(), unit = "", timestamp = timestamp)
    }

    private fun calculatePower(latest: Map<String, Double>, timestamp: Long): ObdReading? {
        val rpm = latest["0C"] ?: return null
        val torquePct = latest["62"] ?: return null  // -125 to +130 %
        val torqueRefNm = latest["63"] ?: return null
        val torqueNm = torqueRefNm * torquePct / 100.0
        val powerKw = torqueNm * rpm / POWER_DIVISOR
        return ObdReading(pid = "POWER", value = powerKw, unit = "kW", timestamp = timestamp)
    }
}
