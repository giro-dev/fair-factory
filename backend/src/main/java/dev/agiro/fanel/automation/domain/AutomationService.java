package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.api.AutomationApi;
import dev.agiro.fanel.automation.api.AutomationRuleDto;
import dev.agiro.fanel.automation.api.RuleType;
import dev.agiro.fanel.automation.infra.AutomationRuleRepository;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.shared.events.HouseholdEvent;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AutomationService implements AutomationApi {
    public static final String TOPIC = "automation";

    private final AutomationRuleRepository rules;
    private final HouseholdApi household;
    private final ApplicationEventPublisher publisher;
    private final AutomationRunMarker runMarker;
    private final Map<RuleType, RuleRunner> runners;

    public AutomationService(AutomationRuleRepository rules, HouseholdApi household,
                             ApplicationEventPublisher publisher, AutomationRunMarker runMarker,
                             List<RuleRunner> runners) {
        this.rules = rules;
        this.household = household;
        this.publisher = publisher;
        this.runMarker = runMarker;
        this.runners = runners.stream().collect(Collectors.toMap(RuleRunner::type, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutomationRuleDto> list(UUID householdId) {
        return rules.findByHouseholdIdOrderByTypeAsc(householdId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public AutomationRuleDto create(UUID householdId, RuleType type, int dayOfWeek, int hour) {
        validateSchedule(dayOfWeek, hour);
        AutomationRule rule = rules.save(new AutomationRule(householdId, type, dayOfWeek, hour));
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(rule);
    }

    @Override
    @Transactional
    public AutomationRuleDto update(UUID householdId, UUID ruleId, Boolean enabled, Integer dayOfWeek, Integer hour) {
        AutomationRule rule = getRule(householdId, ruleId);
        if (enabled != null) rule.setEnabled(enabled);
        if (dayOfWeek != null) {
            validateDayOfWeek(dayOfWeek);
            rule.setDayOfWeek(dayOfWeek);
        }
        if (hour != null) {
            validateHour(hour);
            rule.setHour(hour);
        }
        AutomationRule saved = rules.save(rule);
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID householdId, UUID ruleId) {
        rules.delete(getRule(householdId, ruleId));
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
    }

    /**
     * Runs the rule outside of any transaction (a run may take seconds, e.g. an LLM round-trip)
     * and stamps {@code lastRunAt} in a short separate transaction afterwards.
     */
    @Override
    public AutomationRuleDto runNow(UUID householdId, UUID ruleId) {
        AutomationRule rule = getRule(householdId, ruleId);
        RuleRunner runner = runners.get(rule.getType());
        if (runner == null) {
            throw new IllegalStateException("No runner for rule type " + rule.getType());
        }
        Locale locale = Locale.forLanguageTag(household.get(householdId).locale());
        runner.run(householdId, locale);
        return toDto(runMarker.markRun(householdId, ruleId));
    }

    private AutomationRule getRule(UUID householdId, UUID ruleId) {
        return rules.findByHouseholdIdAndId(householdId, ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Automation rule not found: " + ruleId));
    }

    private static void validateSchedule(int dayOfWeek, int hour) {
        validateDayOfWeek(dayOfWeek);
        validateHour(hour);
    }

    private static void validateDayOfWeek(int dayOfWeek) {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalArgumentException("dayOfWeek must be between 1 (Monday) and 7 (Sunday)");
        }
    }

    private static void validateHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be between 0 and 23");
        }
    }

    private AutomationRuleDto toDto(AutomationRule rule) {
        return new AutomationRuleDto(rule.getId(), rule.getHouseholdId(), rule.getType(), rule.isEnabled(),
                rule.getDayOfWeek(), rule.getHour(), rule.getLastRunAt());
    }
}
