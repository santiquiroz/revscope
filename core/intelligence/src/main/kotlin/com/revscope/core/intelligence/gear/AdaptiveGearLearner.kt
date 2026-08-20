package com.revscope.core.intelligence.gear

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.telemetry.GearDefaults
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
 * assign it to the nearest of [gearCount] clusters (one per gear), then nudge that
 * cluster's centroid toward the new observation using EMA (alpha = [LEARNING_RATE]).
 *
 * Until [isCalibrated] returns true the static default table (from [GearDefaults]) is
 * reported. Once calibrated [gearTable] emits a live [List<GearCluster>] for
 * DerivedMetricsEngine.
 *
 * No es thread-safe: [observe] y [reconfigure] deben llamarse desde el mismo dispatcher
 * (hoy Main vía DashboardViewModel).
 */
class AdaptiveGearLearner(
    gearCount: Int = 6,
    type: VehicleType = VehicleType.CAR,
) {

    companion object {
        private const val LEARNING_RATE = 0.05
        const val MIN_OBSERVATIONS_PER_GEAR = 30
        private const val MIN_RPM = 500.0
        private const val MIN_SPEED_KMH = 3.0

        private fun buildClusters(gearCount: Int, type: VehicleType): List<GearCluster> =
            GearDefaults.ratios(gearCount, type).mapIndexed { i, ratio -> GearCluster(gear = i + 1, centerRatio = ratio) }
    }

    private var appliedGearCount = gearCount
    private var appliedType = type

    private val _gearTable = MutableStateFlow(buildClusters(gearCount, type))
    val gearTable: StateFlow<List<GearCluster>> = _gearTable.asStateFlow()

    private var latestRpm: Double? = null
    private var latestSpeed: Double? = null

    /**
     * Re-arma la tabla de clusters para [gearCount]/[type], pero solo si cambiaron desde la
     * última llamada. Reconectar el BLE no es cambiar de vehículo: IntelligenceOrchestrator
     * llama esto en cada `start()`, y start() se re-dispara en cada reconexión (rutina en
     * este hardware) — sin el guard, cada dropout resetearía observationCount a 0 y la
     * calibración nunca sobreviviría un viaje real. La identidad del learner tampoco cambia
     * nunca: IntelligenceOrchestrator es un singleton construido antes de conocer el perfil
     * activo, así que los StateFlow externos ya suscritos a [gearTable] deben seguir vivos.
     */
    fun reconfigure(gearCount: Int, type: VehicleType) {
        if (gearCount == appliedGearCount && type == appliedType) return
        appliedGearCount = gearCount
        appliedType = type
        latestRpm = null
        latestSpeed = null
        _gearTable.value = buildClusters(gearCount, type)
    }

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
