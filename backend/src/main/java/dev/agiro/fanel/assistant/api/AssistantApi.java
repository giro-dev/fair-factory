package dev.agiro.fanel.assistant.api;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public interface AssistantApi {
    /**
     * Runs the given agent with a one-shot message. Returns {@code Optional.empty()} when the agent
     * is not registered (e.g. no model provider configured) or the model call fails.
     */
    Optional<AgentResponse> run(UUID householdId, String agentId, String message, Locale locale);
}
