package dev.agiro.fanel.automation.api;

import java.util.List;
import java.util.UUID;

public interface AutomationApi {
    List<AutomationRuleDto> list(UUID householdId);
    AutomationRuleDto create(UUID householdId, RuleType type, int dayOfWeek, int hour);
    AutomationRuleDto update(UUID householdId, UUID ruleId, Boolean enabled, Integer dayOfWeek, Integer hour);
    void delete(UUID householdId, UUID ruleId);
    /** Runs the rule immediately, regardless of its schedule, and records {@code lastRunAt}. */
    AutomationRuleDto runNow(UUID householdId, UUID ruleId);
}
