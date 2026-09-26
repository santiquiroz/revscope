package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicle_parts",
    foreignKeys = [
        ForeignKey(
            entity = VehicleKnowledgeEntity::class,
            parentColumns = ["key"],
            childColumns = ["knowledgeKey"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("knowledgeKey")],
)
data class VehiclePartEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val knowledgeKey: String,
    val role: String,
    val partNumber: String,
    val description: String,
    val kind: String,
    @ColumnInfo(defaultValue = "")
    val note: String = "",
    @ColumnInfo(defaultValue = "")
    val source: String = "",
)
