package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Los defaultValue deben coincidir con MIGRATION_18_19: Room valida ambos al arrancar.
@Entity(
    tableName = "diag_sessions",
    indices = [Index("vehicleProfileId")],
)
data class DiagSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val vehicleProfileId: Long = 0,
    val knowledgeKey: String? = null,
    val startedAt: Long,
    val closedAt: Long? = null,
    val title: String,
    // Nombres de Sintoma separados por coma.
    @ColumnInfo(defaultValue = "")
    val symptomTags: String = "",
    @ColumnInfo(defaultValue = "")
    val symptomsText: String = "",
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    val odometerKm: Double? = null,
    val baselineHealthReportId: Long? = null,
    @ColumnInfo(defaultValue = "")
    val interpretation: String = "",
    // Claves "P0122#2" de los pasos de guía marcados, separadas por coma.
    @ColumnInfo(defaultValue = "")
    val checkedSteps: String = "",
)
