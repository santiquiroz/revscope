package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "diag_events",
    foreignKeys = [
        ForeignKey(
            entity = DiagSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId", "timestamp")],
)
data class DiagEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val type: String,
    @ColumnInfo(defaultValue = "APP")
    val source: String = "APP",
    val title: String,
    @ColumnInfo(defaultValue = "")
    val summary: String = "",
    @ColumnInfo(defaultValue = "INFO")
    val verdict: String = "INFO",
    @ColumnInfo(defaultValue = "1")
    val payloadVersion: Int = 1,
    @ColumnInfo(defaultValue = "{}")
    val payloadJson: String = "{}",
    val attachmentPath: String? = null,
)
