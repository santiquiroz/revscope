package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "maintenance_items")
data class MaintenanceItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleProfileId: Long,
    val nombre: String,
    val intervaloKm: Double,
    val ultimoServicioKm: Double,
)
