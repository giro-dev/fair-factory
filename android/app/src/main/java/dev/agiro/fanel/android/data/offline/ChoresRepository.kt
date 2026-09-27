package dev.agiro.fanel.android.data.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.agiro.fanel.android.data.local.CachedSnapshotDao
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import dev.agiro.fanel.android.data.remote.ChoreDto
import dev.agiro.fanel.android.data.remote.ChoresApi
import dev.agiro.fanel.android.data.remote.CreateChoreRequest
import dev.agiro.fanel.android.data.remote.UpdateChoreRequest
import dev.agiro.fanel.android.data.remote.UpdateRecurrenceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ChoresRepository(
    private val api: ChoresApi,
    snapshotDao: CachedSnapshotDao,
    pendingDao: PendingOperationDao,
    pusher: OutboxPusher,
    gson: Gson = Gson()
) : SnapshotRepository<List<ChoreDto>>(
    DOMAIN, snapshotDao, pendingDao, pusher, gson, object : TypeToken<List<ChoreDto>>() {}
) {

    fun observeChores(householdId: String): Flow<List<ChoreDto>> =
        observe(householdId).map { it.orEmpty() }

    suspend fun create(householdId: String, request: CreateChoreRequest): String {
        val localId = newLocalId()
        val chore = ChoreDto(
            id = localId,
            householdId = householdId,
            title = request.title,
            assigneeId = request.assigneeId,
            done = false,
            createdAt = null,
            dueDate = request.dueDate,
            recurrenceFreq = request.recurrenceFreq,
            recurrenceInterval = request.recurrenceInterval,
            rotationMemberIds = request.rotationMemberIds
        )
        mutate(householdId, empty = ::emptyList) { it + chore }
        enqueue(householdId, OP_CREATE, localId, request)
        return localId
    }

    suspend fun update(householdId: String, choreId: String, request: UpdateChoreRequest) {
        mutate(householdId, empty = ::emptyList) { chores ->
            chores.map { chore ->
                if (chore.id != choreId) chore else chore.copy(
                    title = request.title ?: chore.title,
                    assigneeId = request.assigneeId ?: chore.assigneeId,
                    done = request.done ?: chore.done
                )
            }
        }
        enqueue(householdId, OP_UPDATE, choreId, request)
    }

    suspend fun updateRecurrence(householdId: String, choreId: String, request: UpdateRecurrenceRequest) {
        mutate(householdId, empty = ::emptyList) { chores ->
            chores.map { chore ->
                if (chore.id != choreId) chore else chore.copy(
                    dueDate = request.dueDate,
                    recurrenceFreq = request.recurrenceFreq,
                    recurrenceInterval = request.recurrenceInterval,
                    rotationMemberIds = request.rotationMemberIds
                )
            }
        }
        enqueue(householdId, OP_UPDATE_RECURRENCE, choreId, request)
    }

    suspend fun delete(householdId: String, choreId: String) {
        mutate(householdId, empty = ::emptyList) { chores -> chores.filterNot { it.id == choreId } }
        if (!discardPending(householdId, choreId)) enqueue(householdId, OP_DELETE, choreId)
    }

    override suspend fun pull(householdId: String) {
        val remote = api.list(householdId)
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val unsynced = cached(householdId).orEmpty().filter { isLocalId(it.id) && it.id !in remoteIds }
        write(householdId, remote + unsynced)
    }

    override suspend fun execute(operation: PendingOperationEntity): IdRemap? {
        val householdId = operation.householdId
        return when (operation.type) {
            OP_CREATE -> {
                val created = api.create(householdId, payload<CreateChoreRequest>(operation))
                val localId = operation.targetId ?: return null
                replace(householdId, localId, created)
                IdRemap(localId, created.id)
            }
            OP_UPDATE -> {
                val id = requireNotNull(operation.targetId)
                replace(householdId, id, api.update(householdId, id, payload<UpdateChoreRequest>(operation)))
                null
            }
            OP_UPDATE_RECURRENCE -> {
                val id = requireNotNull(operation.targetId)
                replace(householdId, id, api.updateRecurrence(householdId, id, payload<UpdateRecurrenceRequest>(operation)))
                null
            }
            OP_DELETE -> {
                api.delete(householdId, requireNotNull(operation.targetId))
                null
            }
            else -> null
        }
    }

    private suspend fun replace(householdId: String, id: String, chore: ChoreDto) {
        mutate(householdId, empty = ::emptyList) { chores -> chores.map { if (it.id == id) chore else it } }
    }

    companion object {
        const val DOMAIN = "chores"
        const val OP_CREATE = "CREATE"
        const val OP_UPDATE = "UPDATE"
        const val OP_UPDATE_RECURRENCE = "UPDATE_RECURRENCE"
        const val OP_DELETE = "DELETE"
    }
}
