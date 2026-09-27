package dev.agiro.fanel.assistant.api;

/**
 * Effective configuration of a catalogue agent: the yml defaults merged with the persisted
 * override (if any).
 */
public record AgentConfigDto(String id, String nameKey, String descriptionKey, boolean supportsMedia,
                             boolean orchestrator, boolean enabled, String provider, String model,
                             Double temperature, Integer maxTokens, ModelDefaults defaults,
                             boolean overridden, boolean available) {

    /** The catalogue (yml) defaults for the agent's model, before any override is applied. */
    public record ModelDefaults(String provider, String model, Double temperature, Integer maxTokens) {
    }
}
