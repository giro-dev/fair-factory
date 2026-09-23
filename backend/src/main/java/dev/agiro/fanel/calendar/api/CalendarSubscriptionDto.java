package dev.agiro.fanel.calendar.api;

import java.time.Instant;
import java.util.UUID;

public record CalendarSubscriptionDto(UUID id, UUID householdId, String name, String url, String color,
                                      Instant lastSyncedAt, String lastError) {
}
