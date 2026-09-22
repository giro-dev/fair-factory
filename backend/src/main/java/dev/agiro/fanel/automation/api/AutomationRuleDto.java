package dev.agiro.fanel.automation.api;

import java.time.Instant;
import java.util.UUID;

public record AutomationRuleDto(UUID id, UUID householdId, RuleType type, boolean enabled,
                                int dayOfWeek, int hour, Instant lastRunAt) {
}
