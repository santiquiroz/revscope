package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revscope.core.data.db.entities.TelemetryPointEntity
import kotlinx.coroutines.flow.Flow

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
}
