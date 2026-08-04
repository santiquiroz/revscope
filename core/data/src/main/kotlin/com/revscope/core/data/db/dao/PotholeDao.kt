package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.revscope.core.data.db.entities.PotholeEntity

@Dao
interface PotholeDao {

    @Query("SELECT * FROM potholes")
    suspend fun all(): List<PotholeEntity>

    @Insert
    suspend fun insert(pothole: PotholeEntity): Long

    @Update
    suspend fun update(pothole: PotholeEntity)

    @Query("DELETE FROM potholes")
    suspend fun deleteAll()
}
