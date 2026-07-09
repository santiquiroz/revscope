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
}
