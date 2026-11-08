package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.HrPointEntity

@Dao
interface HrDao {

    @Insert
    suspend fun insert(point: HrPointEntity)

    @Query("SELECT * FROM hr_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun pointsForSession(sessionId: Long): List<HrPointEntity>

    @Query("SELECT MAX(bpm) FROM hr_points WHERE sessionId = :sessionId")
    suspend fun maxBpm(sessionId: Long): Float?

    @Query("SELECT AVG(bpm) FROM hr_points WHERE sessionId = :sessionId")
    suspend fun avgBpm(sessionId: Long): Float?

    @Query(
        "SELECT MAX(bpm) FROM hr_points WHERE sessionId = :sessionId AND timestamp BETWEEN :fromMs AND :toMs"
    )
    suspend fun maxBpmBetween(sessionId: Long, fromMs: Long, toMs: Long): Float?

    @Query(
        "SELECT COUNT(*) AS count, MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs " +
            "FROM hr_points WHERE sessionId = :sessionId"
    )
    suspend fun spanForSession(sessionId: Long): SeriesSpan

    @Query(
        "SELECT MIN(timestamp) AS timestamp, AVG(bpm) AS value FROM hr_points " +
            "WHERE sessionId = :sessionId " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun avgBuckets(sessionId: Long, bucketMs: Long, limit: Int): List<TimedValue>

    @Query(
        "SELECT l.id AS lapId, MAX(h.bpm) AS value FROM laps l " +
            "LEFT JOIN hr_points h ON h.sessionId = l.sessionId " +
            "AND h.timestamp BETWEEN l.completedAt - l.timeMs AND l.completedAt " +
            "WHERE l.sessionId = :sessionId GROUP BY l.id"
    )
    suspend fun lapMaxBpm(sessionId: Long): List<LapPeak>

    @Query(
        "SELECT * FROM hr_points " +
            "WHERE sessionId = :sessionId " +
            "AND (timestamp > :afterTimestamp OR (timestamp = :afterTimestamp AND id > :afterId)) " +
            "ORDER BY timestamp ASC, id ASC LIMIT :pageSize"
    )
    suspend fun pageForSession(
        sessionId: Long,
        afterTimestamp: Long,
        afterId: Long,
        pageSize: Int,
    ): List<HrPointEntity>
}
