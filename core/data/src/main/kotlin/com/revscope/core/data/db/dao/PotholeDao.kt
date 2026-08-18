package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.revscope.core.data.db.entities.PotholeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PotholeDao {

    @Query("SELECT * FROM potholes")
    suspend fun all(): List<PotholeEntity>

    @Query("SELECT * FROM potholes")
    fun observeAll(): Flow<List<PotholeEntity>>

    @Insert
    suspend fun insert(pothole: PotholeEntity): Long

    @Update
    suspend fun update(pothole: PotholeEntity)

    @Query("DELETE FROM potholes")
    suspend fun deleteAll()

    // ── Sync con el servidor colaborativo ────────────────────────────────────

    @Query("SELECT * FROM potholes WHERE source = 'LOCAL' AND syncedAt IS NULL")
    suspend fun unsynced(): List<PotholeEntity>

    @Query("UPDATE potholes SET syncedAt = :nowMs WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, nowMs: Long)

    /** El cache remoto se reemplaza completo en cada sync — el server es la fuente de verdad de lo ajeno. */
    @Query("DELETE FROM potholes WHERE source = 'REMOTE'")
    suspend fun deleteRemote()

    @Insert
    suspend fun insertAllRemote(potholes: List<PotholeEntity>)
}
