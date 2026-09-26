package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicle_knowledge")
data class VehicleKnowledgeEntity(
    @PrimaryKey
    val key: String,
    val displayName: String,
    val vehicleType: String,
    val ecu: String? = null,
    val ecuSource: String? = null,
    @ColumnInfo(defaultValue = "")
    val protocolNotes: String = "",
    // Arreglo JSON de notas con tipo, texto y fuente.
    @ColumnInfo(defaultValue = "")
    val notes: String = "",
    @ColumnInfo(defaultValue = "[]")
    val wiringJson: String = "[]",
    @ColumnInfo(defaultValue = "0")
    val seedVersion: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val userEdited: Boolean = false,
)
