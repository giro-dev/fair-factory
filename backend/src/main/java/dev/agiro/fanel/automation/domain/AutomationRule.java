package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.api.RuleType;
import dev.agiro.fanel.shared.domain.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_rule")
public class AutomationRule extends UuidEntity {
    @Column(name = "household_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID householdId;
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private RuleType type;
    @Column(nullable = false)
    private boolean enabled;
    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;
    /** Local hour (0–23) in the household timezone at which the rule fires. */
    @Column(name = "hour", nullable = false)
    private int hour;
    @Column(name = "last_run_at")
    private Instant lastRunAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AutomationRule() {}

    public AutomationRule(UUID householdId, RuleType type, int dayOfWeek, int hour) {
        this.householdId = householdId;
        this.type = type;
        this.enabled = true;
        this.dayOfWeek = dayOfWeek;
        this.hour = hour;
        this.createdAt = Instant.now();
    }

    public UUID getHouseholdId() { return householdId; }
    public RuleType getType() { return type; }
    public boolean isEnabled() { return enabled; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getHour() { return hour; }
    public Instant getLastRunAt() { return lastRunAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDayOfWeek(int dayOfWeek) { this.dayOfWeek = dayOfWeek; }
    public void setHour(int hour) { this.hour = hour; }
    public void setLastRunAt(Instant lastRunAt) { this.lastRunAt = lastRunAt; }
}
