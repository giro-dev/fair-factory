package dev.agiro.fanel.android.data

import com.google.gson.Gson
import dev.agiro.fanel.android.SyncPreferences
import dev.agiro.fanel.android.data.local.CalendarEventDao
import dev.agiro.fanel.android.data.local.CalendarEventEntity
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import dev.agiro.fanel.android.data.offline.IdRemap
import dev.agiro.fanel.android.data.offline.OperationHandler
import dev.agiro.fanel.android.data.offline.OutboxPusher
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
    private val pusher: OutboxPusher,
    private val syncPreferences: SyncPreferences
) : CalendarRepositoryContract, OperationHandler {
    private val gson = Gson()
    private val syncTimestamps = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)

    init {
        pusher.register(DOMAIN, this)
    }

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
        enqueueCreate(householdId, localId, gson.toJson(CreateEventRequest.from(draft)))
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
            // Still local-only: replace the queued create so only the latest data is sent.
            enqueueCreate(event.householdId, event.localId, gson.toJson(CreateEventRequest.from(draft)))
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
            // Deterministic id so repeated edits collapse into a single queued update.
            pusher.enqueue(
                event.householdId, DOMAIN, OP_UPDATE, event.localId,
                gson.toJson(UpdateEventRequest.from(draft)),
                id = "${event.localId}:UPDATE"
            )
        }
    }

    override suspend fun deleteEvent(event: CalendarEventEntity) {
        if (event.remoteId == null) {
            pusher.discard(event.householdId, DOMAIN, event.localId)
            eventDao.deleteByLocalId(event.householdId, event.localId)
        } else {
            eventDao.upsert(event.copy(pendingStatus = CalendarEventEntity.PENDING_DELETE))
            pusher.discard(event.householdId, DOMAIN, event.localId)
            pusher.enqueue(
                event.householdId, DOMAIN, OP_DELETE, event.localId, null,
                id = "${event.localId}:DELETE"
            )
        }
    }

    override suspend fun fullSync(householdId: String, from: String, to: String) {
        if (householdId.isBlank()) throw IllegalArgumentException("householdId is required")
        pusher.pushAll(householdId)
        pullVisibleRange(householdId, from, to)
        val now = System.currentTimeMillis()
        syncPreferences.setLastSuccessfulSync(householdId, now)
        syncTimestamps.tryEmit(householdId to now)
    }

    override suspend fun execute(operation: PendingOperationEntity): IdRemap? {
        val householdId = operation.householdId
        val targetId = operation.targetId ?: return null
        return when (operation.type) {
            OP_CREATE -> {
                val request = gson.fromJson(operation.payloadJson, CreateEventRequest::class.java)
                val created = api.create(householdId, request)
                if (eventDao.findByLocalId(householdId, targetId) != null) {
                    eventDao.deleteByLocalId(householdId, targetId)
                    eventDao.upsert(created.toEntity(householdId, targetId))
                }
                IdRemap(targetId, created.id)
            }

            OP_UPDATE -> {
                val entity = findEntity(householdId, targetId)
                api.update(
                    householdId,
                    entity?.remoteId ?: targetId,
                    gson.fromJson(operation.payloadJson, UpdateEventRequest::class.java)
                )
                entity?.let { eventDao.upsert(it.copy(pendingStatus = null)) }
                null
            }

            OP_DELETE -> {
                val entity = findEntity(householdId, targetId)
                api.delete(householdId, entity?.remoteId ?: targetId)
                eventDao.deleteByLocalId(householdId, entity?.localId ?: targetId)
                null
            }

            else -> null
        }
    }

    private suspend fun enqueueCreate(householdId: String, localId: String, payloadJson: String) {
        pusher.enqueue(householdId, DOMAIN, OP_CREATE, localId, payloadJson, id = localId)
    }

    /** Looks up the event by local id first, then by remote id (the target may have been remapped). */
    private suspend fun findEntity(householdId: String, id: String): CalendarEventEntity? =
        eventDao.findByLocalId(householdId, id) ?: eventDao.findByRemoteId(householdId, id)

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

    companion object {
        const val DOMAIN = "calendar"
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_DELETE = "DELETE"
    }
}
