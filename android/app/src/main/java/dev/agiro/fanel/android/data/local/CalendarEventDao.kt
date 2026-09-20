package dev.agiro.fanel.android.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query(
        "SELECT * FROM calendar_events " +
            "WHERE householdId = :householdId AND date BETWEEN :from AND :to " +
            "AND (pendingStatus IS NULL OR pendingStatus != 'PENDING_DELETE') " +
            "ORDER BY date, time"
    )
    fun observeEvents(householdId: String, from: String, to: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE householdId = :householdId AND localId = :localId")
    suspend fun findByLocalId(householdId: String, localId: String): CalendarEventEntity?

    @Query(
        "DELETE FROM calendar_events " +
            "WHERE householdId = :householdId AND remoteId IS NOT NULL AND pendingStatus IS NULL " +
            "AND date BETWEEN :from AND :to"
    )
    suspend fun deleteSyncedInRange(householdId: String, from: String, to: String)

    @Query("DELETE FROM calendar_events WHERE householdId = :householdId AND localId = :localId")
    suspend fun deleteByLocalId(householdId: String, localId: String)

    @Query(
        "UPDATE calendar_events SET title = :title, time = :time, durationMinutes = :durationMinutes, " +
            "assigneeIds = :assigneeIds, " +
            "recurrenceFreq = :recurrenceFreq, recurrenceInterval = :recurrenceInterval, " +
            "recurrenceUntil = :recurrenceUntil " +
            "WHERE householdId = :householdId AND remoteId = :remoteId"
    )
    suspend fun applyUpdateByRemoteId(
        householdId: String,
        remoteId: String,
        title: String,
        time: String?,
        durationMinutes: Int?,
        assigneeIds: String,
        recurrenceFreq: String?,
        recurrenceInterval: Int?,
        recurrenceUntil: String?
    )

    @Upsert
    suspend fun upsert(event: CalendarEventEntity)

    @Upsert
    suspend fun upsert(events: List<CalendarEventEntity>)
}
