package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revscope.core.data.db.entities.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(SUM(distanceKm),0) FROM sessions WHERE vehicleProfileId = :profileId")
    fun observeSumDistanceKmForProfile(profileId: Long): Flow<Double>

    /** App-tracked distance (OBD or GPS session) for a profile between two odometer readings. */
    @Query(
        "SELECT COALESCE(SUM(distanceKm),0) FROM sessions " +
            "WHERE vehicleProfileId = :profileId AND startedAt >= :fromEpochMs AND startedAt < :toEpochMs",
    )
    suspend fun sumDistanceKmBetween(profileId: Long, fromEpochMs: Long, toEpochMs: Long): Double

    /** Most recent completed trips for a profile — feeds the Mecánico IA vehicle context. */
    @Query(
        "SELECT * FROM sessions WHERE vehicleProfileId = :profileId AND endedAt IS NOT NULL " +
            "ORDER BY startedAt DESC LIMIT :limit",
    )
    suspend fun getRecentForProfile(profileId: Long, limit: Int): List<SessionEntity>
}
