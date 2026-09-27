package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.Attachment;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Shared helpers for {@link Agent} implementations. */
public final class AgentSupport {

    private AgentSupport() {}

    public static String resolveConversationId(AgentRequest request) {
        return request.conversationId() != null && !request.conversationId().isBlank()
                ? request.conversationId()
                : UUID.randomUUID().toString();
    }

    public static String householdContext(UUID householdId, UUID memberId) {
        return "\n\nYou are assisting household " + householdId
                + (memberId != null ? " and member " + memberId : "")
                + ". When calling tools that require a householdId, always use " + householdId
                + ". When a tool needs year and week, prefer getCurrentIsoWeek().";
    }

    public static List<Media> toMedia(List<Attachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> new Media(MimeType.valueOf(a.mimeType()),
                        new ByteArrayResource(Base64.getDecoder().decode(a.data()))))
                .toList();
    }
}
