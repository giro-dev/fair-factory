package dev.agiro.fanel.assistant.api;

import java.util.List;

public record AgentResponse(String agentId, String conversationId, String text, List<String> toolCalls,
                            List<Delegation> delegations) {
}
