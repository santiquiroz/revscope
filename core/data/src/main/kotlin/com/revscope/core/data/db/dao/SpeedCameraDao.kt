package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.revscope.core.data.db.entities.SpeedCameraEntity

@Dao
interface SpeedCameraDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cameras: List<SpeedCameraEntity>)

    @Query("SELECT * FROM speed_cameras")
    suspend fun all(): List<SpeedCameraEntity>

    @Query("SELECT COUNT(*) FROM speed_cameras")
    suspend fun count(): Int

    @Query("DELETE FROM speed_cameras")
    suspend fun deleteAll()
}
