package dev.agiro.fanel.android

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dev.agiro.fanel.android.data.offline.RecipesRepository
import dev.agiro.fanel.android.data.remote.AgentDto
import dev.agiro.fanel.android.data.remote.AgentRequest
import dev.agiro.fanel.android.data.remote.AssistantApi
import dev.agiro.fanel.android.data.remote.Attachment
import dev.agiro.fanel.android.data.remote.CreateRecipeRequest
import dev.agiro.fanel.android.data.remote.DelegationDto
import dev.agiro.fanel.android.data.remote.IngredientDto
import dev.agiro.fanel.android.data.remote.RecipeSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.UUID

enum class ChatRole { USER, ASSISTANT }

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val attachments: List<Attachment> = emptyList(),
    val recipe: RecipeSuggestion? = null,
    /** Ids of the subagents the orchestrator delegated to, for the "via …" caption. */
    val via: List<String> = emptyList(),
    /** The text is raw structured content (recipe JSON): the recipe card renders it instead. */
    val hideText: Boolean = false,
    val dev: DevStats? = null,
    val error: Boolean = false
)

data class DevStats(
    val agentId: String,
    val latencyMs: Long,
    val conversationId: String,
    val delegations: List<DelegationDto>
)

class AssistantViewModel(
    application: Application,
    private val assistantApi: AssistantApi = (application as FanelApplication).appContainer.assistantApi,
    private val recipesRepository: RecipesRepository = (application as FanelApplication).appContainer.recipesRepository,
    private val sessionStore: SessionStore = (application as FanelApplication).appContainer.sessionStore
) : AndroidViewModel(application) {
    private val householdId = MutableStateFlow("")
    private val _agents = MutableStateFlow<List<AgentDto>>(emptyList())
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _pending = MutableStateFlow(false)
    private val _errorRes = MutableStateFlow<Int?>(null)
    private var conversationId: String = UUID.randomUUID().toString()
    private var pendingImage: Attachment? = null

    val uiState: StateFlow<AssistantUiState> = combine(
        householdId, _agents, _messages, _pending, _errorRes
    ) { household, agents, messages, pending, errorRes ->
        AssistantUiState(
            householdId = household,
            agents = agents,
            messages = messages,
            pending = pending,
            errorRes = errorRes
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AssistantUiState.empty())

    init {
        activateHousehold(sessionStore.householdId)
    }

    fun activateHousehold(newHouseholdId: String) {
        if (householdId.value == newHouseholdId) return
        householdId.value = newHouseholdId
        if (newHouseholdId.isBlank()) return
        viewModelScope.launch {
            runCatching { assistantApi.agents(newHouseholdId) }
                .onSuccess { agents ->
                    _agents.value = agents
                    _errorRes.value = null
                }
                .onFailure { _errorRes.value = R.string.assistant_agents_error }
        }
    }

    fun send(text: String, attachment: Attachment?) {
        val currentHouseholdId = householdId.value
        val message = text.trim()
        if (currentHouseholdId.isBlank() || _agents.value.isEmpty()) return
        if (message.isEmpty() && attachment == null) return

        val attachments = listOfNotNull(attachment)
        if (attachment != null && attachment.mimeType.startsWith("image/")) {
            pendingImage = attachment
        }
        val shownText = message.ifEmpty {
            getApplication<Application>().getString(R.string.assistant_image_attached)
        }
        _messages.update { it + ChatMessage(ChatRole.USER, shownText, attachments) }
        _pending.value = true

        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            runCatching {
                assistantApi.chat(
                    currentHouseholdId,
                    AgentRequest(
                        null, conversationId, message, attachments,
                        sessionStore.memberId.ifBlank { null }
                    )
                )
            }
                .onSuccess { response ->
                    val latencyMs = System.currentTimeMillis() - startedAt
                    conversationId = response.conversationId
                    val delegations = response.delegations.orEmpty()
                    // Prefer the structured recipe from the recipe-from-image delegation; a recipe
                    // parsed straight from the reply text hides it (the card renders it instead).
                    var recipe = delegations
                        .firstOrNull { it.agentId == RECIPE_AGENT_ID }
                        ?.let { tryParseRecipe(it.text) }
                    val hideText = if (recipe == null) {
                        recipe = tryParseRecipe(response.text)
                        recipe != null
                    } else false
                    val recipeWithImage = if (recipe != null && recipe.imageData == null) {
                        pendingImage?.let { recipe.copy(imageMimeType = it.mimeType, imageData = it.data) }
                            ?: recipe
                    } else recipe
                    _messages.update {
                        it + ChatMessage(
                            role = ChatRole.ASSISTANT,
                            text = response.text,
                            recipe = recipeWithImage,
                            via = delegations.map { d -> d.agentId },
                            hideText = hideText,
                            dev = DevStats(response.agentId, latencyMs, response.conversationId, delegations)
                        )
                    }
                    if (recipeWithImage != null && wantsCreate(message)) {
                        createRecipe(recipeWithImage)
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "Assistant chat request failed", e)
                    _messages.update {
                        it + ChatMessage(
                            ChatRole.ASSISTANT,
                            getApplication<Application>().getString(R.string.assistant_chat_error),
                            error = true
                        )
                    }
                }
            _pending.value = false
        }
    }

    fun createRecipe(recipe: RecipeSuggestion) {
        val currentHouseholdId = householdId.value
        val name = recipe.name
        if (currentHouseholdId.isBlank() || name.isNullOrBlank()) return
        viewModelScope.launch {
            runCatching {
                recipesRepository.create(
                    currentHouseholdId,
                    CreateRecipeRequest(
                        name = name,
                        servings = recipe.servings ?: 4,
                        notes = recipe.notes,
                        description = recipe.description,
                        steps = recipe.steps.orEmpty(),
                        tags = recipe.tags.orEmpty(),
                        ingredients = recipe.ingredients.orEmpty().map {
                            IngredientDto(null, it.name, it.quantity, it.unit, it.category)
                        },
                        imageMimeType = recipe.imageMimeType,
                        imageData = recipe.imageData
                    )
                )
                runCatching { recipesRepository.sync(currentHouseholdId) }
            }
                .onSuccess {
                    pendingImage = null
                    _messages.update {
                        it + ChatMessage(
                            ChatRole.ASSISTANT,
                            getApplication<Application>().getString(R.string.assistant_recipe_created)
                        )
                    }
                }
                .onFailure { e ->
                    Log.w(TAG, "Create recipe from assistant failed", e)
                    _messages.update {
                        it + ChatMessage(
                            ChatRole.ASSISTANT,
                            getApplication<Application>().getString(R.string.assistant_chat_error),
                            error = true
                        )
                    }
                }
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AssistantViewModel(application) as T
    }

    companion object {
        private const val TAG = "AssistantViewModel"
        private const val RECIPE_AGENT_ID = "recipe-from-image"
        private val gson = Gson()

        private val CREATE_TRIGGERS = listOf(
            "crea", "crear", "crei", "creï", "afegeix", "guarda", "desa", "fes-la",
            "añadir", "agrega", "guardar", "salvar", "hazla"
        )

        fun wantsCreate(text: String): Boolean {
            val normalized = normalize(text)
            return CREATE_TRIGGERS.any { normalized.contains(it) }
        }

        fun tryParseRecipe(text: String): RecipeSuggestion? {
            val json = extractJson(text) ?: return null
            return try {
                val parsed = gson.fromJson(json, RecipeSuggestion::class.java)
                if (parsed != null && parsed.error != true &&
                    parsed.name != null && parsed.ingredients != null
                ) parsed else null
            } catch (_: Exception) {
                null
            }
        }

        fun extractJson(text: String): String? {
            val trimmed = text.trim()
            val codeBlock = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```").find(trimmed)
            if (codeBlock != null) return codeBlock.groupValues[1].trim()
            val first = trimmed.indexOf('{')
            val last = trimmed.lastIndexOf('}')
            return if (first >= 0 && last > first) trimmed.substring(first, last + 1) else null
        }

        private fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
    }
}

data class AssistantUiState(
    val householdId: String,
    val agents: List<AgentDto>,
    val messages: List<ChatMessage>,
    val pending: Boolean,
    val errorRes: Int?
) {
    val canAttachMedia: Boolean
        get() = agents.any { it.supportsMedia }

    companion object {
        fun empty(): AssistantUiState = AssistantUiState(
            householdId = "",
            agents = emptyList(),
            messages = emptyList(),
            pending = false,
            errorRes = null
        )
    }
}
