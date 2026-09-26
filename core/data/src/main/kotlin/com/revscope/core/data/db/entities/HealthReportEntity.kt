package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_reports")
data class HealthReportEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleProfileId: Long,
    val timestamp: Long,
    /** v1: array de {area, nivel, titulo, causa}; v2: {"v":2,"items":[…],"metricas":{…}} (HealthReportFormato en core:obd) */
    val resultsJson: String,
)
