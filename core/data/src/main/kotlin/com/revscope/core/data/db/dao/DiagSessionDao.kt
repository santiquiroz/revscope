package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.revscope.core.data.db.entities.DiagEventEntity
import com.revscope.core.data.db.entities.DiagSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DiagSessionDao {

    @Insert
    abstract suspend fun insertSession(session: DiagSessionEntity): Long

    @Update
    abstract suspend fun updateSession(session: DiagSessionEntity)

    @Query("SELECT * FROM diag_sessions WHERE id = :id")
    abstract suspend fun getSession(id: Long): DiagSessionEntity?

    @Query(OPEN_SESSION)
    abstract suspend fun getOpenSession(vehicleProfileId: Long): DiagSessionEntity?

    @Query(OPEN_SESSION)
    abstract fun observeOpenSession(vehicleProfileId: Long): Flow<DiagSessionEntity?>

    @Query("SELECT * FROM diag_sessions WHERE vehicleProfileId = :vehicleProfileId ORDER BY startedAt DESC, id DESC")
    abstract fun observeSessions(vehicleProfileId: Long): Flow<List<DiagSessionEntity>>

    @Query("UPDATE diag_sessions SET closedAt = :closedAt WHERE vehicleProfileId = :vehicleProfileId AND closedAt IS NULL")
    abstract suspend fun closeOpenSessions(vehicleProfileId: Long, closedAt: Long): Int

    @Query("UPDATE diag_sessions SET closedAt = :closedAt WHERE id = :id AND closedAt IS NULL")
    abstract suspend fun closeSession(id: Long, closedAt: Long): Int

    @Query("DELETE FROM diag_sessions WHERE id = :id")
    abstract suspend fun deleteSession(id: Long): Int

    // Una sola sesión abierta por vehículo: abrir una nueva cierra la anterior en la misma transacción.
    @Transaction
    open suspend fun openClosingPrevious(session: DiagSessionEntity): Long {
        closeOpenSessions(session.vehicleProfileId, session.startedAt)
        return insertSession(session)
    }

    @Insert
    abstract suspend fun insertEvent(event: DiagEventEntity): Long

    @Query(EVENTS_OF_SESSION)
    abstract suspend fun getEvents(sessionId: Long): List<DiagEventEntity>

    @Query(EVENTS_OF_SESSION)
    abstract fun observeEvents(sessionId: Long): Flow<List<DiagEventEntity>>

    private companion object {
        const val OPEN_SESSION =
            "SELECT * FROM diag_sessions WHERE vehicleProfileId = :vehicleProfileId AND closedAt IS NULL " +
                "ORDER BY startedAt DESC, id DESC LIMIT 1"
        const val EVENTS_OF_SESSION =
            "SELECT * FROM diag_events WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC"
    }
}
