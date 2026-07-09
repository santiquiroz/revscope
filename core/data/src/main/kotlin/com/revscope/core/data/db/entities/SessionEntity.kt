package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleProfileId: Long,
    val startedAt: Long,    // epoch ms
    val endedAt: Long?,
    val adapterName: String,
    val maxRpm: Int,
    val maxSpeed: Int,
    val distanceKm: Float,
    /** Best automatic launch times this session — null if no full run happened */
    val best0to60Ms: Long? = null,
    val best0to100Ms: Long? = null,
    /** Estimated liters consumed this trip — null if fuel rate/MAF data was unavailable */
    val fuelLiters: Double? = null,
    /** Estimated cost in COP at the fuel price configured at session end */
    val fuelCostCop: Double? = null,
    /** Eco-driving score 0-100 — null if there wasn't enough telemetry to compute it */
    val ecoScore: Int? = null,
)
