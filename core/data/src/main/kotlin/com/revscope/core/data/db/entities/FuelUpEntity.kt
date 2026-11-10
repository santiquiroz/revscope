package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "fuel_ups", indices = [Index("vehicleProfileId")])
data class FuelUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleProfileId: Long,
    val timestamp: Long,
    val liters: Double,
    val fullTank: Boolean,
)
