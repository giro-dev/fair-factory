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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

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
        String conversationId = request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();

        String systemPrompt = promptLoader.load(definition.id(), locale);
        String context = "\n\nYou are assisting household " + householdId
                + (memberId != null ? " and member " + memberId : "")
                + ". When calling tools that require a householdId, always use " + householdId
                + ". When a tool needs year and week, prefer getCurrentIsoWeek().";

        log.debug("Agent {} executing for household {} with conversationId {}, prompt length {}",
                definition.id(), householdId, conversationId,
                systemPrompt != null ? systemPrompt.length() : 0);
        log.debug("User message: {}", request.message());
        if (request.attachments() != null) {
            for (Attachment attachment : request.attachments()) {
                log.debug("Attachment: mimeType={}, dataLength={}", attachment.mimeType(), attachment.data().length());
            }
        }

        var prompt = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            prompt.system(systemPrompt + context);
        } else {
            prompt.system(context.trim());
        }
        String response = prompt
                .user(user -> {
                    user.text(request.message());
                    if (request.attachments() != null) {
                        for (Attachment attachment : request.attachments()) {
                            byte[] bytes = Base64.getDecoder().decode(attachment.data());
                            MimeType mimeType = MimeType.valueOf(attachment.mimeType());
                            user.media(new Media(mimeType, new ByteArrayResource(bytes)));
                        }
                    }
                })
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        log.debug("Agent {} response: {}", definition.id(), response);
        return new AgentResponse(definition.id(), conversationId, response != null ? response : "", List.of());
    }
}
