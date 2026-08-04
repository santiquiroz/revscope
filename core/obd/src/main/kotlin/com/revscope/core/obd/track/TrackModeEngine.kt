package com.revscope.core.obd.track

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.hypot

private const val LINE_HALF_WIDTH_M = 15.0
private const val METERS_PER_DEG_LAT = 110_540.0
private const val METERS_PER_DEG_LON_EQ = 111_320.0
private const val MIN_MOVEMENT_M = 1.0

/**
 * Lap timer over the live GPS stream. Arm the finish line while sitting on it
 * (uses the last fix + travel heading); every subsequent crossing in the same
 * direction closes a lap, with the crossing instant interpolated along the
 * movement segment for sub-sample accuracy.
 */
@Singleton
class TrackModeEngine @Inject constructor() {

    data class Lap(val number: Int, val timeMs: Long)

    data class TrackState(
        val finishLineSet: Boolean = false,
        val lapInProgress: Boolean = false,
        val currentLapStartMs: Long? = null,
        val laps: List<Lap> = emptyList(),
        val bestLapMs: Long? = null,
        val hasGpsFix: Boolean = false,
    )

    private data class Fix(val lat: Double, val lon: Double, val timeMs: Long)

    /** Finish line as a segment in local meters around [refLat]/[refLon]. */
    private data class FinishLine(
        val refLat: Double,
        val refLon: Double,
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        val headingX: Double, val headingY: Double,
    )

    private val _state = MutableStateFlow(TrackState())
    val state: StateFlow<TrackState> = _state.asStateFlow()

    private val _lapEvents = MutableSharedFlow<Lap>(extraBufferCapacity = 8)
    val lapEvents: SharedFlow<Lap> = _lapEvents.asSharedFlow()

    // Ghost racing (self-ghost): la vuelta en curso corre contra la MEJOR vuelta de esta
    // sesión. delta > 0 = vas perdiendo. null = aún no hay fantasma (primera vuelta).
    private val ghostEngine = GhostRaceEngine()
    private val lapBuffer = mutableListOf<GhostRaceEngine.GhostPoint>()
    private val _ghostDeltaMs = MutableStateFlow<Long?>(null)
    val ghostDeltaMs: StateFlow<Long?> = _ghostDeltaMs.asStateFlow()

    // Cuando hay un fantasma REMOTO cargado, la mejor vuelta propia deja de sobrescribirlo.
    @Volatile private var remoteGhostActive = false

    /** Snapshot de la mejor vuelta de la sesión, lista para subir al servidor. */
    data class BestLapUpload(val finishLat: Double, val finishLon: Double, val timeMs: Long, val points: List<GhostRaceEngine.GhostPoint>)
    @Volatile private var bestLapUpload: BestLapUpload? = null

    fun bestLapForUpload(): BestLapUpload? = bestLapUpload

    /** Coordenadas de la línea de meta armada — para pedir fantasmas de esta pista. */
    fun finishLinePoint(): Pair<Double, Double>? = line?.let { it.refLat to it.refLon }

    /** Carga un fantasma remoto y corre contra él (reemplaza el self-ghost hasta [useSelfGhost]). */
    @Synchronized
    fun setRemoteGhost(points: List<GhostRaceEngine.GhostPoint>) {
        ghostEngine.setGhost(points)
        remoteGhostActive = ghostEngine.hasGhost
        ghostEngine.onLapStart()
        _ghostDeltaMs.value = null
    }

    @Synchronized
    fun useSelfGhost() {
        remoteGhostActive = false
        ghostEngine.clearGhost()
        _ghostDeltaMs.value = null
    }

    private var prevFix: Fix? = null
    private var lastFix: Fix? = null
    private var line: FinishLine? = null
    private var lapStartMs: Long? = null

    /**
     * Arms the finish line at the current GPS position, oriented perpendicular to
     * the current travel heading. Returns false without a fix or heading.
     */
    @Synchronized
    fun armFinishLine(): Boolean {
        val current = lastFix ?: return false
        val previous = prevFix ?: return false
        val (hx, hy) = headingVector(previous, current) ?: return false

        // Perpendicular to heading, half-width to each side
        val px = -hy
        val py = hx
        line = FinishLine(
            refLat = current.lat,
            refLon = current.lon,
            x1 = px * LINE_HALF_WIDTH_M, y1 = py * LINE_HALF_WIDTH_M,
            x2 = -px * LINE_HALF_WIDTH_M, y2 = -py * LINE_HALF_WIDTH_M,
            headingX = hx, headingY = hy,
        )
        lapStartMs = null
        if (!remoteGhostActive) ghostEngine.clearGhost()
        lapBuffer.clear()
        bestLapUpload = null
        _ghostDeltaMs.value = null
        _state.value = _state.value.copy(
            finishLineSet = true,
            lapInProgress = false,
            currentLapStartMs = null,
            laps = emptyList(),
            bestLapMs = null,
        )
        Timber.i("TrackMode: finish line armed at ${current.lat},${current.lon}")
        return true
    }

    @Synchronized
    fun clear() {
        line = null
        lapStartMs = null
        remoteGhostActive = false
        ghostEngine.clearGhost()
        lapBuffer.clear()
        bestLapUpload = null
        _ghostDeltaMs.value = null
        _state.value = TrackState(hasGpsFix = lastFix != null)
    }

    @Synchronized
    fun onGpsFix(lat: Double, lon: Double, timeMs: Long) {
        val fix = Fix(lat, lon, timeMs)
        prevFix = lastFix
        lastFix = fix
        if (!_state.value.hasGpsFix) _state.value = _state.value.copy(hasGpsFix = true)

        val activeLine = line ?: return
        val previous = prevFix ?: return

        val crossingT = crossingParameter(activeLine, previous, fix)
        if (crossingT == null) {
            feedGhost(fix)
            return
        }
        val crossingTime = previous.timeMs + ((fix.timeMs - previous.timeMs) * crossingT).toLong()

        val start = lapStartMs
        if (start == null) {
            lapStartMs = crossingTime
            ghostEngine.onLapStart()
            lapBuffer.clear()
            _state.value = _state.value.copy(lapInProgress = true, currentLapStartMs = crossingTime)
            Timber.i("TrackMode: lap 1 started")
            return
        }

        val lapTime = crossingTime - start
        val lap = Lap(number = _state.value.laps.size + 1, timeMs = lapTime)
        val isBest = lapTime < (_state.value.bestLapMs ?: Long.MAX_VALUE)
        // La mejor vuelta se guarda para subir, y se vuelve el self-ghost salvo que
        // haya un fantasma remoto cargado.
        if (isBest && lapBuffer.size >= 2) {
            val snapshot = lapBuffer.toList()
            bestLapUpload = BestLapUpload(activeLine.refLat, activeLine.refLon, lapTime, snapshot)
            if (!remoteGhostActive) ghostEngine.setGhost(snapshot)
        }
        lapBuffer.clear()
        ghostEngine.onLapStart()
        _ghostDeltaMs.value = null
        lapStartMs = crossingTime
        _state.value = _state.value.copy(
            laps = _state.value.laps + lap,
            bestLapMs = minOf(_state.value.bestLapMs ?: Long.MAX_VALUE, lapTime),
            currentLapStartMs = crossingTime,
        )
        _lapEvents.tryEmit(lap)
        Timber.i("TrackMode: lap ${lap.number} = ${lap.timeMs} ms")
    }

    /** Alimenta el fantasma con la vuelta en curso (buffer propio + delta en vivo). */
    private fun feedGhost(fix: Fix) {
        val start = lapStartMs ?: return
        val tRel = fix.timeMs - start
        lapBuffer += GhostRaceEngine.GhostPoint(fix.lat, fix.lon, tRel)
        _ghostDeltaMs.value = ghostEngine.onFix(fix.lat, fix.lon, tRel)
    }

    // ── Geometry ─────────────────────────────────────────────────────────────

    private fun toLocalMeters(lineRef: FinishLine, fix: Fix): Pair<Double, Double> {
        val x = (fix.lon - lineRef.refLon) * METERS_PER_DEG_LON_EQ * cos(Math.toRadians(lineRef.refLat))
        val y = (fix.lat - lineRef.refLat) * METERS_PER_DEG_LAT
        return x to y
    }

    private fun headingVector(from: Fix, to: Fix): Pair<Double, Double>? {
        val dx = (to.lon - from.lon) * METERS_PER_DEG_LON_EQ * cos(Math.toRadians(from.lat))
        val dy = (to.lat - from.lat) * METERS_PER_DEG_LAT
        val length = hypot(dx, dy)
        if (length < MIN_MOVEMENT_M) return null
        return (dx / length) to (dy / length)
    }

    /**
     * Returns the parameter t ∈ [0,1] along the movement segment where it crosses
     * the finish line in the armed direction, or null when there is no crossing.
     */
    private fun crossingParameter(activeLine: FinishLine, from: Fix, to: Fix): Double? {
        val (fx, fy) = toLocalMeters(activeLine, from)
        val (tx, ty) = toLocalMeters(activeLine, to)

        val moveX = tx - fx
        val moveY = ty - fy
        // Direction filter: must cross the same way the line was armed
        if (moveX * activeLine.headingX + moveY * activeLine.headingY <= 0) return null

        // Segment intersection: movement (f→t) vs finish line (p1→p2)
        val lineX = activeLine.x2 - activeLine.x1
        val lineY = activeLine.y2 - activeLine.y1
        val denominator = moveX * lineY - moveY * lineX
        if (kotlin.math.abs(denominator) < 1e-12) return null

        val t = ((activeLine.x1 - fx) * lineY - (activeLine.y1 - fy) * lineX) / denominator
        val u = ((activeLine.x1 - fx) * moveY - (activeLine.y1 - fy) * moveX) / denominator
        return if (t in 0.0..1.0 && u in 0.0..1.0) t else null
    }
}
