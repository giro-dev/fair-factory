package dev.agiro.fanel.calendar.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** Periodically refreshes every external calendar subscription. */
@Component
public class SubscriptionSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionSyncScheduler.class);

    private final CalendarSubscriptionService subscriptions;

    public SubscriptionSyncScheduler(CalendarSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Scheduled(fixedDelayString = "${fanel.calendar.ics-refresh-minutes:60}",
            timeUnit = TimeUnit.MINUTES, initialDelay = 5)
    public void syncAll() {
        for (CalendarSubscription subscription : subscriptions.listAll()) {
            try {
                subscriptions.sync(subscription);
            } catch (Exception e) {
                log.warn("Sync of calendar subscription {} failed", subscription.getId(), e);
            }
        }
    }
}
