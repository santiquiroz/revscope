package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revscope.core.data.db.entities.TelemetryPointEntity
import kotlinx.coroutines.flow.Flow

data class TimedValue(val timestamp: Long, val value: Float)

data class SeriesSpan(val count: Int, val firstTs: Long?, val lastTs: Long?)

@Dao
interface TelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: TelemetryPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TelemetryPointEntity>)

    @Query("SELECT * FROM telemetry_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeBySession(sessionId: Long): Flow<List<TelemetryPointEntity>>

    @Query(
        "SELECT * FROM telemetry_points WHERE sessionId = :sessionId AND pid = :pid ORDER BY timestamp ASC"
    )
    fun observeBySessionAndPid(sessionId: Long, pid: String): Flow<List<TelemetryPointEntity>>

    @Query("DELETE FROM telemetry_points WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    // ── Trip report aggregates ───────────────────────────────────────────────

    @Query(
        "SELECT * FROM telemetry_points WHERE sessionId = :sessionId AND pid = :pid ORDER BY timestamp ASC"
    )
    suspend fun pointsForSessionAndPid(sessionId: Long, pid: String): List<TelemetryPointEntity>

    @Query("SELECT * FROM telemetry_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun pointsForSession(sessionId: Long): List<TelemetryPointEntity>

    @Query(
        "SELECT MAX(value) FROM telemetry_points WHERE sessionId = :sessionId AND pid = :pid"
    )
    suspend fun maxValue(sessionId: Long, pid: String): Float?

    @Query(
        "SELECT AVG(value) FROM telemetry_points WHERE sessionId = :sessionId AND pid = :pid AND value > 0"
    )
    suspend fun avgNonZeroValue(sessionId: Long, pid: String): Float?

    @Query("SELECT COUNT(*) FROM telemetry_points WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    // ── Lecturas acotadas del reporte y export paginado ─────────────────────

    @Query(
        "SELECT COUNT(*) AS count, MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs " +
            "FROM telemetry_points WHERE sessionId = :sessionId AND pid = :pid"
    )
    suspend fun spanForSessionAndPid(sessionId: Long, pid: String): SeriesSpan

    @Query(
        "SELECT MIN(timestamp) AS timestamp, AVG(value) AS value FROM telemetry_points " +
            "WHERE sessionId = :sessionId AND pid = :pid " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun avgBucketsForPid(sessionId: Long, pid: String, bucketMs: Long, limit: Int): List<TimedValue>

    @Query(
        "SELECT * FROM telemetry_points " +
            "WHERE sessionId = :sessionId " +
            "AND (timestamp > :afterTimestamp OR (timestamp = :afterTimestamp AND id > :afterId)) " +
            "ORDER BY timestamp ASC, id ASC LIMIT :pageSize"
    )
    suspend fun pageForSession(
        sessionId: Long,
        afterTimestamp: Long,
        afterId: Long,
        pageSize: Int,
    ): List<TelemetryPointEntity>

    // ── Tendencia de salud de batería ────────────────────────────────────────

    data class SessionVoltage(val startedAt: Long, val avgVolts: Double)

    /** Voltaje promedio (VBAT) por sesión, más reciente primero — insumo del análisis de tendencia. */
    @Query(
        "SELECT s.startedAt AS startedAt, AVG(t.value) AS avgVolts " +
            "FROM telemetry_points t JOIN sessions s ON s.id = t.sessionId " +
            "WHERE t.pid = 'VBAT' AND t.value > 0 " +
            "GROUP BY t.sessionId ORDER BY s.startedAt DESC LIMIT :limit"
    )
    suspend fun recentSessionVoltages(limit: Int = 20): List<SessionVoltage>
}
