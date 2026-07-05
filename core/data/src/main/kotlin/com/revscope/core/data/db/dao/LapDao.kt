package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.LapEntity

@Dao
interface LapDao {

    @Insert
    suspend fun insert(lap: LapEntity)

    @Query("SELECT * FROM laps WHERE sessionId = :sessionId ORDER BY lapNumber ASC")
    suspend fun lapsForSession(sessionId: Long): List<LapEntity>
}
