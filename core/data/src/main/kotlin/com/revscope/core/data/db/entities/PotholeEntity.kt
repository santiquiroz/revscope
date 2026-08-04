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
    /** LOCAL = detectado por este teléfono; REMOTE = bajado del servidor colaborativo. */
    val source: String = SOURCE_LOCAL,
    /** Epoch ms de la última subida al servidor — null = pendiente de sync. Solo aplica a LOCAL. */
    val syncedAt: Long? = null,
) {
    companion object {
        const val SOURCE_LOCAL = "LOCAL"
        const val SOURCE_REMOTE = "REMOTE"
    }
}
