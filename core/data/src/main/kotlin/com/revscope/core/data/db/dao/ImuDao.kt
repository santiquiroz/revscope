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
}
