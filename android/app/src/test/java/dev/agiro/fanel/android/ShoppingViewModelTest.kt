package dev.agiro.fanel.android

import dev.agiro.fanel.android.data.offline.ShoppingRepository
import dev.agiro.fanel.android.data.remote.AddItemRequest
import dev.agiro.fanel.android.data.remote.ListNameRequest
import dev.agiro.fanel.android.data.remote.ShoppingApi
import dev.agiro.fanel.android.data.remote.ShoppingItemDto
import dev.agiro.fanel.android.data.remote.ShoppingListDto
import dev.agiro.fanel.android.data.remote.UpdateItemRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ShoppingViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(api: FakeShoppingApi): Pair<ShoppingViewModel, MutableList<ShoppingUiState>> {
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val store = OfflineTestStore(context)
        val vm = ShoppingViewModel(
            application = context,
            repository = ShoppingRepository(api, store.snapshotDao, store.pendingDao, store.pusher),
            sessionStore = sessionStore,
            householdEvents = EmptyHouseholdEvents,
            syncScheduler = FakeSyncScheduler()
        )
        return vm to mutableListOf()
    }

    @Test
    fun showsEmptyStateWhenNoListsExist() = runBlocking {
        val api = FakeShoppingApi()
        val (vm, states) = viewModel(api)
        val job = launch(UnconfinedTestDispatcher()) { vm.uiState.collect { states.add(it) } }

        assertEquals(emptyList<ShoppingListDto>(), states.last().lists)
        assertEquals(null, states.last().activeList)
        job.cancel()
    }

    @Test
    fun groupsItemsByCategoryWithUncategorizedLast() = runBlocking {
        val api = FakeShoppingApi().apply {
            lists.add(
                ShoppingListDto(
                    id = "l1", householdId = "household-1", name = "Setmanal",
                    items = listOf(
                        item("1", "Pomes", category = "Fruita"),
                        item("2", "Detergent", category = null),
                        item("3", "Llet", category = "Lactis")
                    )
                )
            )
        }
        val (vm, states) = viewModel(api)
        val job = launch(UnconfinedTestDispatcher()) { vm.uiState.collect { states.add(it) } }

        val groups = states.last().groups
        assertEquals(listOf("Fruita", "Lactis"), groups.dropLast(1).map { it.name })
        assertEquals(3, groups.flatMap { it.items }.size)
        job.cancel()
    }

    @Test
    fun toggleDonePatchesItem() = runBlocking {
        val api = FakeShoppingApi().apply {
            lists.add(
                ShoppingListDto(
                    id = "l1", householdId = "household-1", name = "Setmanal",
                    items = listOf(item("1", "Pomes", done = false))
                )
            )
        }
        val (vm, _) = viewModel(api)

        vm.toggleDone(api.lists[0].items!!.first())

        assertEquals("1", api.lastUpdateItemId)
        assertEquals(true, api.lastUpdateRequest?.done)
    }

    @Test
    fun addItemPostsToActiveList() = runBlocking {
        val api = FakeShoppingApi().apply {
            lists.add(ShoppingListDto("l1", "household-1", "Setmanal", emptyList()))
        }
        val (vm, _) = viewModel(api)

        vm.addItem("l1", AddItemRequest("Ous", 12.0, "unitats", "Lactis", true))

        assertEquals("l1", api.lastAddListId)
        assertEquals("Ous", api.lastAddRequest?.name)
        assertEquals(true, api.lastAddRequest?.recurring)
        assertNotNull(api.lists[0].items!!.firstOrNull { it.name == "Ous" })
    }

    @Test
    fun clearPurchasedCallsEndpoint() = runBlocking {
        val api = FakeShoppingApi().apply {
            lists.add(
                ShoppingListDto(
                    id = "l1", householdId = "household-1", name = "Setmanal",
                    items = listOf(item("1", "Pomes", done = true), item("2", "Llet", done = false))
                )
            )
        }
        val (vm, _) = viewModel(api)

        vm.clearPurchased("l1")

        assertEquals("l1", api.lastClearListId)
        assertEquals(1, api.lists[0].items!!.size)
    }

    private fun item(
        id: String,
        name: String,
        category: String? = null,
        done: Boolean = false
    ) = ShoppingItemDto(id, name, null, null, category, false, done)
}

private class FakeShoppingApi : ShoppingApi {
    var lists: MutableList<ShoppingListDto> = mutableListOf()
    var lastAddListId: String? = null
    var lastAddRequest: AddItemRequest? = null
    var lastUpdateItemId: String? = null
    var lastUpdateRequest: UpdateItemRequest? = null
    var lastClearListId: String? = null

    override suspend fun listLists(householdId: String): List<ShoppingListDto> = lists.toList()

    override suspend fun createList(householdId: String, request: ListNameRequest): ShoppingListDto {
        val created = ShoppingListDto("l${lists.size + 1}", householdId, request.name, emptyList())
        lists.add(created)
        return created
    }

    override suspend fun getList(householdId: String, listId: String): ShoppingListDto =
        lists.first { it.id == listId }

    override suspend fun updateList(
        householdId: String,
        listId: String,
        request: ListNameRequest
    ): ShoppingListDto {
        val list = lists.first { it.id == listId }.copy(name = request.name)
        lists.replaceAll { if (it.id == listId) list else it }
        return list
    }

    override suspend fun deleteList(householdId: String, listId: String) {
        lists.removeAll { it.id == listId }
    }

    override suspend fun addItem(
        householdId: String,
        listId: String,
        request: AddItemRequest
    ): ShoppingItemDto {
        lastAddListId = listId
        lastAddRequest = request
        val created = ShoppingItemDto(
            "i-${listId}-${lists.size}", request.name, request.quantity,
            request.unit, request.category, request.recurring, false
        )
        lists.replaceAll {
            if (it.id == listId) it.copy(items = it.items.orEmpty() + created) else it
        }
        return created
    }

    override suspend fun updateItem(
        householdId: String,
        itemId: String,
        request: UpdateItemRequest
    ): ShoppingItemDto {
        lastUpdateItemId = itemId
        lastUpdateRequest = request
        lists.replaceAll { list ->
            list.copy(
                items = list.items.orEmpty().map { item ->
                    if (item.id == itemId) {
                        item.copy(
                            done = request.done ?: item.done,
                            recurring = request.recurring ?: item.recurring
                        )
                    } else item
                }
            )
        }
        return lists.flatMap { it.items.orEmpty() }.first { it.id == itemId }
    }

    override suspend fun removeItem(householdId: String, itemId: String) {
        lists.replaceAll { list ->
            list.copy(items = list.items.orEmpty().filter { it.id != itemId })
        }
    }

    override suspend fun clearPurchased(householdId: String, listId: String): Map<String, Int> {
        lastClearListId = listId
        var removed = 0
        lists.replaceAll { list ->
            if (list.id == listId) {
                val kept = list.items.orEmpty().filter { !it.done }
                removed = list.items.orEmpty().size - kept.size
                list.copy(items = kept)
            } else list
        }
        return mapOf("removed" to removed)
    }
}
