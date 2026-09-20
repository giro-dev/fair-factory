package dev.agiro.fanel.household.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MemberDto(UUID id, UUID householdId, String name, MemberRole role, String color, Instant createdAt,
                         String username, List<UUID> guardianIds, boolean hasPin, boolean hasCredentials) {
}
