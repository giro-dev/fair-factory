package dev.agiro.fanel.automation.domain;

import dev.agiro.fanel.automation.api.RuleType;
import dev.agiro.fanel.shared.events.NotificationRequested;
import dev.agiro.fanel.shopping.api.ShoppingApi;
import dev.agiro.fanel.shopping.api.ShoppingItemDto;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Publishes a push notification with the number of pending items on the default shopping list. */
@Component
public class ShoppingReminderRunner implements RuleRunner {
    private final ShoppingApi shopping;
    private final ApplicationEventPublisher publisher;

    public ShoppingReminderRunner(ShoppingApi shopping, ApplicationEventPublisher publisher) {
        this.shopping = shopping;
        this.publisher = publisher;
    }

    @Override
    public RuleType type() {
        return RuleType.SHOPPING_REMINDER;
    }

    // Transactional so the NotificationRequested reaches @ApplicationModuleListener consumers
    // (transactional event listeners drop events published outside a transaction). Short-lived:
    // a count query plus the publication, no remote calls.
    @Override
    @Transactional
    public void run(UUID householdId, Locale locale) {
        long pending = shopping.getDefaultList(householdId).items().stream()
                .filter(item -> !item.done())
                .count();
        if (pending > 0) {
            publisher.publishEvent(new NotificationRequested(householdId, List.of(),
                    "notifications.shopping-reminder.title", "notifications.shopping-reminder.body",
                    List.of(String.valueOf(pending))));
        }
    }
}
