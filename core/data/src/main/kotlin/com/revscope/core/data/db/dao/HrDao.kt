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
}
