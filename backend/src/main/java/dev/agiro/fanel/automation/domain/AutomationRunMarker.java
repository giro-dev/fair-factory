package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.infra.AutomationRuleRepository;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/** Stamps {@code lastRunAt} on a rule in its own short transaction, after the rule ran without one. */
@Component
public class AutomationRunMarker {
    private final AutomationRuleRepository rules;

    public AutomationRunMarker(AutomationRuleRepository rules) {
        this.rules = rules;
    }

    @Transactional
    public AutomationRule markRun(UUID householdId, UUID ruleId) {
        AutomationRule rule = rules.findByHouseholdIdAndId(householdId, ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Automation rule not found: " + ruleId));
        rule.setLastRunAt(Instant.now());
        return rules.save(rule);
    }
}
