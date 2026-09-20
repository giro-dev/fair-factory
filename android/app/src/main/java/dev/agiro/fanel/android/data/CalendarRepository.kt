package dev.agiro.fanel.android.data

import com.google.gson.Gson
import dev.agiro.fanel.android.SyncPreferences
import dev.agiro.fanel.android.data.local.CalendarEventDao
import dev.agiro.fanel.android.data.local.CalendarEventEntity
import dev.agiro.fanel.android.data.local.OutboxDao
import dev.agiro.fanel.android.data.local.OutboxEntity
import dev.agiro.fanel.android.data.remote.CalendarApi
import dev.agiro.fanel.android.data.remote.CalendarEventDto
import dev.agiro.fanel.android.data.remote.CreateEventRequest
import dev.agiro.fanel.android.data.remote.UpdateEventRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import java.util.UUID

class CalendarRepository(
    private val api: CalendarApi,
    private val eventDao: CalendarEventDao,
    private val outboxDao: OutboxDao,
    private val syncPreferences: SyncPreferences
) : CalendarRepositoryContract {
    private val gson = Gson()
    private val syncTimestamps = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)

    override fun observeEvents(
        householdId: String,
        from: String,
        to: String
    ): Flow<List<CalendarEventEntity>> = eventDao.observeEvents(householdId, from, to)

    override fun lastSuccessfulSync(householdId: String): Flow<Long> = syncTimestamps
        .filter { it.first == householdId }
        .map { it.second }
        .onStart { emit(syncPreferences.getLastSuccessfulSync(householdId)) }

    override suspend fun createEvent(draft: CalendarDraft, householdId: String) {
        val localId = UUID.randomUUID().toString()
        eventDao.upsert(
            CalendarEventEntity(
                householdId = householdId,
                occurrenceKey = localId,
                localId = localId,
                remoteId = null,
                title = draft.title,
                date = draft.date,
                anchorDate = draft.date,
                time = draft.time,
                durationMinutes = draft.durationMinutes,
                addedBy = draft.addedBy,
                assigneeIds = draft.assigneeIds.joinToString(","),
                recurrenceFreq = draft.recurrenceFreq,
                recurrenceInterval = draft.recurrenceInterval,
                recurrenceUntil = draft.recurrenceUntil,
                pendingStatus = CalendarEventEntity.PENDING_CREATE
            )
        )
        outboxDao.upsert(
            OutboxEntity(
                localId = localId,
                householdId = householdId,
                remoteId = null,
                operation = OutboxEntity.OP_CREATE,
                payloadJson = gson.toJson(CreateEventRequest.from(draft)),
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    override suspend fun updateEvent(event: CalendarEventEntity, draft: CalendarDraft) {
        val remoteId = event.remoteId
        if (remoteId == null) {
            eventDao.upsert(
                event.copy(
                    title = draft.title,
                    date = draft.date,
                    anchorDate = draft.date,
                    time = draft.time,
                    durationMinutes = draft.durationMinutes,
                    addedBy = draft.addedBy,
                    assigneeIds = draft.assigneeIds.joinToString(","),
                    recurrenceFreq = draft.recurrenceFreq,
                    recurrenceInterval = draft.recurrenceInterval,
                    recurrenceUntil = draft.recurrenceUntil
                )
            )
            outboxDao.upsert(
                OutboxEntity(
                    localId = event.localId,
                    householdId = event.householdId,
                    remoteId = null,
                    operation = OutboxEntity.OP_CREATE,
                    payloadJson = gson.toJson(CreateEventRequest.from(draft)),
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        } else {
            eventDao.applyUpdateByRemoteId(
                householdId = event.householdId,
                remoteId = remoteId,
                title = draft.title,
                time = draft.time,
                durationMinutes = draft.durationMinutes,
                assigneeIds = draft.assigneeIds.joinToString(","),
                recurrenceFreq = draft.recurrenceFreq,
                recurrenceInterval = draft.recurrenceInterval,
                recurrenceUntil = draft.recurrenceUntil
            )
            eventDao.upsert(
                event.copy(
                    title = draft.title,
                    time = draft.time,
                    durationMinutes = draft.durationMinutes,
                    anchorDate = draft.date,
                    assigneeIds = draft.assigneeIds.joinToString(","),
                    recurrenceFreq = draft.recurrenceFreq,
                    recurrenceInterval = draft.recurrenceInterval,
                    recurrenceUntil = draft.recurrenceUntil,
                    pendingStatus = CalendarEventEntity.PENDING_UPDATE
                )
            )
            outboxDao.upsert(
                OutboxEntity(
                    localId = event.localId,
                    householdId = event.householdId,
                    remoteId = remoteId,
                    operation = OutboxEntity.OP_UPDATE,
                    payloadJson = gson.toJson(UpdateEventRequest.from(draft)),
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun deleteEvent(event: CalendarEventEntity) {
        val remoteId = event.remoteId
        if (remoteId == null) {
            outboxDao.delete(event.localId)
            eventDao.deleteByLocalId(event.householdId, event.localId)
        } else {
            eventDao.upsert(event.copy(pendingStatus = CalendarEventEntity.PENDING_DELETE))
            outboxDao.upsert(
                OutboxEntity(
                    localId = event.localId,
                    householdId = event.householdId,
                    remoteId = remoteId,
                    operation = OutboxEntity.OP_DELETE,
                    payloadJson = null,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun fullSync(householdId: String, from: String, to: String) {
        if (householdId.isBlank()) throw IllegalArgumentException("householdId is required")
        pushPending(householdId)
        pullVisibleRange(householdId, from, to)
        val now = System.currentTimeMillis()
        syncPreferences.setLastSuccessfulSync(householdId, now)
        syncTimestamps.tryEmit(householdId to now)
    }

    private suspend fun pushPending(householdId: String) {
        for (entry in outboxDao.pendingForHousehold(householdId)) {
            when (entry.operation) {
                OutboxEntity.OP_CREATE -> {
                    val request = gson.fromJson(entry.payloadJson, CreateEventRequest::class.java)
                    val created = api.create(householdId, request)
                    val entity = eventDao.findByLocalId(householdId, entry.localId)
                    if (entity != null) {
                        eventDao.deleteByLocalId(householdId, entry.localId)
                        eventDao.upsert(created.toEntity(householdId, entry.localId))
                    }
                    outboxDao.delete(entry.localId)
                }

                OutboxEntity.OP_UPDATE -> {
                    val remoteId = entry.remoteId ?: continue
                    val request = gson.fromJson(entry.payloadJson, UpdateEventRequest::class.java)
                    api.update(householdId, remoteId, request)
                    val entity = eventDao.findByLocalId(householdId, entry.localId)
                    if (entity != null) {
                        eventDao.upsert(entity.copy(pendingStatus = null))
                    }
                    outboxDao.delete(entry.localId)
                }

                OutboxEntity.OP_DELETE -> {
                    entry.remoteId?.let { api.delete(householdId, it) }
                    eventDao.deleteByLocalId(householdId, entry.localId)
                    outboxDao.delete(entry.localId)
                }
            }
        }
    }

    private suspend fun pullVisibleRange(householdId: String, from: String, to: String) {
        val remote = api.list(householdId, from, to)
        eventDao.deleteSyncedInRange(householdId, from, to)
        eventDao.upsert(remote.map { it.toEntity(householdId, localId = it.id) })
    }

    private fun CalendarEventDto.toEntity(householdId: String, localId: String): CalendarEventEntity =
        CalendarEventEntity(
            householdId = householdId,
            occurrenceKey = "$id:$date",
            localId = localId,
            remoteId = id,
            title = title,
            date = date,
            anchorDate = anchorDate,
            time = time,
            durationMinutes = durationMinutes,
            addedBy = addedBy,
            assigneeIds = assigneeIds.orEmpty().joinToString(","),
            recurrenceFreq = recurrenceFreq,
            recurrenceInterval = recurrenceInterval,
            recurrenceUntil = recurrenceUntil,
            pendingStatus = null
        )
}
