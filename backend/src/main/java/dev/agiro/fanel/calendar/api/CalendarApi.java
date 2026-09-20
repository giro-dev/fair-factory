package dev.agiro.fanel.calendar.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public interface CalendarApi {
    /** Occurrences (recurring events expanded) within [from, to]; defaults to roughly the last month through the next year when null. */
    List<CalendarEventDto> list(UUID householdId, LocalDate from, LocalDate to);
    /** Raw series, one row per event (no recurrence expansion); used for export. */
    List<CalendarEventDto> listAll(UUID householdId);
    CalendarEventDto create(UUID householdId, String title, LocalDate date, LocalTime time, Integer durationMinutes,
                            UUID addedBy, List<UUID> assigneeIds, RecurrenceFrequency recurrenceFreq,
                            Integer recurrenceInterval, LocalDate recurrenceUntil);
    CalendarEventDto update(UUID householdId, UUID eventId, String title, LocalDate date, LocalTime time,
                            Integer durationMinutes, List<UUID> assigneeIds, RecurrenceFrequency recurrenceFreq,
                            Integer recurrenceInterval, LocalDate recurrenceUntil);
    void delete(UUID householdId, UUID eventId);
}
