package dev.agiro.fanel.assistant.domain.agents;

import dev.agiro.fanel.assistant.api.AgentDefinition;
import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.Attachment;
import dev.agiro.fanel.assistant.api.Delegation;
import dev.agiro.fanel.assistant.domain.Agent;
import dev.agiro.fanel.assistant.domain.DelegationContext;
import dev.agiro.fanel.assistant.infra.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static dev.agiro.fanel.assistant.domain.AgentSupport.householdContext;
import static dev.agiro.fanel.assistant.domain.AgentSupport.resolveConversationId;
import static dev.agiro.fanel.assistant.domain.AgentSupport.toMedia;

/**
 * The default chat agent. It answers directly with the shared household tools and delegates
 * specialised tasks to the subagents exposed as {@code delegate_to_*} tool callbacks.
 */
public class OrchestratorAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(OrchestratorAgent.class);

    private final AgentDefinition definition;
    private final ChatClient chatClient;
    private final PromptLoader promptLoader;

    public OrchestratorAgent(AgentDefinition definition, ChatClient chatClient, PromptLoader promptLoader) {
        this.definition = definition;
        this.chatClient = chatClient;
        this.promptLoader = promptLoader;
    }

    @Override
    public AgentDefinition definition() {
        return definition;
    }

    @Override
    public AgentResponse execute(AgentRequest request, UUID householdId, UUID memberId, Locale locale) {
        String conversationId = resolveConversationId(request);
        List<Attachment> attachments = request.attachments() != null ? request.attachments() : List.of();
        DelegationContext ctx = new DelegationContext(householdId, memberId, locale, conversationId,
                attachments, new CopyOnWriteArrayList<>());

        String systemPrompt = promptLoader.load(definition.id(), locale);
        String context = householdContext(householdId, memberId);

        String userText = request.message();
        List<Media> media = List.of();
        if (!attachments.isEmpty()) {
            if (definition.supportsMedia()) {
                media = toMedia(attachments);
            } else {
                String mimeTypes = attachments.stream().map(Attachment::mimeType).distinct().toList().toString();
                userText += "\n\n[The user attached " + attachments.size() + " image(s) (" + mimeTypes
                        + "). You cannot see them; delegate to a subagent that supports images.]";
            }
        }

        log.debug("Orchestrator executing for household {} with conversationId {}", householdId, conversationId);

        String finalUserText = userText;
        List<Media> finalMedia = media;
        var prompt = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.system(systemPrompt + context);
        } else {
            prompt.system(context.trim());
        }
        String response = prompt
                .user(user -> {
                    user.text(finalUserText);
                    for (Media m : finalMedia) {
                        user.media(m);
                    }
                })
                .toolContext(Map.of(DelegationContext.KEY, ctx))
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        List<String> toolCalls = ctx.collector().stream().map(Delegation::agentId).distinct().toList();
        return new AgentResponse(definition.id(), conversationId, response != null ? response : "",
                toolCalls, List.copyOf(ctx.collector()));
    }
}
