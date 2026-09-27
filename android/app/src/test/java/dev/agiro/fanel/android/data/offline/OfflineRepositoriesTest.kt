package dev.agiro.fanel.android.data.offline

import dev.agiro.fanel.android.FakeHouseholdApi
import dev.agiro.fanel.android.OfflineTestStore
import dev.agiro.fanel.android.data.remote.AddItemRequest
import dev.agiro.fanel.android.data.remote.CreateRecipeRequest
import dev.agiro.fanel.android.data.remote.ListNameRequest
import dev.agiro.fanel.android.data.remote.MemberDto
import dev.agiro.fanel.android.data.remote.RecipeDto
import dev.agiro.fanel.android.data.remote.RecipesApi
import dev.agiro.fanel.android.data.remote.ShoppingApi
import dev.agiro.fanel.android.data.remote.ShoppingItemDto
import dev.agiro.fanel.android.data.remote.ShoppingListDto
import dev.agiro.fanel.android.data.remote.UpdateItemRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
class OfflineRepositoriesTest {
    private val householdId = "household-1"

    private fun store() = OfflineTestStore(RuntimeEnvironment.getApplication())

    @Test
    fun membersAreServedFromCacheWhenOffline() = runBlocking {
        val store = store()
        val api = FakeHouseholdApi(listOf(member("m1", "Albert")))
        val repository = MembersRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        repository.sync(householdId)
        api.failMembers = true

        assertEquals(listOf("Albert"), repository.members(householdId).map { it.name })
        assertEquals(listOf("Albert"), repository.observeMembers(householdId).first().map { it.name })
    }

    @Test
    fun recipeCreatedOfflineIsVisibleAndPushedLater() = runBlocking {
        val store = store()
        val api = FakeRecipesApi().apply { offline = true }
        val repository = RecipesRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        val localId = repository.create(householdId, recipeRequest("Pa amb tomàquet"))
        runCatching { repository.sync(householdId) }

        assertTrue(SnapshotRepository.isLocalId(localId))
        assertEquals(listOf(localId), repository.observeRecipes(householdId).first().map { it.id })
        assertEquals(1, repository.observePendingCount(householdId).first())

        api.offline = false
        repository.sync(householdId)

        assertEquals(listOf("Pa amb tomàquet"), api.recipes.map { it.name })
        assertEquals(0, repository.observePendingCount(householdId).first())
        assertEquals(api.recipes.map { it.id }, repository.observeRecipes(householdId).first().map { it.id })
    }

    @Test
    fun deletingLocallyCreatedRecipeDropsQueuedCreate() = runBlocking {
        val store = store()
        val api = FakeRecipesApi().apply { offline = true }
        val repository = RecipesRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        val localId = repository.create(householdId, recipeRequest("Esborrany"))
        repository.delete(householdId, localId)
        api.offline = false
        repository.sync(householdId)

        assertTrue(api.recipes.isEmpty())
        assertTrue(api.deleted.isEmpty())
        assertEquals(0, repository.observePendingCount(householdId).first())
    }

    @Test
    fun shoppingItemAddedToOfflineListIsRemappedToServerListId() = runBlocking {
        val store = store()
        val api = FakeShoppingApi().apply { offline = true }
        val repository = ShoppingRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        val localListId = repository.createList(householdId, "Setmanal")
        repository.addItem(householdId, localListId, AddItemRequest("Ous", 12.0, "u", null, false))
        val cached = repository.observeLists(householdId).first()
        assertEquals(listOf("Ous"), cached.single().items.orEmpty().map { it.name })

        api.offline = false
        repository.sync(householdId)

        val remoteList = api.lists.single()
        assertFalse(SnapshotRepository.isLocalId(remoteList.id))
        assertEquals(remoteList.id, api.lastAddListId)
        assertEquals(listOf("Ous"), remoteList.items.orEmpty().map { it.name })
        assertEquals(remoteList.id, repository.observeLists(householdId).first().single().id)
        assertEquals(0, repository.observePendingCount(householdId).first())
    }

    @Test
    fun rejectedCreateLeavesLocalListVisibleAfterPull() = runBlocking {
        val store = store()
        val api = FakeShoppingApi().apply { failWithStatus = 400 }
        val repository = ShoppingRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        val localId = repository.createList(householdId, "Setmanal")
        repository.sync(householdId)

        assertTrue(api.lists.isEmpty())
        assertEquals(0, repository.observePendingCount(householdId).first())
        val lists = repository.observeLists(householdId).first()
        assertEquals(listOf(localId), lists.map { it.id })
        assertEquals(listOf("Setmanal"), lists.map { it.name })
    }

    @Test
    fun pusherDropsUnrecoverableOperationsButKeepsTransientOnes() = runBlocking {
        val store = store()
        val api = FakeRecipesApi()
        val repository = RecipesRepository(api, store.snapshotDao, store.pendingDao, store.pusher)

        api.failWithStatus = 404
        repository.delete(householdId, "gone")
        repository.sync(householdId)
        assertEquals(0, repository.observePendingCount(householdId).first())

        api.failWithStatus = 503
        repository.delete(householdId, "later")
        val result = runCatching { repository.sync(householdId) }
        assertTrue(result.exceptionOrNull() is HttpException)
        assertEquals(1, repository.observePendingCount(householdId).first())
    }

    private fun member(id: String, name: String) =
        MemberDto(id, householdId, name, null, null, null, null, false, false)

    private fun recipeRequest(name: String) =
        CreateRecipeRequest(name, 2, null, null, emptyList(), emptyList(), emptyList(), null, null)
}

private fun httpError(status: Int): HttpException =
    HttpException(Response.error<Unit>(status, "".toResponseBody("text/plain".toMediaType())))

private class FakeRecipesApi : RecipesApi {
    val recipes = mutableListOf<RecipeDto>()
    val deleted = mutableListOf<String>()
    var offline = false
    var failWithStatus: Int? = null

    private fun gate() {
        if (offline) throw IOException("offline")
    }

    private fun mutationGate() {
        gate()
        failWithStatus?.let { throw httpError(it) }
    }

    override suspend fun list(householdId: String): List<RecipeDto> {
        gate()
        return recipes.toList()
    }

    override suspend fun get(householdId: String, recipeId: String): RecipeDto = recipes.first { it.id == recipeId }

    override suspend fun create(householdId: String, request: CreateRecipeRequest): RecipeDto {
        mutationGate()
        val created = RecipeDto(
            "r-${recipes.size + 1}", householdId, request.name, request.servings, request.notes,
            request.description, request.steps, request.tags, request.imageMimeType, request.imageData,
            request.ingredients, null
        )
        recipes.add(created)
        return created
    }

    override suspend fun delete(householdId: String, recipeId: String) {
        mutationGate()
        deleted.add(recipeId)
        recipes.removeAll { it.id == recipeId }
    }
}

private class FakeShoppingApi : ShoppingApi {
    val lists = mutableListOf<ShoppingListDto>()
    var offline = false
    var failWithStatus: Int? = null
    var lastAddListId: String? = null

    private fun gate() {
        if (offline) throw IOException("offline")
    }

    private fun mutationGate() {
        gate()
        failWithStatus?.let { throw httpError(it) }
    }

    override suspend fun listLists(householdId: String): List<ShoppingListDto> {
        gate()
        return lists.toList()
    }

    override suspend fun createList(householdId: String, request: ListNameRequest): ShoppingListDto {
        mutationGate()
        val created = ShoppingListDto("l-${lists.size + 1}", householdId, request.name, emptyList())
        lists.add(created)
        return created
    }

    override suspend fun getList(householdId: String, listId: String): ShoppingListDto = lists.first { it.id == listId }

    override suspend fun updateList(householdId: String, listId: String, request: ListNameRequest): ShoppingListDto {
        gate()
        val updated = lists.first { it.id == listId }.copy(name = request.name)
        lists.replaceAll { if (it.id == listId) updated else it }
        return updated
    }

    override suspend fun deleteList(householdId: String, listId: String) {
        gate()
        lists.removeAll { it.id == listId }
    }

    override suspend fun addItem(householdId: String, listId: String, request: AddItemRequest): ShoppingItemDto {
        gate()
        lastAddListId = listId
        val list = lists.first { it.id == listId }
        val created = ShoppingItemDto(
            "i-${list.items.orEmpty().size + 1}", request.name, request.quantity, request.unit,
            request.category, request.recurring, false
        )
        lists.replaceAll { if (it.id == listId) it.copy(items = it.items.orEmpty() + created) else it }
        return created
    }

    override suspend fun updateItem(householdId: String, itemId: String, request: UpdateItemRequest): ShoppingItemDto {
        gate()
        return lists.flatMap { it.items.orEmpty() }.first { it.id == itemId }
    }

    override suspend fun removeItem(householdId: String, itemId: String) {
        gate()
        lists.replaceAll { list -> list.copy(items = list.items.orEmpty().filterNot { it.id == itemId }) }
    }

    override suspend fun clearPurchased(householdId: String, listId: String): Map<String, Int> {
        gate()
        return mapOf("removed" to 0)
    }
}
