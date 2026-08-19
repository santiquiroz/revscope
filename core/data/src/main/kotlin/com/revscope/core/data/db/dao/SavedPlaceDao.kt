package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.revscope.core.data.db.entities.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_places ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Insert
    suspend fun insert(place: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE saved_places SET lastUsedAt = :nowMs WHERE id = :id")
    suspend fun touch(id: Long, nowMs: Long)

    @Query("DELETE FROM saved_places WHERE type = :type")
    suspend fun deleteByType(type: String)

    /** HOME y WORK son únicos: reemplazar es borrar el anterior e insertar el nuevo. */
    @Transaction
    suspend fun upsertSpecial(place: SavedPlaceEntity) {
        deleteByType(place.type)
        insert(place)
    }

    @Query(
        "DELETE FROM saved_places WHERE type = 'RECENT' AND id NOT IN " +
            "(SELECT id FROM saved_places WHERE type = 'RECENT' ORDER BY lastUsedAt DESC LIMIT 20)",
    )
    suspend fun pruneRecents()

    @Query("DELETE FROM saved_places WHERE type = :type AND name = :name AND lat = :lat AND lon = :lon")
    suspend fun deleteMatching(type: String, name: String, lat: Double, lon: Double)

    @Query("SELECT COUNT(*) FROM saved_places WHERE type = 'FAVORITE' AND name = :name AND lat = :lat AND lon = :lon")
    suspend fun countFavorite(name: String, lat: Double, lon: Double): Int

    /** Un destino usado entra al historial; el historial no crece sin tope (LRU 20). */
    @Transaction
    suspend fun recordRecent(place: SavedPlaceEntity) {
        deleteMatching("RECENT", place.name, place.lat, place.lon)
        insert(place)
        pruneRecents()
    }
}
