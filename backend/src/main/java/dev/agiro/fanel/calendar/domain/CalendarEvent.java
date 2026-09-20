package dev.agiro.fanel.calendar.domain;

import dev.agiro.fanel.calendar.api.RecurrenceFrequency;
import dev.agiro.fanel.shared.domain.UuidEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "calendar_event")
public class CalendarEvent extends UuidEntity {
    @Column(name = "household_id", nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID householdId;
    private String title;
    @Column(name = "event_date", nullable = false)
    private LocalDate date;
    @Column(name = "event_time")
    private LocalTime time;
    /** Length in minutes; only meaningful when {@link #time} is set. */
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Column(name = "added_by")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID addedBy;

    /** Members this event is for; may be empty (shared/unassigned) or have several (e.g. siblings). */
    @ElementCollection
    @CollectionTable(name = "calendar_event_assignee", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "member_id")
    @JdbcTypeCode(SqlTypes.CHAR)
    private Set<UUID> assigneeIds = new HashSet<>();

    /** Null means the event does not repeat. When set, the event repeats every {@link #recurrenceInterval}
     * {@link #recurrenceFreq} starting from {@link #date}, optionally until {@link #recurrenceUntil} (inclusive). */
    @Column(name = "recurrence_freq")
    @Enumerated(EnumType.STRING)
    private RecurrenceFrequency recurrenceFreq;
    @Column(name = "recurrence_interval")
    private Integer recurrenceInterval;
    @Column(name = "recurrence_until")
    private LocalDate recurrenceUntil;

    protected CalendarEvent() {}

    public CalendarEvent(UUID householdId, String title, LocalDate date, LocalTime time, Integer durationMinutes,
                         UUID addedBy, List<UUID> assigneeIds, RecurrenceFrequency recurrenceFreq,
                         Integer recurrenceInterval, LocalDate recurrenceUntil) {
        this.householdId = householdId;
        this.title = title;
        this.date = date;
        this.time = time;
        this.durationMinutes = durationMinutes;
        this.addedBy = addedBy;
        if (assigneeIds != null) this.assigneeIds = new HashSet<>(assigneeIds);
        this.recurrenceFreq = recurrenceFreq;
        this.recurrenceInterval = recurrenceInterval;
        this.recurrenceUntil = recurrenceUntil;
    }

    public UUID getHouseholdId() { return householdId; }
    public String getTitle() { return title; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public UUID getAddedBy() { return addedBy; }
    public List<UUID> getAssigneeIds() { return assigneeIds.stream().toList(); }
    public RecurrenceFrequency getRecurrenceFreq() { return recurrenceFreq; }
    public Integer getRecurrenceInterval() { return recurrenceInterval; }
    public LocalDate getRecurrenceUntil() { return recurrenceUntil; }

    public void setTitle(String title) { this.title = title; }
    public void setDate(LocalDate date) { this.date = date; }
    public void setTime(LocalTime time) { this.time = time; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setAssigneeIds(List<UUID> assigneeIds) { this.assigneeIds = new HashSet<>(assigneeIds); }
    public void setRecurrence(RecurrenceFrequency freq, Integer interval, LocalDate until) {
        this.recurrenceFreq = freq;
        this.recurrenceInterval = freq == null ? null : interval;
        this.recurrenceUntil = freq == null ? null : until;
    }
}
