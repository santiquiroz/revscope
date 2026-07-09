package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Fixed speed camera from OpenStreetMap (highway=speed_camera). */
@Entity(tableName = "speed_cameras")
data class SpeedCameraEntity(
    /** OSM node id — stable across re-downloads */
    @PrimaryKey val osmId: Long,
    val latitude: Double,
    val longitude: Double,
    /** Posted limit if OSM has it (maxspeed tag), km/h */
    val maxSpeedKmh: Int?,
)
