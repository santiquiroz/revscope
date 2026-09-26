package com.revscope.core.data.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reference_bands",
    indices = [Index("knowledgeKey", "bandKey", unique = true)],
)
data class ReferenceBandEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val knowledgeKey: String,
    val bandKey: String,
    val minValue: Double?,
    val maxValue: Double?,
    val unit: String,
    val origin: String,
    @ColumnInfo(defaultValue = "")
    val source: String = "",
)
