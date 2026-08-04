package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Hueco/resalto detectado por el IMU del teléfono — mapa personal de peligros viales. */
@Entity(tableName = "potholes")
data class PotholeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    /** Pico vertical en G del golpe más fuerte registrado en este punto. */
    val severityG: Float,
    /** Veces que se ha vuelto a golpear aquí — confianza del reporte. */
    val hits: Int,
    val lastHitAt: Long,
)
