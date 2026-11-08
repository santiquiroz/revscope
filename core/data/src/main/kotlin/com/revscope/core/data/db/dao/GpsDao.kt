package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revscope.core.data.db.entities.GpsPointEntity

@Dao
interface GpsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<GpsPointEntity>)

    @Query("SELECT * FROM gps_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun pointsForSession(sessionId: Long): List<GpsPointEntity>

    @Query("SELECT MAX(speedKmh) FROM gps_points WHERE sessionId = :sessionId")
    suspend fun maxSpeed(sessionId: Long): Float?

    @Query("SELECT COUNT(*) FROM gps_points WHERE sessionId = :sessionId")
    suspend fun countForSession(sessionId: Long): Int

    @Query(
        "SELECT COUNT(*) AS count, MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs " +
            "FROM gps_points WHERE sessionId = :sessionId"
    )
    suspend fun spanForSession(sessionId: Long): SeriesSpan

    // Columnas sueltas junto a MIN(): SQLite las toma de la fila con el mínimo, o sea la primera del balde.
    @Query(
        "SELECT id, sessionId, MIN(timestamp) AS timestamp, latitude, longitude, speedKmh FROM gps_points " +
            "WHERE sessionId = :sessionId " +
            "GROUP BY timestamp / :bucketMs ORDER BY timestamp ASC LIMIT :limit"
    )
    suspend fun firstSampleBuckets(sessionId: Long, bucketMs: Long, limit: Int): List<GpsPointEntity>

    @Query(
        "SELECT * FROM gps_points " +
            "WHERE sessionId = :sessionId " +
            "AND (timestamp > :afterTimestamp OR (timestamp = :afterTimestamp AND id > :afterId)) " +
            "ORDER BY timestamp ASC, id ASC LIMIT :pageSize"
    )
    suspend fun pageForSession(
        sessionId: Long,
        afterTimestamp: Long,
        afterId: Long,
        pageSize: Int,
    ): List<GpsPointEntity>
}
