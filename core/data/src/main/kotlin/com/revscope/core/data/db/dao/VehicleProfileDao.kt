package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.revscope.core.data.db.entities.VehicleProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: VehicleProfileEntity): Long

    @Update
    suspend fun update(profile: VehicleProfileEntity)

    @Query("SELECT * FROM vehicle_profiles ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE id = :id")
    suspend fun getById(id: Long): VehicleProfileEntity?

    @Query("DELETE FROM vehicle_profiles WHERE id = :id")
    suspend fun deleteById(id: Long)
}
