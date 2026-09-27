package dev.agiro.fanel.android.data.offline

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.agiro.fanel.android.data.local.CachedSnapshotDao
import dev.agiro.fanel.android.data.local.PendingOperationDao
import dev.agiro.fanel.android.data.local.PendingOperationEntity
import dev.agiro.fanel.android.data.remote.CreateRecipeRequest
import dev.agiro.fanel.android.data.remote.RecipeDto
import dev.agiro.fanel.android.data.remote.RecipesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RecipesRepository(
    private val api: RecipesApi,
    snapshotDao: CachedSnapshotDao,
    pendingDao: PendingOperationDao,
    pusher: OutboxPusher,
    gson: Gson = Gson()
) : SnapshotRepository<List<RecipeDto>>(
    DOMAIN, snapshotDao, pendingDao, pusher, gson, object : TypeToken<List<RecipeDto>>() {}
) {

    fun observeRecipes(householdId: String): Flow<List<RecipeDto>> =
        observe(householdId).map { it.orEmpty() }

    /** Adds the recipe locally with a temporary id and queues the remote create. Returns the local id. */
    suspend fun create(householdId: String, request: CreateRecipeRequest): String {
        val localId = newLocalId()
        val recipe = RecipeDto(
            id = localId,
            householdId = householdId,
            name = request.name,
            servings = request.servings,
            notes = request.notes,
            description = request.description,
            steps = request.steps,
            tags = request.tags,
            imageMimeType = request.imageMimeType,
            imageData = request.imageData,
            ingredients = request.ingredients,
            createdAt = null
        )
        mutate(householdId, empty = ::emptyList) { it + recipe }
        enqueue(householdId, OP_CREATE, localId, request)
        return localId
    }

    suspend fun delete(householdId: String, recipeId: String) {
        mutate(householdId, empty = ::emptyList) { recipes -> recipes.filterNot { it.id == recipeId } }
        if (!discardPending(householdId, recipeId)) enqueue(householdId, OP_DELETE, recipeId)
    }

    override suspend fun pull(householdId: String) {
        val remote = api.list(householdId)
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val unsynced = cached(householdId).orEmpty().filter { isLocalId(it.id) && it.id !in remoteIds }
        write(householdId, remote + unsynced)
    }

    override suspend fun execute(operation: PendingOperationEntity): IdRemap? = when (operation.type) {
        OP_CREATE -> {
            val created = api.create(operation.householdId, payload<CreateRecipeRequest>(operation))
            val localId = operation.targetId ?: return null
            mutate(operation.householdId, empty = ::emptyList) { recipes ->
                recipes.map { if (it.id == localId) created else it }
            }
            IdRemap(localId, created.id)
        }
        OP_DELETE -> {
            api.delete(operation.householdId, requireNotNull(operation.targetId))
            null
        }
        else -> null
    }

    companion object {
        const val DOMAIN = "recipes"
        const val OP_CREATE = "CREATE"
        const val OP_DELETE = "DELETE"
    }
}
