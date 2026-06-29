package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // "Mazda CX-30"
    val type: String,           // "CAR" | "MOTORCYCLE"
    val vin: String?,
    /** JSON array of active PID strings: ["0C","0D","05"] */
    val enabledPids: String,
    /** JSON array of gear ratio floats: [3.5,2.1,1.4,1.0,0.8,0.6] — null if unknown */
    val gearRatios: String?,
    val createdAt: Long,        // epoch ms
)
