package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.revscope.core.data.db.entities.ReferenceBandEntity
import com.revscope.core.data.db.entities.VehicleKnowledgeEntity
import com.revscope.core.data.db.entities.VehiclePartEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class VehicleKnowledgeDao {

    @Query("SELECT * FROM vehicle_knowledge WHERE `key` = :key")
    abstract suspend fun getKnowledge(key: String): VehicleKnowledgeEntity?

    @Query("SELECT * FROM vehicle_knowledge ORDER BY displayName")
    abstract fun observeAll(): Flow<List<VehicleKnowledgeEntity>>

    // Upsert actualiza sin borrar: un REPLACE dispararía el ON DELETE CASCADE de los repuestos.
    @Upsert
    abstract suspend fun upsertKnowledge(knowledge: VehicleKnowledgeEntity)

    // Marca el modelo como editado: la semilla ya no pisa el cableado que anotó el técnico.
    @Query("UPDATE vehicle_knowledge SET wiringJson = :wiringJson, userEdited = 1 WHERE `key` = :key")
    abstract suspend fun updateWiring(key: String, wiringJson: String): Int

    @Query("SELECT * FROM vehicle_parts WHERE knowledgeKey = :key ORDER BY id")
    abstract suspend fun getParts(key: String): List<VehiclePartEntity>

    @Insert
    abstract suspend fun insertParts(parts: List<VehiclePartEntity>)

    @Query("DELETE FROM vehicle_parts WHERE knowledgeKey = :key")
    abstract suspend fun deleteParts(key: String)

    @Query("SELECT * FROM reference_bands WHERE knowledgeKey = :key ORDER BY bandKey")
    abstract suspend fun getBands(key: String): List<ReferenceBandEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun replaceBand(band: ReferenceBandEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertBandsKeepingExisting(bands: List<ReferenceBandEntity>)

    @Query("DELETE FROM reference_bands WHERE knowledgeKey = :key AND bandKey = :bandKey")
    abstract suspend fun deleteBand(key: String, bandKey: String): Int

    @Query("DELETE FROM reference_bands WHERE knowledgeKey = :key AND origin = :origin")
    abstract suspend fun deleteBandsWithOrigin(key: String, origin: String)

    // Las bandas de otro origen (las del usuario) se conservan: la semilla no las pisa.
    @Transaction
    open suspend fun replaceSeed(
        knowledge: VehicleKnowledgeEntity,
        parts: List<VehiclePartEntity>,
        bands: List<ReferenceBandEntity>,
        seedBandOrigin: String,
    ) {
        upsertKnowledge(knowledge)
        deleteParts(knowledge.key)
        insertParts(parts)
        deleteBandsWithOrigin(knowledge.key, seedBandOrigin)
        insertBandsKeepingExisting(bands)
    }
}
