package dev.agiro.fanel.assistant.api;

import java.util.List;
import java.util.UUID;

public record AgentRequest(String agentId, String conversationId, String message, List<Attachment> attachments,
                           UUID memberId) {
}
