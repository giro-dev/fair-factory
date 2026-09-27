package dev.agiro.fanel.assistant.domain.agents;

import dev.agiro.fanel.assistant.api.AgentDefinition;
import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.Attachment;
import dev.agiro.fanel.assistant.domain.Agent;
import dev.agiro.fanel.assistant.infra.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static dev.agiro.fanel.assistant.domain.AgentSupport.householdContext;
import static dev.agiro.fanel.assistant.domain.AgentSupport.resolveConversationId;
import static dev.agiro.fanel.assistant.domain.AgentSupport.toMedia;

public class DefaultAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(DefaultAgent.class);
    private final AgentDefinition definition;
    private final ChatClient chatClient;
    private final PromptLoader promptLoader;

    public DefaultAgent(AgentDefinition definition, ChatClient chatClient, PromptLoader promptLoader) {
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

        String systemPrompt = promptLoader.load(definition.id(), locale);
        String context = householdContext(householdId, memberId);

        log.debug("Agent {} executing for household {} with conversationId {}, prompt length {}",
                definition.id(), householdId, conversationId,
                systemPrompt != null ? systemPrompt.length() : 0);
        log.debug("User message: {}", request.message());
        if (request.attachments() != null) {
            for (Attachment attachment : request.attachments()) {
                log.debug("Attachment: mimeType={}, dataLength={}", attachment.mimeType(), attachment.data().length());
            }
        }

        List<Media> media = toMedia(request.attachments());
        var prompt = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.system(systemPrompt + context);
        } else {
            prompt.system(context.trim());
        }
        String response = prompt
                .user(user -> {
                    user.text(request.message());
                    for (Media m : media) {
                        user.media(m);
                    }
                })
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.debug("Agent {} response: {}", definition.id(), response);
        return new AgentResponse(definition.id(), conversationId, response != null ? response : "",
                List.of(), List.of());
    }
}
