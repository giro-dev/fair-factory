package dev.agiro.fanel.android.data.remote

data class Attachment(
    val mimeType: String,
    val data: String
)

data class AgentDto(
    val id: String,
    val nameKey: String,
    val descriptionKey: String?,
    val supportsMedia: Boolean,
    val toolNames: List<String>?,
    val orchestrator: Boolean?,
    val model: String?
)

data class AgentRequest(
    val agentId: String?,
    val conversationId: String,
    val message: String,
    val attachments: List<Attachment>,
    val memberId: String?
)

/** A task the orchestrator delegated to a subagent, with its raw response and latency. */
data class DelegationDto(
    val agentId: String,
    val text: String,
    val latencyMs: Long?
)

data class AgentResponse(
    val agentId: String,
    val conversationId: String,
    val text: String,
    val toolCalls: List<String>?,
    val delegations: List<DelegationDto>?
)

/** Recipe JSON the recipe agent can embed in its answer; mirrors the web client's suggestion type. */
data class RecipeSuggestion(
    val error: Boolean?,
    val name: String?,
    val servings: Int?,
    val notes: String?,
    val description: String?,
    val steps: List<String>?,
    val imageMimeType: String?,
    val imageData: String?,
    val tags: List<String>?,
    val ingredients: List<IngredientDto>?
)
