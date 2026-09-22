package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.api.RuleType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AutomationSchedulerTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    private static AutomationRule rule(int dayOfWeek, int hour, Instant lastRunAt) {
        AutomationRule rule = new AutomationRule(UUID.randomUUID(), RuleType.SHOPPING_REMINDER, dayOfWeek, hour);
        rule.setLastRunAt(lastRunAt);
        return rule;
    }

    @Test
    void dueWhenDayAndHourMatchInHouseholdZone() {
        // 2026-03-02 is a Monday. 18:00 Europe/Madrid is 17:00 UTC — still matches on the Madrid clock.
        ZonedDateTime localNow = ZonedDateTime.of(2026, 3, 2, 18, 0, 0, 0, MADRID);
        assertThat(AutomationScheduler.isDue(rule(1, 18, null), localNow)).isTrue();
        assertThat(AutomationScheduler.isDue(rule(1, 17, null), localNow)).isFalse();
        assertThat(AutomationScheduler.isDue(rule(2, 18, null), localNow)).isFalse();
    }

    @Test
    void notDueTwiceTheSameLocalDay() {
        ZonedDateTime localNow = ZonedDateTime.of(2026, 3, 2, 18, 30, 0, 0, MADRID);
        Instant earlierToday = ZonedDateTime.of(2026, 3, 2, 18, 0, 0, 0, MADRID).toInstant();
        assertThat(AutomationScheduler.isDue(rule(1, 18, earlierToday), localNow)).isFalse();
    }

    @Test
    void dueAgainWhenLastRunWasYesterday() {
        ZonedDateTime localNow = ZonedDateTime.of(2026, 3, 2, 18, 0, 0, 0, MADRID);
        Instant yesterday = ZonedDateTime.of(2026, 3, 1, 18, 0, 0, 0, MADRID).toInstant();
        assertThat(AutomationScheduler.isDue(rule(1, 18, yesterday), localNow)).isTrue();
        // A run stamped 00:30 UTC is already the same local day in Madrid.
        Instant utcYesterdayMadridToday = ZonedDateTime.of(2026, 3, 2, 0, 30, 0, 0, ZoneId.of("UTC")).toInstant();
        assertThat(AutomationScheduler.isDue(rule(1, 18, utcYesterdayMadridToday), localNow)).isFalse();
    }
}
