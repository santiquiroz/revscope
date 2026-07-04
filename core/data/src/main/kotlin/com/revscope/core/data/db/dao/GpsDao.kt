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
}
