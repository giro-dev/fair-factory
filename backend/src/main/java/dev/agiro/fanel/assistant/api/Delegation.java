package dev.agiro.fanel.assistant.api;

/**
 * A task the orchestrator delegated to a specialised subagent, with the subagent's raw response
 * (which may contain structured JSON, e.g. a recipe proposal) and how long it took.
 */
public record Delegation(String agentId, String text, long latencyMs) {
}
