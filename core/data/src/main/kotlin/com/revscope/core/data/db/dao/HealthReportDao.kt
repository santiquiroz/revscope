package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.revscope.core.data.db.entities.HealthReportEntity

@Dao
interface HealthReportDao {

    @Insert
    suspend fun insert(report: HealthReportEntity): Long

    @Query("SELECT * FROM health_reports ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): HealthReportEntity?

    @Query("SELECT * FROM health_reports WHERE id = :id")
    suspend fun getById(id: Long): HealthReportEntity?

    @Query(
        "SELECT * FROM health_reports WHERE vehicleProfileId = :profileId AND timestamp < :before " +
            "ORDER BY timestamp DESC LIMIT 1",
    )
    suspend fun latestForProfileBefore(profileId: Long, before: Long): HealthReportEntity?

    @Query(
        "SELECT * FROM health_reports WHERE vehicleProfileId = :profileId AND timestamp < :before " +
            "ORDER BY timestamp DESC LIMIT :limit",
    )
    suspend fun recentForProfileBefore(profileId: Long, before: Long, limit: Int): List<HealthReportEntity>
}
