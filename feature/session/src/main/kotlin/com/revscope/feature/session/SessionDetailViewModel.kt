package com.revscope.feature.session

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.revscope.core.common.export.CsvFormat
import com.revscope.core.common.export.CsvShare
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.stateInSafe
import com.revscope.core.common.units.UnitFormatter
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.HrDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.LapPeak
import com.revscope.core.data.db.dao.SeriesSpan
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.TimedValue
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.HrPointEntity
import com.revscope.core.data.db.entities.ImuPointEntity
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity
import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.data.db.entities.vehicleType
import com.revscope.core.intelligence.debrief.TripDebriefGenerator
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.telemetry.TripStatsCalculator
import com.revscope.core.obd.trip.EcoScoreCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt

internal fun csvLine(vararg columns: Any?): String =
    columns.joinToString(",") { CsvFormat.escapar(it.toString()) }

internal const val REPORT_MAX_ROWS = 20_000
internal const val CSV_PAGE_SIZE = 5_000
internal const val RAW_BUCKET_MS = 1L
private const val MIN_BUCKET_MS = 1_000L
private const val CHART_MAX_POINTS = 240

// Baldes alineados al epoch: una ventana de d ms toca a lo sumo d / balde + 2 baldes.
internal fun reportBucketMs(span: SeriesSpan, maxRows: Int = REPORT_MAX_ROWS): Long {
    if (span.count <= maxRows) return RAW_BUCKET_MS
    val durationMs = ((span.lastTs ?: 0L) - (span.firstTs ?: 0L)).coerceAtLeast(0L)
    val perBucket = maxRows - 2L
    return maxOf(MIN_BUCKET_MS, (durationMs + perBucket - 1) / perBucket)
}

internal fun mergeByTime(a: List<ImuPointEntity>, b: List<ImuPointEntity>): List<ImuPointEntity> =
    (a + b).distinctBy { it.id }.sortedWith(compareBy({ it.timestamp }, { it.id }))

internal data class PageKey(val timestamp: Long, val id: Long) {
    companion object {
        val START = PageKey(Long.MIN_VALUE, Long.MIN_VALUE)
    }
}

internal typealias PageFetch<T> = suspend (after: PageKey, pageSize: Int) -> List<T>

internal class TripCsvPages(
    val obd: PageFetch<TelemetryPointEntity>,
    val gps: PageFetch<GpsPointEntity>,
    val imu: PageFetch<ImuPointEntity>,
    val hr: PageFetch<HrPointEntity>,
)

internal suspend fun writeTripCsv(out: Appendable, pages: TripCsvPages, pageSize: Int = CSV_PAGE_SIZE) {
    out.appendLine(csvLine("section", "timestamp", "key", "value1", "value2"))
    appendPaged(out, pages.obd, pageSize, { PageKey(it.timestamp, it.id) }) {
        csvLine("obd", it.timestamp, it.pid, it.value, "")
    }
    appendPaged(out, pages.gps, pageSize, { PageKey(it.timestamp, it.id) }) {
        csvLine("gps", it.timestamp, it.latitude, it.longitude, it.speedKmh)
    }
    appendPaged(out, pages.imu, pageSize, { PageKey(it.timestamp, it.id) }) {
        csvLine("imu", it.timestamp, it.gLat, it.gLong, it.leanDeg)
    }
    appendPaged(out, pages.hr, pageSize, { PageKey(it.timestamp, it.id) }) {
        csvLine("hr", it.timestamp, it.bpm, "", "")
    }
}

private suspend fun <T> appendPaged(
    out: Appendable,
    fetch: PageFetch<T>,
    pageSize: Int,
    keyOf: (T) -> PageKey,
    lineOf: (T) -> String,
) {
    var after = PageKey.START
    while (true) {
        val page = fetch(after, pageSize)
        page.forEach { out.appendLine(lineOf(it)) }
        if (page.size < pageSize) return
        after = keyOf(page.last())
    }
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val stringResolver: StringResolver,
    private val sessionDao: SessionDao,
    private val debriefGenerator: TripDebriefGenerator,
    private val telemetryDao: TelemetryDao,
    private val gpsDao: GpsDao,
    private val lapDao: LapDao,
    private val imuDao: ImuDao,
    private val hrDao: HrDao,
    private val profileDao: VehicleProfileDao,
    private val resolver: StringResolver,
    private val units: UnitFormatter,
    sessionManager: ObdSessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val profiles: StateFlow<List<VehicleProfileEntity>> = profileDao.observeAll()
        .stateInSafe(viewModelScope, emptyList(), started = SharingStarted.Eagerly)

    data class TripReport(
        val session: SessionEntity,
        val avgSpeedKmh: Float,
        val maxRpm: Int,
        val avgRpm: Int,
        val maxCoolantTemp: Int,
        val maxThrottlePct: Int,
        val totalPoints: Int,
        val rpmSeries: List<Float>,
        val speedSeries: List<Float>,
        /** lat/lon pairs, downsampled — empty when no GPS was recorded */
        val gpsTrack: List<Pair<Double, Double>>,
        /** speed (km/h) per track point, same indices as gpsTrack — for color grading */
        val gpsTrackSpeeds: List<Float>,
        val gpsDistanceKm: Double,
        val gpsMaxSpeedKmh: Int,
        val laps: List<LapEntity>,
        val maxLateralG: Float?,
        val maxBrakingG: Float?,
        val maxLeanDeg: Float?,
        /** (gLat, gLong) samples for the friction circle, downsampled */
        val frictionPoints: List<Pair<Float, Float>>,
        /** aligned with gpsTrack — true where braking hard */
        val brakingMask: List<Boolean>,
        /** per-lap peaks, same order as [laps] */
        val lapStats: List<LapStat>,
        /** (throttle %, longitudinal G) pairs matched by timestamp — tuning view */
        val throttleGPoints: List<Pair<Float, Float>>,
        /** Rider heart rate from the paired watch — empty if none streamed */
        val hrSeries: List<Float>,
        val avgBpm: Int?,
        val maxBpm: Int?,
        /** Recalculated from this trip's raw IMU/RPM — null when there's no telemetry to derive it from */
        val ecoDesglose: EcoScoreCalculator.Desglose?,
    )

    data class LapStat(val maxAbsG: Float?, val maxAbsLean: Float?, val maxBpm: Float?)

    /** Per-metric CSV export offered from the "Exportar…" menu — [tipo] feeds the filename. */
    enum class ExportMetric(val tipo: String) {
        VELOCIDAD("velocidad"),
        RPM("rpm"),
        TEMPERATURA("temperatura"),
        RITMO_CARDIACO("ritmo-cardiaco"),
        IMU("imu"),
        GPS("gps"),
    }

    sealed class UiState {
        object Loading : UiState()
        data class Ready(val report: TripReport) : UiState()
        data class NotFound(val message: String) : UiState()
    }

    sealed class DebriefState {
        object Idle : DebriefState()
        object Generating : DebriefState()
        data class Ready(val text: String) : DebriefState()
        data class Error(val message: String) : DebriefState()
    }

    private val sessionId: Long = checkNotNull(savedStateHandle["sessionId"])

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * Gate de lean para esta sesión: usa el perfil asignado a la sesión si está disponible,
     * si no cae al perfil activo — mismo patrón que [ObdForegroundService] para sesiones en curso.
     */
    val isMotorcycle: StateFlow<Boolean> = combine(
        state, profiles, sessionManager.activeProfile,
    ) { uiState, profileList, activeProfile ->
        val vehicleProfileId = (uiState as? UiState.Ready)?.report?.session?.vehicleProfileId
        val profile = profileList.firstOrNull { it.id == vehicleProfileId } ?: activeProfile
        profile?.vehicleType == VehicleType.MOTORCYCLE
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _debrief = MutableStateFlow<DebriefState>(DebriefState.Idle)
    val debrief: StateFlow<DebriefState> = _debrief.asStateFlow()

    /** Digest local (SQL/Kotlin) → una llamada corta a la IA que solo narra. */
    fun generateDebrief() {
        val report = (_state.value as? UiState.Ready)?.report ?: return
        if (_debrief.value is DebriefState.Generating) return
        _debrief.value = DebriefState.Generating
        viewModelScope.launch {
            val session = report.session
            val profile = profiles.value.firstOrNull { it.id == session.vehicleProfileId }
            val previous = runCatching { sessionDao.getRecentForProfile(session.vehicleProfileId, 10) }
                .getOrNull()
                .orEmpty()
                .filter { it.id != session.id && it.endedAt != null }
            val prevEco = previous.mapNotNull { it.ecoScore }
            val prevMaxSpeed = previous.map { it.maxSpeed }.filter { it > 0 }
            val digest = TripDebriefGenerator.TripDigest(
                vehicleName = profile?.name ?: stringResolver.get(R.string.trips_my_vehicle),
                // El debrief describe el vehículo de la sesión; sin perfil resoluble no se asume el activo.
                isMotorcycle = profile?.vehicleType == VehicleType.MOTORCYCLE,
                distanceKm = if (session.distanceKm > 0) session.distanceKm.toDouble() else report.gpsDistanceKm,
                durationMin = ((session.endedAt ?: session.startedAt) - session.startedAt) / 60_000L,
                avgSpeedKmh = report.avgSpeedKmh.toInt(),
                maxSpeedKmh = maxOf(session.maxSpeed, report.gpsMaxSpeedKmh),
                maxRpm = report.maxRpm,
                ecoScore = session.ecoScore,
                fuelCostCop = session.fuelCostCop,
                maxBrakingG = report.maxBrakingG,
                maxLateralG = report.maxLateralG,
                maxLeanDeg = report.maxLeanDeg,
                best0to100s = session.best0to100Ms?.let { it / 1000.0 },
                prevTripCount = previous.size,
                prevAvgEcoScore = prevEco.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                prevAvgMaxSpeedKmh = prevMaxSpeed.takeIf { it.isNotEmpty() }?.average()?.toInt(),
            )
            debriefGenerator.generate(digest).fold(
                onSuccess = { _debrief.value = DebriefState.Ready(it.trim()) },
                onFailure = { _debrief.value = DebriefState.Error(it.message?.take(160) ?: stringResolver.get(R.string.trips_debrief_error)) },
            )
        }
    }

    init {
        viewModelScope.launch { loadReport() }
    }

    private suspend fun loadReport() {
        try {
            val session = sessionDao.getById(sessionId) ?: run {
                _state.value = UiState.NotFound(stringResolver.get(R.string.trips_session_error_not_found))
                return
            }
            _state.value = UiState.Ready(buildReport(session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "SessionDetail: failed to load report for session $sessionId")
            _state.value = UiState.NotFound(stringResolver.get(R.string.trips_report_load_error))
        }
    }

    private suspend fun buildReport(session: SessionEntity): TripReport {
        val rpmPoints = pidSeries("0C")
        val gpsPoints = gpsSeries()
        val sampledGps = downsampleList(gpsPoints, TRACK_MAX_POINTS)
        val imu = imuSeries()
        val laps = lapDao.lapsForSession(sessionId)
        return TripReport(
            session = session,
            avgSpeedKmh = telemetryDao.avgNonZeroValue(sessionId, "0D") ?: 0f,
            maxRpm = (telemetryDao.maxValue(sessionId, "0C") ?: 0f).roundToInt(),
            avgRpm = (telemetryDao.avgNonZeroValue(sessionId, "0C") ?: 0f).roundToInt(),
            maxCoolantTemp = (telemetryDao.maxValue(sessionId, "05") ?: 0f).roundToInt(),
            maxThrottlePct = (telemetryDao.maxValue(sessionId, "11") ?: 0f).roundToInt(),
            totalPoints = telemetryDao.countForSession(sessionId),
            rpmSeries = downsample(rpmPoints),
            speedSeries = downsample(pidSeries("0D")),
            gpsTrack = sampledGps.map { it.latitude to it.longitude },
            gpsTrackSpeeds = sampledGps.map { it.speedKmh },
            gpsDistanceKm = TripStatsCalculator.gpsDistanceKm(gpsPoints),
            gpsMaxSpeedKmh = (gpsDao.maxSpeed(sessionId) ?: 0f).roundToInt(),
            laps = laps,
            maxLateralG = imuDao.maxAbsLateralG(sessionId),
            maxBrakingG = imuDao.maxBrakingG(sessionId)?.let { -it },
            maxLeanDeg = imuDao.maxAbsLean(sessionId),
            frictionPoints = downsampleList(imu.samples, FRICTION_MAX_POINTS).map { it.gLat to it.gLong },
            brakingMask = brakingMask(sampledGps, imu),
            lapStats = lapStats(laps),
            throttleGPoints = throttleGPoints(imu),
            hrSeries = downsampleList(hrSeries().map { it.value }, CHART_MAX_POINTS),
            avgBpm = hrDao.avgBpm(sessionId)?.roundToInt(),
            maxBpm = hrDao.maxBpm(sessionId)?.roundToInt(),
            ecoDesglose = ecoDesglose(session, rpmPoints, imu.envelope),
        )
    }

    private suspend fun pidSeries(pid: String): List<TimedValue> {
        val bucketMs = reportBucketMs(telemetryDao.spanForSessionAndPid(sessionId, pid))
        return telemetryDao.avgBucketsForPid(sessionId, pid, bucketMs, REPORT_MAX_ROWS)
    }

    private suspend fun gpsSeries(): List<GpsPointEntity> {
        val bucketMs = reportBucketMs(gpsDao.spanForSession(sessionId))
        return gpsDao.firstSampleBuckets(sessionId, bucketMs, REPORT_MAX_ROWS)
    }

    private suspend fun hrSeries(): List<TimedValue> {
        val bucketMs = reportBucketMs(hrDao.spanForSession(sessionId))
        return hrDao.avgBuckets(sessionId, bucketMs, REPORT_MAX_ROWS)
    }

    private suspend fun imuSeries(): ImuSeries {
        val bucketMs = reportBucketMs(imuDao.spanForSession(sessionId))
        val samples = imuDao.firstSampleBuckets(sessionId, bucketMs, REPORT_MAX_ROWS)
        if (bucketMs == RAW_BUCKET_MS) return ImuSeries(samples, samples, samples, bucketMs)
        val braking = imuDao.minGLongBuckets(sessionId, bucketMs, REPORT_MAX_ROWS)
        val peaks = imuDao.maxGLongBuckets(sessionId, bucketMs, REPORT_MAX_ROWS)
        return ImuSeries(samples, braking, mergeByTime(braking, peaks), bucketMs)
    }

    private fun brakingMask(track: List<GpsPointEntity>, imu: ImuSeries): List<Boolean> {
        val timestamps = imu.braking.map { it.timestamp }
        val windowMs = maxOf(BRAKING_WINDOW_MS, imu.bucketMs)
        return track.map { gps ->
            minGLongAround(imu.braking, timestamps, gps.timestamp, windowMs) < BRAKING_G_THRESHOLD
        }
    }

    private suspend fun throttleGPoints(imu: ImuSeries): List<Pair<Float, Float>> {
        val timestamps = imu.samples.map { it.timestamp }
        val windowMs = maxOf(NEAREST_IMU_WINDOW_MS, imu.bucketMs / 2)
        return downsampleList(pidSeries("11"), THROTTLE_G_MAX_POINTS).mapNotNull { t ->
            nearestGLong(imu.samples, timestamps, t.timestamp, windowMs)?.let { g -> t.value to g }
        }
    }

    private suspend fun lapStats(laps: List<LapEntity>): List<LapStat> {
        if (laps.isEmpty()) return emptyList()
        val lateral = imuDao.lapMaxAbsLateralG(sessionId).byLap()
        val lean = imuDao.lapMaxAbsLean(sessionId).byLap()
        val bpm = hrDao.lapMaxBpm(sessionId).byLap()
        return laps.map { LapStat(maxAbsG = lateral[it.id], maxAbsLean = lean[it.id], maxBpm = bpm[it.id]) }
    }

    private fun List<LapPeak>.byLap(): Map<Long, Float?> = associate { it.lapId to it.value }

    private class ImuSeries(
        val samples: List<ImuPointEntity>,
        val braking: List<ImuPointEntity>,
        val envelope: List<ImuPointEntity>,
        val bucketMs: Long,
    )

    /** Reassigns this trip to a different vehicle profile and refreshes the report header. */
    fun assignVehicle(profileId: Long) {
        val ready = _state.value as? UiState.Ready ?: return
        val updatedSession = ready.report.session.copy(vehicleProfileId = profileId)
        viewModelScope.launch {
            runCatching { sessionDao.update(updatedSession) }
                .onSuccess { _state.value = UiState.Ready(ready.report.copy(session = updatedSession)) }
                .onFailure { Timber.w(it, "SessionDetail: failed to assign vehicle to session $sessionId") }
        }
    }

    /**
     * Recalculates the eco-driving breakdown from this trip's raw IMU/RPM points — the
     * persisted [SessionEntity.ecoScore] only stores the final number, not the desglose.
     * Null when there's no telemetry to derive it from (mirrors ObdSessionManager's guard).
     */
    private suspend fun ecoDesglose(
        session: SessionEntity,
        rpmPoints: List<TimedValue>,
        imuPoints: List<ImuPointEntity>,
    ): EcoScoreCalculator.Desglose? {
        if (rpmPoints.isEmpty() && imuPoints.isEmpty()) return null
        val redlineRpm = profileDao.getById(session.vehicleProfileId)?.redlineRpm ?: DEFAULT_REDLINE_RPM
        return EcoScoreCalculator.calculate(
            accelLongitudinal = imuPoints.map { it.gLong.toDouble() * EARTH_GRAVITY_MS2 },
            rpmPoints = rpmPoints.map { it.timestamp to it.value.toDouble() },
            redlineRpm = redlineRpm,
        )
    }

    /** Charts don't need thousands of points — keep every Nth so Vico stays fluid. */
    private fun downsample(points: List<TimedValue>): List<Float> {
        if (points.size <= CHART_MAX_POINTS) return points.map { it.value }
        val step = points.size / CHART_MAX_POINTS
        return points.filterIndexed { index, _ -> index % step == 0 }.map { it.value }
    }

    /**
     * Writes the full session (telemetry + GPS) as CSV to app cache and returns a
     * shareable content:// Uri. Runs on IO — thousands of rows.
     */
    suspend fun exportCsv(): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(appContext.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, "revscope_trip_$sessionId.csv")
            file.bufferedWriter().use { out -> writeTripCsv(out, tripCsvPages()) }
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Timber.e(e, "SessionDetail: CSV export failed")
            null
        }
    }

    private fun tripCsvPages() = TripCsvPages(
        obd = { after, size -> telemetryDao.pageForSession(sessionId, after.timestamp, after.id, size) },
        gps = { after, size -> gpsDao.pageForSession(sessionId, after.timestamp, after.id, size) },
        imu = { after, size -> imuDao.pageForSession(sessionId, after.timestamp, after.id, size) },
        hr = { after, size -> hrDao.pageForSession(sessionId, after.timestamp, after.id, size) },
    )

    /** Exports one metric's full-resolution series for this session — separate from the downsampled charts. */
    fun exportMetric(context: Context, metric: ExportMetric) {
        viewModelScope.launch {
            when (metric) {
                ExportMetric.VELOCIDAD -> exportPidMetric(context, metric, "0D", "valor_kmh")
                ExportMetric.RPM -> exportPidMetric(context, metric, "0C", "valor_rpm")
                ExportMetric.TEMPERATURA -> exportPidMetric(context, metric, "05", "valor_c")
                ExportMetric.RITMO_CARDIACO -> exportHr(context, metric)
                ExportMetric.IMU -> exportImu(context, metric)
                ExportMetric.GPS -> exportGps(context, metric)
            }
        }
    }

    private suspend fun exportPidMetric(
        context: Context,
        metric: ExportMetric,
        pid: String,
        valueColumn: String,
    ) {
        val points = telemetryDao.pointsForSessionAndPid(sessionId, pid)
        if (points.isEmpty()) return
        CsvShare.shareCsv(
            context = context,
            tipo = metric.tipo,
            header = listOf("timestamp_iso", "epoch_ms", valueColumn),
            rows = points.asSequence().map { p -> listOf(CsvShare.isoTimestamp(p.timestamp), p.timestamp, p.value) },
            resolver = resolver,
        )
    }

    private suspend fun exportHr(context: Context, metric: ExportMetric) {
        val points = hrDao.pointsForSession(sessionId)
        if (points.isEmpty()) return
        CsvShare.shareCsv(
            context = context,
            tipo = metric.tipo,
            header = listOf("timestamp_iso", "epoch_ms", "bpm"),
            rows = points.asSequence().map { p -> listOf(CsvShare.isoTimestamp(p.timestamp), p.timestamp, p.bpm) },
            resolver = resolver,
        )
    }

    private suspend fun exportImu(context: Context, metric: ExportMetric) {
        val points = imuDao.pointsForSession(sessionId)
        if (points.isEmpty()) return
        CsvShare.shareCsv(
            context = context,
            tipo = metric.tipo,
            header = listOf("timestamp_iso", "epoch_ms", "g_lat", "g_long", "lean_deg"),
            rows = points.asSequence().map { p ->
                listOf(CsvShare.isoTimestamp(p.timestamp), p.timestamp, p.gLat, p.gLong, p.leanDeg)
            },
            resolver = resolver,
        )
    }

    private suspend fun exportGps(context: Context, metric: ExportMetric) {
        val points = gpsDao.pointsForSession(sessionId)
        if (points.isEmpty()) return
        CsvShare.shareCsv(
            context = context,
            tipo = metric.tipo,
            header = listOf("timestamp_iso", "epoch_ms", "lat", "lon", "vel_kmh"),
            rows = points.asSequence().map { p ->
                listOf(CsvShare.isoTimestamp(p.timestamp), p.timestamp, p.latitude, p.longitude, p.speedKmh)
            },
            resolver = resolver,
        )
    }

    private fun <T> downsampleList(points: List<T>, max: Int): List<T> {
        if (points.size <= max) return points
        val step = points.size / max
        return points.filterIndexed { index, _ -> index % step == 0 }
    }

    /** Lowest longitudinal G within ±[windowMs] of [targetMs] — binary search over sorted IMU. */
    private fun minGLongAround(
        imu: List<ImuPointEntity>,
        timestamps: List<Long>,
        targetMs: Long,
        windowMs: Long,
    ): Float {
        if (imu.isEmpty()) return 0f
        val index = timestamps.binarySearch(targetMs).let { if (it < 0) -it - 1 else it }
        var minG = 0f
        var i = index
        while (i < imu.size && imu[i].timestamp <= targetMs + windowMs) {
            if (imu[i].gLong < minG) minG = imu[i].gLong
            i++
        }
        i = index - 1
        while (i >= 0 && imu[i].timestamp >= targetMs - windowMs) {
            if (imu[i].gLong < minG) minG = imu[i].gLong
            i--
        }
        return minG
    }

    /** Renders the current report as a shareable PNG card. */
    suspend fun shareCardUri(): Uri? = withContext(Dispatchers.IO) {
        val ready = (_state.value as? UiState.Ready) ?: return@withContext null
        val r = ready.report
        val s = r.session
        TripShareCard.render(
            appContext,
            stringResolver,
            units,
            TripShareCard.CardData(
                startedAt = s.startedAt,
                durationMs = (s.endedAt ?: s.startedAt) - s.startedAt,
                distanceKm = s.distanceKm,
                maxSpeed = s.maxSpeed,
                avgSpeedKmh = r.avgSpeedKmh,
                maxRpm = s.maxRpm,
                best0to100Ms = s.best0to100Ms,
                maxLateralG = r.maxLateralG,
                maxLeanDeg = r.maxLeanDeg,
                maxBpm = r.maxBpm,
                lapCount = r.laps.size,
                bestLapMs = r.laps.minOfOrNull { it.timeMs },
                track = r.gpsTrack,
                showLean = isMotorcycle.value,
                best0to60mphMs = s.best0to60mphMs,
            ),
        )
    }

    /** Longitudinal G of the IMU sample closest to [targetMs], or null if too far. */
    private fun nearestGLong(
        imu: List<ImuPointEntity>,
        timestamps: List<Long>,
        targetMs: Long,
        windowMs: Long,
    ): Float? {
        if (imu.isEmpty()) return null
        val index = timestamps.binarySearch(targetMs).let { if (it < 0) -it - 1 else it }
        val candidates = listOfNotNull(imu.getOrNull(index - 1), imu.getOrNull(index))
        return candidates
            .minByOrNull { kotlin.math.abs(it.timestamp - targetMs) }
            ?.takeIf { kotlin.math.abs(it.timestamp - targetMs) <= windowMs }
            ?.gLong
    }

    private companion object {
        const val TRACK_MAX_POINTS = 600
        const val FRICTION_MAX_POINTS = 1_500
        const val THROTTLE_G_MAX_POINTS = 800
        const val BRAKING_G_THRESHOLD = -0.25f
        const val BRAKING_WINDOW_MS = 600L
        const val NEAREST_IMU_WINDOW_MS = 300L
        const val DEFAULT_REDLINE_RPM = 10_500
        const val EARTH_GRAVITY_MS2 = 9.80665
    }
}
