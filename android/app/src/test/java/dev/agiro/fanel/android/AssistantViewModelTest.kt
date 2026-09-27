package dev.agiro.fanel.android

import android.content.Context
import dev.agiro.fanel.android.data.offline.RecipesRepository
import dev.agiro.fanel.android.data.remote.AgentDto
import dev.agiro.fanel.android.data.remote.AgentRequest
import dev.agiro.fanel.android.data.remote.AgentResponse
import dev.agiro.fanel.android.data.remote.AssistantApi
import dev.agiro.fanel.android.data.remote.CreateRecipeRequest
import dev.agiro.fanel.android.data.remote.DelegationDto
import dev.agiro.fanel.android.data.remote.RecipeDto
import dev.agiro.fanel.android.data.remote.RecipesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AssistantViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun extractJsonReadsCodeBlock() {
        val text = "Aquí tens la recepta:\n```json\n{\"name\":\"Canelons\"}\n```"
        assertEquals("{\"name\":\"Canelons\"}", AssistantViewModel.extractJson(text))
    }

    @Test
    fun extractJsonReadsBareObject() {
        val text = "Resposta: {\"name\":\"X\"} i prou"
        assertEquals("{\"name\":\"X\"}", AssistantViewModel.extractJson(text))
    }

    @Test
    fun extractJsonReturnsNullWithoutObject() {
        assertNull(AssistantViewModel.extractJson("Cap JSON aquí"))
    }

    @Test
    fun tryParseRecipeAcceptsValidSuggestion() {
        val json = """{"name":"Canelons","servings":4,"ingredients":[{"name":"carn","quantity":300,"unit":"g"}]}"""
        val recipe = AssistantViewModel.tryParseRecipe(json)
        assertNotNull(recipe)
        assertEquals("Canelons", recipe!!.name)
        assertEquals(1, recipe.ingredients!!.size)
    }

    @Test
    fun tryParseRecipeRejectsErrorFlagAndNonRecipes() {
        assertNull(AssistantViewModel.tryParseRecipe("""{"error":true,"name":"X","ingredients":[]}"""))
        assertNull(AssistantViewModel.tryParseRecipe("""{"foo":"bar"}"""))
        assertNull(AssistantViewModel.tryParseRecipe("Text sense JSON"))
    }

    @Test
    fun wantsCreateDetectsTriggersIgnoringDiacritics() {
        assertTrue(AssistantViewModel.wantsCreate("Crea aquesta recepta"))
        assertTrue(AssistantViewModel.wantsCreate("Afegeix-la al llibre"))
        assertTrue(AssistantViewModel.wantsCreate("guárdala"))
        assertFalse(AssistantViewModel.wantsCreate("Què porten els canelons?"))
    }

    private fun recipesRepository(context: Context, api: RecipesApi): RecipesRepository {
        val store = OfflineTestStore(context)
        return RecipesRepository(api, store.snapshotDao, store.pendingDao, store.pusher)
    }

    @Test
    fun sendPostsMessageAndAppendsReply() = runBlocking {
        val assistantApi = FakeAssistantApi()
        val recipesApi = RecordingRecipesApi()
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, recipesApi), sessionStore)
        val states = mutableListOf<AssistantUiState>()
        val job = launch(UnconfinedTestDispatcher()) { vm.uiState.collect { states.add(it) } }

        vm.send("Hola", null)

        val request = assistantApi.lastRequest
        assertNotNull(request)
        assertNull(request!!.agentId)
        assertEquals("Hola", request.message)
        assertNull(request.memberId)
        val last = states.last().messages.last()
        assertEquals(ChatRole.ASSISTANT, last.role)
        assertEquals("Resposta de prova", last.text)
        assertNotNull(last.dev)
        assertEquals("orchestrator", last.dev!!.agentId)
        assertEquals("conv-1", last.dev!!.conversationId)
        job.cancel()
    }

    @Test
    fun sendParsesRecipeFromDelegation() = runBlocking {
        val assistantApi = FakeAssistantApi().apply {
            response = AgentResponse(
                agentId = "orchestrator",
                conversationId = "conv-1",
                text = "He tret la recepta de la foto.",
                toolCalls = null,
                delegations = listOf(
                    DelegationDto(
                        "recipe-from-image",
                        """{"name":"Truita","servings":2,"ingredients":[{"name":"ous","quantity":4}]}""",
                        120L
                    )
                )
            )
        }
        val recipesApi = RecordingRecipesApi()
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, recipesApi), sessionStore)
        val states = mutableListOf<AssistantUiState>()
        val job = launch(UnconfinedTestDispatcher()) { vm.uiState.collect { states.add(it) } }

        vm.send("Crea aquesta recepta", null)

        val recipeMessage = states.last().messages.first { it.recipe != null }
        assertEquals("Truita", recipeMessage.recipe?.name)
        assertFalse(recipeMessage.hideText)
        assertEquals(listOf("recipe-from-image"), recipeMessage.via)
        assertEquals("Truita", recipesApi.lastCreateRequest?.name)
        job.cancel()
    }

    @Test
    fun sendParsesRecipeFromMainReplyAndHidesJson() = runBlocking {
        val assistantApi = FakeAssistantApi().apply {
            response = AgentResponse(
                agentId = "orchestrator",
                conversationId = "conv-1",
                text = """```json
                    {"name":"Truita","servings":2,"ingredients":[{"name":"ous","quantity":4}]}
                    ```""".trimIndent(),
                toolCalls = null,
                delegations = null
            )
        }
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, RecordingRecipesApi()), sessionStore)
        val states = mutableListOf<AssistantUiState>()
        val job = launch(UnconfinedTestDispatcher()) { vm.uiState.collect { states.add(it) } }

        vm.send("Fes una recepta de truita", null)

        val last = states.last().messages.last()
        assertEquals("Truita", last.recipe?.name)
        assertTrue(last.hideText)
        job.cancel()
    }

    @Test
    fun sendDoesNothingWhenNoAgentsAvailable() = runBlocking {
        val assistantApi = FakeAssistantApi().apply { availableAgents = emptyList() }
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, RecordingRecipesApi()), sessionStore)

        vm.send("Hola", null)

        assertNull(assistantApi.lastRequest)
    }

    @Test
    fun sendPassesSelectedMemberId() = runBlocking {
        val assistantApi = FakeAssistantApi()
        val recipesApi = RecordingRecipesApi()
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply {
            householdId = "household-1"
            memberId = "member-9"
        }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, recipesApi), sessionStore)

        vm.send("Hola", null)

        assertEquals("member-9", assistantApi.lastRequest?.memberId)
    }

    @Test
    fun sendAutoCreatesRecipeWhenAsked() = runBlocking {
        val assistantApi = FakeAssistantApi().apply {
            response = AgentResponse(
                agentId = "orchestrator",
                conversationId = "conv-1",
                text = """```json
                    {"name":"Truita","servings":2,"ingredients":[{"name":"ous","quantity":4}]}
                    ```""".trimIndent(),
                toolCalls = null,
                delegations = null
            )
        }
        val recipesApi = RecordingRecipesApi()
        val context = RuntimeEnvironment.getApplication()
        val sessionStore = SessionStore(context).apply { householdId = "household-1" }
        val vm = AssistantViewModel(context, assistantApi, recipesRepository(context, recipesApi), sessionStore)

        vm.send("Crea aquesta recepta", null)

        assertEquals("Truita", recipesApi.lastCreateRequest?.name)
    }
}

private class FakeAssistantApi : AssistantApi {
    var response = AgentResponse("orchestrator", "conv-1", "Resposta de prova", null, null)
    var lastRequest: AgentRequest? = null
    var availableAgents = listOf(
        AgentDto("orchestrator", "assistant.orchestrator.name", null, false, null, true, "ollama:llama3.1"),
        AgentDto("recipe-from-image", "assistant.recipe-from-image.name", null, true, null, false, null)
    )

    override suspend fun agents(householdId: String): List<AgentDto> = availableAgents

    override suspend fun chat(householdId: String, request: AgentRequest): AgentResponse {
        lastRequest = request
        return response
    }
}

private class RecordingRecipesApi : RecipesApi {
    var lastCreateRequest: CreateRecipeRequest? = null

    override suspend fun list(householdId: String): List<RecipeDto> = emptyList()
    override suspend fun get(householdId: String, recipeId: String): RecipeDto =
        throw NoSuchElementException()

    override suspend fun create(householdId: String, request: CreateRecipeRequest): RecipeDto {
        lastCreateRequest = request
        return RecipeDto(
            "r1", householdId, request.name, request.servings, request.notes,
            request.description, request.steps, request.tags,
            request.imageMimeType, request.imageData, request.ingredients, null
        )
    }

    override suspend fun delete(householdId: String, recipeId: String) = Unit
}
