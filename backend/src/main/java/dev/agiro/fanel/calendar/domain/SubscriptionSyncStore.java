package dev.agiro.fanel.calendar.domain;

import dev.agiro.fanel.calendar.infra.CalendarEventRepository;
import dev.agiro.fanel.calendar.infra.CalendarSubscriptionRepository;
import dev.agiro.fanel.calendar.infra.IcsParser.ParsedEvent;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Transactional persistence for subscription syncs, kept separate from the (remote, slow) fetch
 * so no database transaction is held while HTTP or LLM-style work is in flight.
 */
@Component
public class SubscriptionSyncStore {
    private final CalendarSubscriptionRepository subscriptions;
    private final CalendarEventRepository events;

    public SubscriptionSyncStore(CalendarSubscriptionRepository subscriptions, CalendarEventRepository events) {
        this.subscriptions = subscriptions;
        this.events = events;
    }

    /** Replaces all events of a subscription with the freshly parsed ones and marks the sync OK. */
    @Transactional
    public CalendarSubscription replaceEvents(UUID subscriptionId, List<ParsedEvent> parsed) {
        CalendarSubscription subscription = subscriptions.findById(subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException("Calendar subscription not found: " + subscriptionId));
        events.deleteBySubscriptionId(subscriptionId);
        for (ParsedEvent p : parsed) {
            CalendarEvent event = new CalendarEvent(subscription.getHouseholdId(), p.title(), p.date(), p.time(),
                    p.durationMinutes(), null, null, p.freq(), p.interval(), p.until());
            event.setExternal(subscriptionId, p.uid());
            events.save(event);
        }
        subscription.setLastSyncedAt(Instant.now());
        subscription.setLastError(null);
        return subscriptions.save(subscription);
    }

    /** Records a failed sync without touching the previously imported events. */
    @Transactional
    public void markError(UUID subscriptionId, String error) {
        subscriptions.findById(subscriptionId).ifPresent(subscription -> {
            String truncated = error != null && error.length() > 1000 ? error.substring(0, 1000) : error;
            subscription.setLastError(truncated);
            subscriptions.save(subscription);
        });
    }
}
