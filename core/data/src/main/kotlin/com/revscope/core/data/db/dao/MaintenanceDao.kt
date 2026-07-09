package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceDao {

    @Insert
    suspend fun insert(item: MaintenanceItemEntity): Long

    @Update
    suspend fun update(item: MaintenanceItemEntity)

    @Query("SELECT * FROM maintenance_items WHERE vehicleProfileId = :profileId")
    fun observeForProfile(profileId: Long): Flow<List<MaintenanceItemEntity>>

    @Query("SELECT * FROM maintenance_items WHERE vehicleProfileId = :profileId")
    suspend fun listForProfile(profileId: Long): List<MaintenanceItemEntity>
}
