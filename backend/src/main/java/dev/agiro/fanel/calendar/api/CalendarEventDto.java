package dev.agiro.fanel.calendar.api;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CalendarEventDto(UUID id, UUID householdId, String title, LocalDate date, LocalDate anchorDate,
                               LocalTime time, Integer durationMinutes, UUID addedBy, List<UUID> assigneeIds,
                               RecurrenceFrequency recurrenceFreq, Integer recurrenceInterval,
                               LocalDate recurrenceUntil) {
}
