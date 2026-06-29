package com.revscope.core.intelligence.gear

import com.revscope.core.obd.model.ObdReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import kotlin.math.abs

/**
 * Online gear ratio learner using exponential moving average per cluster.
 *
 * Observes RPM + speed readings and converges to the actual gear ratios of the
 * connected vehicle over the first [MIN_OBSERVATIONS_PER_GEAR] observations per gear.
 *
 * Algorithm: for each RPM+speed pair compute `ratio = speed_kmh * 1000 / rpm`,
 * assign it to the nearest of 6 clusters (one per gear), then nudge that cluster's
 * centroid toward the new observation using EMA (alpha = [LEARNING_RATE]).
 *
 * Until [isCalibrated] returns true the static default table is reported.
 * Once calibrated [gearTable] emits a live [List<GearCluster>] for DerivedMetricsEngine.
 */
class AdaptiveGearLearner {

    companion object {
        private const val LEARNING_RATE = 0.05
        private const val MIN_OBSERVATIONS_PER_GEAR = 30
        private const val MIN_RPM = 500.0
        private const val MIN_SPEED_KMH = 3.0

        val DEFAULT_CLUSTERS = listOf(
            GearCluster(gear = 1, centerRatio = 12.0),
            GearCluster(gear = 2, centerRatio = 20.0),
            GearCluster(gear = 3, centerRatio = 31.0),
            GearCluster(gear = 4, centerRatio = 43.0),
            GearCluster(gear = 5, centerRatio = 56.0),
            GearCluster(gear = 6, centerRatio = 77.0),
        )
    }

    private val _gearTable = MutableStateFlow(DEFAULT_CLUSTERS)
    val gearTable: StateFlow<List<GearCluster>> = _gearTable.asStateFlow()

    private var latestRpm: Double? = null
    private var latestSpeed: Double? = null

    fun observe(reading: ObdReading) {
        when (reading.pid) {
            "0C" -> latestRpm = reading.value
            "0D" -> latestSpeed = reading.value
        }
        val rpm = latestRpm ?: return
        val speed = latestSpeed ?: return
        if (rpm < MIN_RPM || speed < MIN_SPEED_KMH) return

        update(ratio = speed * 1000.0 / rpm)
    }

    fun isCalibrated(): Boolean =
        _gearTable.value.all { it.observationCount >= MIN_OBSERVATIONS_PER_GEAR }

    fun toRatioTable(): List<Pair<Int, Double>> =
        _gearTable.value.map { it.gear to it.centerRatio }

    private fun update(ratio: Double) {
        val current = _gearTable.value
        val nearest = current.minByOrNull { abs(it.centerRatio - ratio) } ?: return

        val newCenter = nearest.centerRatio + LEARNING_RATE * (ratio - nearest.centerRatio)
        val updated = current.map { cluster ->
            if (cluster.gear == nearest.gear) {
                cluster.copy(
                    centerRatio = newCenter,
                    observationCount = cluster.observationCount + 1,
                )
            } else cluster
        }
        _gearTable.value = updated

        if (isCalibrated() && updated != current) {
            Timber.i("AdaptiveGearLearner: calibrated — table ${updated.map { "G${it.gear}=%.1f".format(it.centerRatio) }}")
        }
    }
}
