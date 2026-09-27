package dev.agiro.fanel.android.data.offline

import android.util.Log
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import retrofit2.HttpException
import java.util.UUID

/** Result of pushing a create operation: the server id that replaces a locally generated one. */
data class IdRemap(val localId: String, val remoteId: String)

fun interface OperationHandler {
    suspend fun execute(operation: PendingOperationEntity): IdRemap?
}

/**
 * Replays every pending operation of a household in chronological order, regardless of domain,
 * so cross-domain references (e.g. a menu slot pointing to a recipe created offline) resolve
 * before they are sent. Operations rejected by the server as invalid are dropped (and surfaced
 * through [dropped]); transient failures abort the push and leave the remaining queue intact
 * for the next attempt.
 */
class OutboxPusher(private val pendingDao: PendingOperationDao) {
    private val handlers = mutableMapOf<String, OperationHandler>()

    private val _dropped = MutableSharedFlow<PendingOperationEntity>(extraBufferCapacity = 16)

    /** Operations the server rejected as unrecoverable; emitted right after they are removed. */
    val dropped: SharedFlow<PendingOperationEntity> = _dropped

    fun register(domain: String, handler: OperationHandler) {
        handlers[domain] = handler
    }

    suspend fun enqueue(
        householdId: String,
        domain: String,
        type: String,
        targetId: String?,
        payloadJson: String?,
        id: String = UUID.randomUUID().toString(),
        clock: () -> Long = System::currentTimeMillis
    ) {
        pendingDao.upsert(PendingOperationEntity(id, householdId, domain, type, targetId, payloadJson, clock()))
    }

    /** Drops every queued operation of [domain] targeting [targetId]. */
    suspend fun discard(householdId: String, domain: String, targetId: String) {
        pendingDao.deleteByTarget(householdId, domain, targetId)
    }

    suspend fun pushAll(householdId: String) {
        for (operation in pendingDao.pendingForHousehold(householdId)) {
            val handler = handlers[operation.domain] ?: continue
            val current = pendingDao.find(operation.id) ?: continue
            try {
                val remap = handler.execute(current)
                pendingDao.delete(current.id)
                if (remap != null) remapIds(householdId, remap)
            } catch (exception: HttpException) {
                if (exception.code() in UNRECOVERABLE_STATUSES) {
                    Log.w(
                        TAG,
                        "Dropping ${current.domain}/${current.type} on ${current.targetId}: " +
                            "HTTP ${exception.code()}"
                    )
                    pendingDao.delete(current.id)
                    _dropped.tryEmit(current)
                } else {
                    throw exception
                }
            }
        }
    }

    private suspend fun remapIds(householdId: String, remap: IdRemap) {
        for (operation in pendingDao.pendingForHousehold(householdId)) {
            val targetId = operation.targetId
            val payload = operation.payloadJson
            val touchesTarget = targetId == remap.localId
            val touchesPayload = payload?.contains(remap.localId) == true
            if (!touchesTarget && !touchesPayload) continue
            pendingDao.upsert(
                operation.copy(
                    targetId = if (touchesTarget) remap.remoteId else targetId,
                    payloadJson = payload?.replace(remap.localId, remap.remoteId)
                )
            )
        }
    }

    companion object {
        private const val TAG = "OutboxPusher"
        private val UNRECOVERABLE_STATUSES = setOf(400, 404, 409, 410, 422)
    }
}
