package dev.agiro.fanel.android.data.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.agiro.fanel.android.data.local.CachedSnapshotDao
import dev.agiro.fanel.android.data.local.CachedSnapshotEntity
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import java.util.UUID

interface OfflineSyncable {
    val domain: String

    /** Pushes the household outbox and refreshes the cached snapshots of this domain. */
    suspend fun sync(householdId: String)
}

/**
 * Base class for offline-first domains backed by JSON snapshots in Room.
 *
 * Reads always come from the local snapshot ([observe]); mutations are applied optimistically to
 * the snapshot and appended to the shared outbox so they can be replayed by [OutboxPusher].
 * Subclasses implement [pull] (remote -> snapshot) and [execute] (outbox entry -> API call).
 */
abstract class SnapshotRepository<T>(
    final override val domain: String,
    private val snapshotDao: CachedSnapshotDao,
    private val pendingDao: PendingOperationDao,
    private val pusher: OutboxPusher,
    protected val gson: Gson,
    private val snapshotType: TypeToken<T>,
    private val clock: () -> Long = System::currentTimeMillis
) : OfflineSyncable, OperationHandler {

    init {
        pusher.register(domain, this)
    }

    fun observe(householdId: String, key: String = DEFAULT_KEY): Flow<T?> =
        snapshotDao.observe(householdId, domain, key).map { it?.let(::decode) }

    fun observePendingCount(householdId: String): Flow<Int> =
        pendingDao.observePendingCount(householdId, domain)

    /** Operations of this domain that the server rejected during the last push. */
    fun observeDropped(): Flow<PendingOperationEntity> = pusher.dropped.filter { it.domain == domain }

    suspend fun cached(householdId: String, key: String = DEFAULT_KEY): T? =
        snapshotDao.find(householdId, domain, key)?.let(::decode)

    override suspend fun sync(householdId: String) {
        pusher.pushAll(householdId)
        pull(householdId)
    }

    protected abstract suspend fun pull(householdId: String)

    protected suspend fun cachedKeys(householdId: String): List<String> =
        snapshotDao.findAll(householdId, domain).map { it.key }

    protected suspend fun write(householdId: String, value: T, key: String = DEFAULT_KEY) {
        snapshotDao.upsert(CachedSnapshotEntity(householdId, domain, key, gson.toJson(value), clock()))
    }

    protected suspend fun evict(householdId: String, key: String) {
        snapshotDao.delete(householdId, domain, key)
    }

    protected suspend fun mutate(householdId: String, key: String = DEFAULT_KEY, empty: () -> T, transform: (T) -> T) {
        val current = cached(householdId, key) ?: empty()
        write(householdId, transform(current), key)
    }

    protected suspend fun <P> enqueue(householdId: String, type: String, targetId: String?, payload: P) {
        enqueueJson(householdId, type, targetId, gson.toJson(payload))
    }

    protected suspend fun enqueue(householdId: String, type: String, targetId: String?) {
        enqueueJson(householdId, type, targetId, null)
    }

    private suspend fun enqueueJson(householdId: String, type: String, targetId: String?, payloadJson: String?) {
        pusher.enqueue(householdId, domain, type, targetId, payloadJson, clock = clock)
    }

    /** Drops every queued operation targeting [targetId]; returns true when a local create was among them. */
    protected suspend fun discardPending(householdId: String, targetId: String): Boolean {
        val local = isLocalId(targetId)
        pusher.discard(householdId, domain, targetId)
        return local
    }

    protected inline fun <reified P> payload(operation: PendingOperationEntity): P =
        gson.fromJson(operation.payloadJson, P::class.java)

    private fun decode(entity: CachedSnapshotEntity): T = gson.fromJson(entity.json, snapshotType.type)

    companion object {
        const val DEFAULT_KEY = "all"
        private const val LOCAL_PREFIX = "local-"

        fun newLocalId(): String = LOCAL_PREFIX + UUID.randomUUID()
        fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_PREFIX)
    }
}
