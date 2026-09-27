package dev.agiro.fanel.assistant.api;

import java.util.List;

public record AgentDefinition(String id, String nameKey, String descriptionKey,
                              boolean supportsMedia, List<String> toolNames,
                              String toolDescription, boolean orchestrator,
                              String model) {
}
