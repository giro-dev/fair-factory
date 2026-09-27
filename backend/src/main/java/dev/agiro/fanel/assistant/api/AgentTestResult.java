package dev.agiro.fanel.assistant.api;

/**
 * Result of a connectivity ping against an agent's effective model profile (no tools, no memory):
 * it proves the provider/model can be reached, not that the agent behaves correctly.
 */
public record AgentTestResult(boolean ok, long latencyMs, String text, String error) {
}
