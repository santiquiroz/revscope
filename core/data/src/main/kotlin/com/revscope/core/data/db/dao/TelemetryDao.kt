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
}
