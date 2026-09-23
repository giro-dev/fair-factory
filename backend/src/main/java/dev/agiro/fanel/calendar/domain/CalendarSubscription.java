package dev.agiro.fanel.calendar.domain;

import dev.agiro.fanel.shared.domain.UuidEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/** An external ICS calendar (read-only URL) the household subscribes to. */
@Entity
@Table(name = "calendar_subscription")
public class CalendarSubscription extends UuidEntity {
    @Column(name = "household_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID householdId;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 2048)
    private String url;
    @Column(length = 16)
    private String color;
    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;
    @Column(name = "last_error", length = 1024)
    private String lastError;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CalendarSubscription() {}

    public CalendarSubscription(UUID householdId, String name, String url, String color) {
        this.householdId = householdId;
        this.name = name;
        this.url = url;
        this.color = color;
        this.createdAt = Instant.now();
    }

    public UUID getHouseholdId() { return householdId; }
    public String getName() { return name; }
    public String getUrl() { return url; }
    public String getColor() { return color; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setUrl(String url) { this.url = url; }
    public void setColor(String color) { this.color = color; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
