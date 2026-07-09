package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_reports")
data class HealthReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleProfileId: Long,
    val timestamp: Long,
    /** JSON array of {area, nivel, titulo, causa} produced by the health check */
    val resultsJson: String,
)
