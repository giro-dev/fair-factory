package dev.agiro.fanel.assistant.api;

/**
 * Persisted override for a catalogue agent. Null fields keep the catalogue default
 * (a null {@code enabled} keeps the current value, or {@code true} for a new override).
 */
public record AgentConfigUpdate(Boolean enabled, String provider, String model, Double temperature,
                                Integer maxTokens) {
}
