package dev.agiro.fanel.android.ui.assistant

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.m3.Markdown
import dev.agiro.fanel.android.AssistantViewModel
import dev.agiro.fanel.android.ChatMessage
import dev.agiro.fanel.android.ChatRole
import dev.agiro.fanel.android.DevStats
import dev.agiro.fanel.android.FanelApplication
import dev.agiro.fanel.android.R
import dev.agiro.fanel.android.data.remote.AgentDto
import dev.agiro.fanel.android.data.remote.Attachment
import dev.agiro.fanel.android.data.remote.DelegationDto
import dev.agiro.fanel.android.data.remote.RecipeSuggestion
import dev.agiro.fanel.android.ui.components.OfflineBanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    onOpenSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val sessionStore = remember {
        (context.applicationContext as FanelApplication).appContainer.sessionStore
    }
    var devMode by remember { mutableStateOf(sessionStore.assistantDevMode) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                val mimeType = context.contentResolver.getType(uri) ?: "image/*"
                if (bytes != null) {
                    attachment = Attachment(mimeType, Base64.encodeToString(bytes, Base64.DEFAULT))
                }
            }
        }
    }

    val send: () -> Unit = {
        if (input.isNotBlank() || attachment != null) {
            viewModel.send(input, attachment)
            input = ""
            attachment = null
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_screen_title)) },
                actions = {
                    TextButton(
                        onClick = {
                            devMode = !devMode
                            sessionStore.assistantDevMode = devMode
                        }
                    ) {
                        Text(
                            "DEV",
                            color = if (devMode) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OfflineBanner()
            state.errorRes?.let { errorRes ->
                Text(
                    stringResource(errorRes),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            stringResource(
                                if (state.agents.isEmpty()) R.string.assistant_no_agents
                                else R.string.assistant_intro
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(state.messages) { index, message ->
                    MessageBubble(
                        message = message,
                        agents = state.agents,
                        devMode = devMode,
                        onCreateRecipe = { viewModel.createRecipe(it) }
                    )
                }
                if (state.pending) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            attachment?.let { att ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    AttachmentImage(att, modifier = Modifier.height(48.dp).clip(RoundedCornerShape(8.dp)))
                    TextButton(onClick = { attachment = null }) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.canAttachMedia) {
                    IconButton(onClick = { pickImage.launch("image/*") }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.assistant_attach_image)
                        )
                    }
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.assistant_placeholder)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences
                    ),
                    enabled = !state.pending && state.agents.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = send,
                    enabled = !state.pending && (input.isNotBlank() || attachment != null)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_send)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    agents: List<AgentDto>,
    devMode: Boolean,
    onCreateRecipe: (RecipeSuggestion) -> Unit
) {
    val isUser = message.role == ChatRole.USER
    val containerColor = when {
        message.error -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = containerColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                message.attachments.forEach { att ->
                    if (att.mimeType.startsWith("image/")) {
                        AttachmentImage(
                            att,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                if (message.text.isNotBlank() && !message.hideText) {
                    if (isUser || message.error) {
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Markdown(content = message.text)
                    }
                }
                message.recipe?.let { recipe ->
                    RecipeSuggestionCard(recipe, onCreate = { onCreateRecipe(recipe) })
                }
                if (message.via.isNotEmpty()) {
                    val viaLabel = message.via.map { agentLabel(it, agents) }.joinToString(", ")
                    Text(
                        stringResource(R.string.assistant_via, viaLabel),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (devMode && message.dev != null) {
                    DevStatsBlock(message.dev!!, agents)
                }
            }
        }
    }
}

@Composable
private fun DevStatsBlock(dev: DevStats, agents: List<AgentDto>) {
    val mono = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
    Text(
        "${agentLabel(dev.agentId, agents)}${agentModel(dev.agentId, agents)?.let { " · $it" } ?: ""}" +
                " · ${dev.latencyMs} ms",
        style = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        "${stringResource(R.string.assistant_dev_conversation)}: ${dev.conversationId.take(8)}…",
        style = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    dev.delegations.forEachIndexed { index, delegation ->
        DevDelegation(delegation, agents, mono, index)
    }
}

@Composable
private fun DevDelegation(
    delegation: DelegationDto,
    agents: List<AgentDto>,
    mono: TextStyle,
    index: Int
) {
    var expanded by remember(index) { mutableStateOf(false) }
    Text(
        "↳ ${agentLabel(delegation.agentId, agents)}" +
                (agentModel(delegation.agentId, agents)?.let { " · $it" } ?: "") +
                (delegation.latencyMs?.let { " · $it ms" } ?: ""),
        style = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clickable { expanded = !expanded }
            .padding(vertical = 2.dp)
    )
    if (expanded) {
        SelectionContainer {
            Text(
                delegation.text,
                style = mono,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun RecipeSuggestionCard(recipe: RecipeSuggestion, onCreate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(recipe.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.recipe_servings, recipe.servings ?: 4),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            recipe.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            val ingredients = recipe.ingredients.orEmpty()
            if (ingredients.isNotEmpty()) {
                Text(
                    ingredients.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Button(onClick = onCreate, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.assistant_create_recipe))
            }
        }
    }
}

@Composable
private fun AttachmentImage(attachment: Attachment, modifier: Modifier = Modifier) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, attachment.data) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(attachment.data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()?.asImageBitmap()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(R.string.assistant_image_attached),
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun agentLabel(agentId: String, agents: List<AgentDto>): String {
    val agent = agents.firstOrNull { it.id == agentId } ?: return agentId
    return agentName(agent)
}

private fun agentModel(agentId: String, agents: List<AgentDto>): String? =
    agents.firstOrNull { it.id == agentId }?.model

@Composable
private fun agentName(agent: AgentDto): String = when (agent.nameKey) {
    "assistant.orchestrator.name" -> stringResource(R.string.assistant_agent_orchestrator)
    "assistant.general.name" -> stringResource(R.string.assistant_agent_general)
    "assistant.recipe-from-image.name" -> stringResource(R.string.assistant_agent_recipe_image)
    "assistant.menu-planner.name" -> stringResource(R.string.assistant_agent_menu_planner)
    else -> agent.nameKey.substringAfterLast('.').ifBlank { agent.id }
}
