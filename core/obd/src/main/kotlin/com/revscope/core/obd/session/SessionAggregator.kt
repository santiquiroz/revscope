package com.revscope.core.obd.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator
import com.revscope.core.obd.trip.EcoScoreCalculator
import com.revscope.core.obd.trip.FuelCostCalculator
import kotlinx.coroutines.flow.first
import timber.log.Timber
import kotlin.math.roundToInt

/** Closes a driving session and fills the trip aggregates shown in history/reports. */
class SessionAggregator(
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val imuDao: ImuDao,
    private val settings: DataStore<Preferences>,
) {

    /**
     * Closes the session and fills the trip aggregates.
     * [activeProfileProvider] is called lazily at the point the redline is needed,
     * matching the manager's original "read the latest active profile" semantics.
     */
    suspend fun close(sessionId: Long, activeProfileProvider: () -> VehicleProfileEntity?) {
        val session = sessionDao.getById(sessionId) ?: return
        val maxRpm = telemetryDao.maxValue(sessionId, "0C") ?: 0f
        val maxSpeed = telemetryDao.maxValue(sessionId, "0D") ?: 0f
        val speedPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0D")
        val aggregates = computeTripAggregates(sessionId, activeProfileProvider)
        sessionDao.update(
            session.copy(
                endedAt = System.currentTimeMillis(),
                maxRpm = maxRpm.roundToInt(),
                maxSpeed = maxSpeed.roundToInt(),
                distanceKm = TripStatsCalculator.distanceKm(speedPoints).toFloat(),
                fuelLiters = aggregates?.fuelLiters,
                fuelCostCop = aggregates?.fuelCostCop,
                ecoScore = aggregates?.ecoScore,
            )
        )
    }

    private data class TripAggregates(val fuelLiters: Double?, val fuelCostCop: Double?, val ecoScore: Int?)

    /**
     * Fuel cost (COP) and eco-score for the trip just ended — never lets a calculation
     * error block closing the session, since these are report-only extras.
     */
    private suspend fun computeTripAggregates(
        sessionId: Long,
        activeProfileProvider: () -> VehicleProfileEntity?,
    ): TripAggregates? = runCatching {
        val fuel = computeFuelResult(sessionId)
        TripAggregates(
            fuelLiters = fuel?.liters,
            fuelCostCop = fuel?.costCop,
            ecoScore = computeEcoScore(sessionId, activeProfileProvider),
        )
    }.onFailure { Timber.w(it, "SessionAggregator: failed to compute trip aggregates") }
        .getOrNull()

    private suspend fun computeFuelResult(sessionId: Long): FuelCostCalculator.FuelResult? {
        val precioGalonCop = settings.data.first()[PreferencesKeys.FUEL_PRICE_COP_PER_GALLON]
            ?: DEFAULT_FUEL_PRICE_COP_PER_GALLON
        val fuelRatePoints = telemetryDao.pointsForSessionAndPid(sessionId, PID_FUEL_RATE)
            .map { it.timestamp to it.value.toDouble() }
        FuelCostCalculator.fromFuelRate(fuelRatePoints, precioGalonCop)?.let { return it }
        val mafPoints = telemetryDao.pointsForSessionAndPid(sessionId, PID_MAF)
            .map { it.timestamp to it.value.toDouble() }
        return FuelCostCalculator.fromMaf(mafPoints, precioGalonCop)
    }

    private suspend fun computeEcoScore(sessionId: Long, activeProfileProvider: () -> VehicleProfileEntity?): Int? {
        // ImuPointEntity.gLong is in G — EcoScoreCalculator's thresholds are m/s².
        val accelLongitudinal = imuDao.pointsForSession(sessionId)
            .map { it.gLong.toDouble() * EARTH_GRAVITY_MS2 }
        val rpmPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0C")
            .map { it.timestamp to it.value.toDouble() }
        if (accelLongitudinal.isEmpty() && rpmPoints.isEmpty()) return null
        val redlineRpm = activeProfileProvider()?.redlineRpm ?: DEFAULT_REDLINE_RPM
        return EcoScoreCalculator.calculate(accelLongitudinal, rpmPoints, redlineRpm).score
    }

    companion object {
        private const val PID_FUEL_RATE = "5E"
        private const val PID_MAF = "10"
        private const val DEFAULT_FUEL_PRICE_COP_PER_GALLON = 16_000.0
        private const val DEFAULT_REDLINE_RPM = 10_500
        private const val EARTH_GRAVITY_MS2 = 9.80665
    }
}
