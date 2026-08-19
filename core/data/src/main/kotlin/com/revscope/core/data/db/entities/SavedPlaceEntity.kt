package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un lugar guardado del mapa. type: HOME | WORK | FAVORITE | RECENT. */
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val lastUsedAt: Long,
)
