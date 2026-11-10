package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.FuelUpEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelUpDao {
    @Insert
    suspend fun insert(item: FuelUpEntity): Long

    @Query("DELETE FROM fuel_ups WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM fuel_ups WHERE vehicleProfileId = :profileId ORDER BY timestamp ASC")
    fun observeForProfile(profileId: Long): Flow<List<FuelUpEntity>>
}
