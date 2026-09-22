package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.infra.AutomationRuleRepository;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.zone.ZoneRulesException;

/**
 * Every hour, runs the enabled automation rules whose configured day-of-week and hour match the
 * current local time in each household's timezone. A rule fires at most once per local day.
 */
@Component
public class AutomationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutomationScheduler.class);

    private final AutomationRuleRepository rules;
    private final AutomationService service;
    private final HouseholdApi household;

    public AutomationScheduler(AutomationRuleRepository rules, AutomationService service, HouseholdApi household) {
        this.rules = rules;
        this.service = service;
        this.household = household;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runDueRules() {
        Instant now = Instant.now();
        for (HouseholdDto householdDto : household.list()) {
            ZonedDateTime local;
            try {
                local = now.atZone(ZoneId.of(householdDto.timezone()));
            } catch (ZoneRulesException e) {
                log.warn("Unknown timezone {} for household {}", householdDto.timezone(), householdDto.id());
                continue;
            }
            for (AutomationRule rule : rules.findByHouseholdIdOrderByTypeAsc(householdDto.id())) {
                if (!rule.isEnabled() || !isDue(rule, local)) continue;
                try {
                    service.runNow(rule.getHouseholdId(), rule.getId());
                } catch (Exception e) {
                    log.warn("Automation rule {} ({}) of household {} failed",
                            rule.getId(), rule.getType(), householdDto.id(), e);
                }
            }
        }
    }

    static boolean isDue(AutomationRule rule, ZonedDateTime localNow) {
        if (rule.getDayOfWeek() != localNow.getDayOfWeek().getValue()) return false;
        if (rule.getHour() != localNow.getHour()) return false;
        if (rule.getLastRunAt() == null) return true;
        LocalDate lastRunDate = rule.getLastRunAt().atZone(localNow.getZone()).toLocalDate();
        return lastRunDate.isBefore(localNow.toLocalDate());
    }
}
