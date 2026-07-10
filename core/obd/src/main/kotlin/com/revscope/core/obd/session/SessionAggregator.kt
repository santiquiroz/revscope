package com.revscope.core.obd.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.FuelPricePrefs
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.obd.telemetry.TripStatsCalculator
import com.revscope.core.obd.trip.EcoScoreCalculator
import com.revscope.core.obd.trip.FuelCostCalculator
import timber.log.Timber
import kotlin.math.roundToInt

/** Closes a driving session and fills the trip aggregates shown in history/reports. */
class SessionAggregator(
    private val sessionDao: SessionDao,
    private val telemetryDao: TelemetryDao,
    private val imuDao: ImuDao,
    private val settings: DataStore<Preferences>,
    private val gpsDao: GpsDao,
) {

    /**
     * Closes the session and fills the trip aggregates.
     * [activeProfileProvider] is called lazily at the point the redline is needed,
     * matching the manager's original "read the latest active profile" semantics.
     */
    suspend fun close(sessionId: Long, activeProfileProvider: () -> VehicleProfileEntity?) {
        val session = sessionDao.getById(sessionId) ?: return
        val maxRpm = telemetryDao.maxValue(sessionId, "0C") ?: 0f
        val obdMaxSpeed = telemetryDao.maxValue(sessionId, "0D") ?: 0f
        val speedPoints = telemetryDao.pointsForSessionAndPid(sessionId, "0D")
        val motion = resolveTripMotion(sessionId, obdMaxSpeed, speedPoints)
        val aggregates = computeTripAggregates(sessionId, activeProfileProvider)
        sessionDao.update(
            session.copy(
                endedAt = System.currentTimeMillis(),
                maxRpm = maxRpm.roundToInt(),
                maxSpeed = motion.maxSpeed.roundToInt(),
                distanceKm = motion.distanceKm,
                fuelLiters = aggregates?.fuelLiters,
                fuelCostCop = aggregates?.fuelCostCop,
                ecoScore = aggregates?.ecoScore,
            )
        )
    }

    /**
     * GPS-only trips have no "0D" telemetry — falls back to the recorded GPS track
     * (haversine distance + max GPS speed) ONLY when no OBD speed points exist at all,
     * so a session with an adapter is never touched by this branch.
     */
    private suspend fun resolveTripMotion(
        sessionId: Long,
        obdMaxSpeed: Float,
        obdSpeedPoints: List<TelemetryPointEntity>,
    ): TripMotion {
        if (obdSpeedPoints.isNotEmpty()) {
            return TripMotion(
                maxSpeed = obdMaxSpeed,
                distanceKm = TripStatsCalculator.distanceKm(obdSpeedPoints).toFloat(),
            )
        }
        return gpsFallbackMotion(gpsDao.pointsForSession(sessionId))
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
        val fuelType = activeProfileProvider()?.fuelType
        val fuel = computeFuelResult(sessionId, fuelType)
        TripAggregates(
            fuelLiters = fuel?.liters,
            fuelCostCop = fuel?.costCop,
            ecoScore = computeEcoScore(sessionId, activeProfileProvider),
        )
    }.onFailure { Timber.w(it, "SessionAggregator: failed to compute trip aggregates") }
        .getOrNull()

    private suspend fun computeFuelResult(sessionId: Long, fuelType: String?): FuelCostCalculator.FuelResult? {
        val precioGalonCop = FuelPricePrefs.priceFor(FuelPricePrefs.read(settings), fuelType)
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
        private const val DEFAULT_REDLINE_RPM = 10_500
        private const val EARTH_GRAVITY_MS2 = 9.80665

        /** Pure GPS-track fallback for [resolveTripMotion] — no I/O, unit-testable directly. */
        internal fun gpsFallbackMotion(gpsPoints: List<GpsPointEntity>): TripMotion =
            TripMotion(
                maxSpeed = gpsPoints.maxOfOrNull { it.speedKmh } ?: 0f,
                distanceKm = TripStatsCalculator.gpsDistanceKm(gpsPoints).toFloat(),
            )
    }
}

internal data class TripMotion(val maxSpeed: Float, val distanceKm: Float)
