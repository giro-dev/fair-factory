package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.api.RuleType;

import java.util.Locale;
import java.util.UUID;

/** Executes one kind of automation rule. */
public interface RuleRunner {
    RuleType type();
    void run(UUID householdId, Locale locale);
}
