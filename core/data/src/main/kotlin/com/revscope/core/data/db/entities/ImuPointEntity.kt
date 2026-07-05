package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "imu_points",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sessionId", "timestamp")],
)
data class ImuPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,   // epoch ms
    /** Lateral G (cornering): + right, − left, vehicle frame via GPS heading */
    val gLat: Float,
    /** Longitudinal G: + acceleration, − braking */
    val gLong: Float,
    /** Vehicle roll vs calibrated mount, degrees: + right lean */
    val leanDeg: Float,
)
