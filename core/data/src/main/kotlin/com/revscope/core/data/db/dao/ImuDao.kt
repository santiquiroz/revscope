package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.ImuPointEntity

@Dao
interface ImuDao {

    @Insert
    suspend fun insertAll(points: List<ImuPointEntity>)

    @Query("SELECT * FROM imu_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun pointsForSession(sessionId: Long): List<ImuPointEntity>

    @Query("SELECT MAX(ABS(gLat)) FROM imu_points WHERE sessionId = :sessionId")
    suspend fun maxAbsLateralG(sessionId: Long): Float?

    @Query("SELECT MIN(gLong) FROM imu_points WHERE sessionId = :sessionId")
    suspend fun maxBrakingG(sessionId: Long): Float?

    @Query("SELECT MAX(gLong) FROM imu_points WHERE sessionId = :sessionId")
    suspend fun maxAccelG(sessionId: Long): Float?

    @Query("SELECT MAX(ABS(leanDeg)) FROM imu_points WHERE sessionId = :sessionId")
    suspend fun maxAbsLean(sessionId: Long): Float?

    // ── Per-lap window aggregates ────────────────────────────────────────────

    @Query(
        "SELECT MAX(ABS(gLat)) FROM imu_points WHERE sessionId = :sessionId AND timestamp BETWEEN :fromMs AND :toMs"
    )
    suspend fun maxAbsLateralGBetween(sessionId: Long, fromMs: Long, toMs: Long): Float?

    @Query(
        "SELECT MAX(ABS(leanDeg)) FROM imu_points WHERE sessionId = :sessionId AND timestamp BETWEEN :fromMs AND :toMs"
    )
    suspend fun maxAbsLeanBetween(sessionId: Long, fromMs: Long, toMs: Long): Float?

    @Query(
        "SELECT l.id AS lapId, MAX(ABS(i.gLat)) AS value FROM laps l " +
            "LEFT JOIN imu_points i ON i.sessionId = l.sessionId " +
            "AND i.timestamp BETWEEN l.completedAt - l.timeMs AND l.completedAt " +
            "WHERE l.sessionId = :sessionId GROUP BY l.id"
    )
    suspend fun lapMaxAbsLateralG(sessionId: Long): List<LapPeak>

    @Query(
        "SELECT l.id AS lapId, MAX(ABS(i.leanDeg)) AS value FROM laps l " +
            "LEFT JOIN imu_points i ON i.sessionId = l.sessionId " +
            "AND i.timestamp BETWEEN l.completedAt - l.timeMs AND l.completedAt " +
            "WHERE l.sessionId = :sessionId GROUP BY l.id"
    )
    suspend fun lapMaxAbsLean(sessionId: Long): List<LapPeak>

    // ── Lecturas acotadas del reporte y export paginado ─────────────────────

    @Query(
        "SELECT COUNT(*) AS count, MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs " +
            "FROM imu_points WHERE sessionId = :sessionId"
    )
    suspend fun spanForSession(sessionId: Long): SeriesSpan

    // Columnas sueltas junto a MIN()/MAX(): SQLite las toma de la fila que tiene ese extremo.
    @Query(
        "SELECT id, sessionId, MIN(timestamp) AS timestamp, gLat, gLong, leanDeg FROM imu_points " +
            "WHERE sessionId = :sessionId " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun firstSampleBuckets(sessionId: Long, bucketMs: Long, limit: Int): List<ImuPointEntity>

    @Query(
        "SELECT id, sessionId, timestamp, gLat, MIN(gLong) AS gLong, leanDeg FROM imu_points " +
            "WHERE sessionId = :sessionId " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun minGLongBuckets(sessionId: Long, bucketMs: Long, limit: Int): List<ImuPointEntity>

    @Query(
        "SELECT id, sessionId, timestamp, gLat, MAX(gLong) AS gLong, leanDeg FROM imu_points " +
            "WHERE sessionId = :sessionId " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun maxGLongBuckets(sessionId: Long, bucketMs: Long, limit: Int): List<ImuPointEntity>

    @Query(
        "SELECT * FROM imu_points " +
            "WHERE sessionId = :sessionId " +
            "AND (timestamp > :afterTimestamp OR (timestamp = :afterTimestamp AND id > :afterId)) " +
            "ORDER BY timestamp ASC, id ASC LIMIT :pageSize"
    )
    suspend fun pageForSession(
        sessionId: Long,
        afterTimestamp: Long,
        afterId: Long,
        pageSize: Int,
    ): List<ImuPointEntity>
}

data class LapPeak(val lapId: Long, val value: Float?)
