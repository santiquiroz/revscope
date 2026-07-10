package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    /**
     * Replaces the whole table atomically. Downloads re-encode OSM element ids
     * (node/way/relation share one numeric namespace) so a plain upsert could
     * leave stale rows behind from a previous encoding — wipe first instead.
     */
    @Transaction
    suspend fun replaceAll(cameras: List<SpeedCameraEntity>) {
        deleteAll()
        insertAll(cameras)
    }
}
