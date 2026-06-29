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
)
