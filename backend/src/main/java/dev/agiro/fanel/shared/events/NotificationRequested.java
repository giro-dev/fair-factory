package dev.agiro.fanel.shared.events;

import java.util.List;
import java.util.UUID;

/**
 * Requests a push notification to household members. An empty {@code memberIds} list targets
 * all members of the household. {@code titleKey}/{@code bodyKey} are {@link java.util.ResourceBundle}
 * message keys resolved in the household locale; {@code bodyArgs} are the body message arguments.
 */
public record NotificationRequested(UUID householdId, List<UUID> memberIds, String titleKey,
                                    String bodyKey, List<String> bodyArgs) {
}
