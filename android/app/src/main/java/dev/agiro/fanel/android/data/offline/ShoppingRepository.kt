package dev.agiro.fanel.android.data.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.agiro.fanel.android.data.local.CachedSnapshotDao
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import dev.agiro.fanel.android.data.remote.AddItemRequest
import dev.agiro.fanel.android.data.remote.ListNameRequest
import dev.agiro.fanel.android.data.remote.ShoppingApi
import dev.agiro.fanel.android.data.remote.ShoppingItemDto
import dev.agiro.fanel.android.data.remote.ShoppingListDto
import dev.agiro.fanel.android.data.remote.UpdateItemRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ShoppingRepository(
    private val api: ShoppingApi,
    snapshotDao: CachedSnapshotDao,
    pendingDao: PendingOperationDao,
    pusher: OutboxPusher,
    gson: Gson = Gson()
) : SnapshotRepository<List<ShoppingListDto>>(
    DOMAIN, snapshotDao, pendingDao, pusher, gson, object : TypeToken<List<ShoppingListDto>>() {}
) {

    data class AddItemOperation(val listId: String, val request: AddItemRequest)

    fun observeLists(householdId: String): Flow<List<ShoppingListDto>> =
        observe(householdId).map { it.orEmpty() }

    suspend fun createList(householdId: String, name: String): String {
        require(name.isNotBlank()) { "List name must not be blank" }
        val localId = newLocalId()
        mutate(householdId, empty = ::emptyList) { it + ShoppingListDto(localId, householdId, name, emptyList()) }
        enqueue(householdId, OP_CREATE_LIST, localId, ListNameRequest(name))
        return localId
    }

    suspend fun renameList(householdId: String, listId: String, name: String) {
        require(name.isNotBlank()) { "List name must not be blank" }
        mutate(householdId, empty = ::emptyList) { lists ->
            lists.map { if (it.id == listId) it.copy(name = name) else it }
        }
        enqueue(householdId, OP_RENAME_LIST, listId, ListNameRequest(name))
    }

    suspend fun deleteList(householdId: String, listId: String) {
        mutate(householdId, empty = ::emptyList) { lists -> lists.filterNot { it.id == listId } }
        if (!discardPending(householdId, listId)) enqueue(householdId, OP_DELETE_LIST, listId)
    }

    suspend fun addItem(householdId: String, listId: String, request: AddItemRequest): String {
        val localId = newLocalId()
        val item = ShoppingItemDto(
            id = localId,
            name = request.name,
            quantity = request.quantity,
            unit = request.unit,
            category = request.category,
            recurring = request.recurring,
            done = false
        )
        updateList(householdId, listId) { it.copy(items = it.items.orEmpty() + item) }
        enqueue(householdId, OP_ADD_ITEM, localId, AddItemOperation(listId, request))
        return localId
    }

    suspend fun updateItem(householdId: String, itemId: String, request: UpdateItemRequest) {
        mutate(householdId, empty = ::emptyList) { lists ->
            lists.map { list ->
                list.copy(
                    items = list.items?.map { item ->
                        if (item.id != itemId) item else item.copy(
                            name = request.name ?: item.name,
                            quantity = request.quantity ?: item.quantity,
                            unit = request.unit ?: item.unit,
                            category = request.category ?: item.category,
                            recurring = request.recurring ?: item.recurring,
                            done = request.done ?: item.done
                        )
                    }
                )
            }
        }
        enqueue(householdId, OP_UPDATE_ITEM, itemId, request)
    }

    suspend fun removeItem(householdId: String, itemId: String) {
        mutate(householdId, empty = ::emptyList) { lists ->
            lists.map { list -> list.copy(items = list.items?.filterNot { it.id == itemId }) }
        }
        if (!discardPending(householdId, itemId)) enqueue(householdId, OP_REMOVE_ITEM, itemId)
    }

    suspend fun clearPurchased(householdId: String, listId: String) {
        updateList(householdId, listId) { list -> list.copy(items = list.items?.filterNot { it.done }) }
        enqueue(householdId, OP_CLEAR_PURCHASED, listId)
    }

    /**
     * Replaces the snapshot with the server state, preserving locally created lists and items
     * (ids with the local- prefix) that have no remote counterpart so unsynced work is never
     * wiped by a pull.
     */
    override suspend fun pull(householdId: String) {
        val remote = api.listLists(householdId)
        val local = cached(householdId).orEmpty()
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val merged = remote.map { list ->
            val unsyncedItems = local.firstOrNull { it.id == list.id }?.items.orEmpty()
                .filter { isLocalId(it.id) }
            if (unsyncedItems.isEmpty()) list else list.copy(items = list.items.orEmpty() + unsyncedItems)
        } + local.filter { isLocalId(it.id) && it.id !in remoteIds }
        write(householdId, merged)
    }

    override suspend fun execute(operation: PendingOperationEntity): IdRemap? {
        val householdId = operation.householdId
        val targetId = operation.targetId
        return when (operation.type) {
            OP_CREATE_LIST -> {
                val created = api.createList(householdId, payload<ListNameRequest>(operation))
                if (targetId == null) return null
                updateList(householdId, targetId) { created.copy(items = it.items) }
                IdRemap(targetId, created.id)
            }
            OP_RENAME_LIST -> {
                api.updateList(householdId, requireNotNull(targetId), payload<ListNameRequest>(operation))
                null
            }
            OP_DELETE_LIST -> {
                api.deleteList(householdId, requireNotNull(targetId))
                null
            }
            OP_ADD_ITEM -> {
                val add = payload<AddItemOperation>(operation)
                val created = api.addItem(householdId, add.listId, add.request)
                if (targetId == null) return null
                replaceItem(householdId, targetId, created)
                IdRemap(targetId, created.id)
            }
            OP_UPDATE_ITEM -> {
                val id = requireNotNull(targetId)
                replaceItem(householdId, id, api.updateItem(householdId, id, payload<UpdateItemRequest>(operation)))
                null
            }
            OP_REMOVE_ITEM -> {
                api.removeItem(householdId, requireNotNull(targetId))
                null
            }
            OP_CLEAR_PURCHASED -> {
                api.clearPurchased(householdId, requireNotNull(targetId))
                null
            }
            else -> null
        }
    }

    private suspend fun updateList(householdId: String, listId: String, transform: (ShoppingListDto) -> ShoppingListDto) {
        mutate(householdId, empty = ::emptyList) { lists -> lists.map { if (it.id == listId) transform(it) else it } }
    }

    private suspend fun replaceItem(householdId: String, itemId: String, item: ShoppingItemDto) {
        mutate(householdId, empty = ::emptyList) { lists ->
            lists.map { list -> list.copy(items = list.items?.map { if (it.id == itemId) item else it }) }
        }
    }

    companion object {
        const val DOMAIN = "shopping"
        const val OP_CREATE_LIST = "CREATE_LIST"
        const val OP_RENAME_LIST = "RENAME_LIST"
        const val OP_DELETE_LIST = "DELETE_LIST"
        const val OP_ADD_ITEM = "ADD_ITEM"
        const val OP_UPDATE_ITEM = "UPDATE_ITEM"
        const val OP_REMOVE_ITEM = "REMOVE_ITEM"
        const val OP_CLEAR_PURCHASED = "CLEAR_PURCHASED"
    }
}
