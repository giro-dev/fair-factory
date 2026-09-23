package dev.agiro.fanel.calendar.domain;

import dev.agiro.fanel.calendar.api.CalendarSubscriptionDto;
import dev.agiro.fanel.calendar.infra.CalendarEventRepository;
import dev.agiro.fanel.calendar.infra.CalendarSubscriptionRepository;
import dev.agiro.fanel.calendar.infra.HttpIcsFetcher;
import dev.agiro.fanel.calendar.infra.IcsFetcher;
import dev.agiro.fanel.calendar.infra.IcsParser;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.shared.events.HouseholdEvent;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class CalendarSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(CalendarSubscriptionService.class);

    private final CalendarSubscriptionRepository subscriptions;
    private final CalendarEventRepository events;
    private final IcsFetcher fetcher;
    private final IcsParser parser;
    private final SubscriptionSyncStore syncStore;
    private final HouseholdApi household;
    private final ApplicationEventPublisher publisher;

    public CalendarSubscriptionService(CalendarSubscriptionRepository subscriptions, CalendarEventRepository events,
                                       IcsFetcher fetcher, IcsParser parser, SubscriptionSyncStore syncStore,
                                       HouseholdApi household, ApplicationEventPublisher publisher) {
        this.subscriptions = subscriptions;
        this.events = events;
        this.fetcher = fetcher;
        this.parser = parser;
        this.syncStore = syncStore;
        this.household = household;
        this.publisher = publisher;
    }

    @Transactional(readOnly = true)
    public List<CalendarSubscriptionDto> list(UUID householdId) {
        return subscriptions.findByHouseholdIdOrderByNameAsc(householdId).stream()
                .map(this::toDto).toList();
    }

    public CalendarSubscriptionDto create(UUID householdId, String name, String url, String color) {
        String normalizedUrl = HttpIcsFetcher.toHttpUrl(url);
        CalendarSubscription subscription = subscriptions.save(
                new CalendarSubscription(householdId, name, normalizedUrl, color));
        sync(subscription);
        return toDto(subscriptions.findById(subscription.getId()).orElse(subscription));
    }

    @Transactional
    public CalendarSubscriptionDto update(UUID householdId, UUID subscriptionId, String name, String url, String color) {
        CalendarSubscription subscription = get(householdId, subscriptionId);
        if (name != null) subscription.setName(name);
        if (url != null) subscription.setUrl(HttpIcsFetcher.toHttpUrl(url));
        if (color != null) subscription.setColor(color);
        return toDto(subscriptions.save(subscription));
    }

    @Transactional
    public void delete(UUID householdId, UUID subscriptionId) {
        CalendarSubscription subscription = get(householdId, subscriptionId);
        events.deleteBySubscriptionId(subscriptionId);
        subscriptions.delete(subscription);
        publisher.publishEvent(new HouseholdEvent(householdId, CalendarService.TOPIC));
    }

    public CalendarSubscriptionDto syncSubscription(UUID householdId, UUID subscriptionId) {
        CalendarSubscription subscription = get(householdId, subscriptionId);
        sync(subscription);
        return toDto(subscriptions.findById(subscriptionId).orElse(subscription));
    }

    /**
     * Fetches and re-imports the subscription. The HTTP fetch runs without a transaction; replacing
     * the events and recording the outcome happen in short transactions inside {@link SubscriptionSyncStore}.
     * Failures are stored on {@code lastError} and keep the previous events — they never propagate.
     */
    public void sync(CalendarSubscription subscription) {
        try {
            String ics = fetcher.fetch(subscription.getUrl());
            ZoneId zone = householdZone(subscription.getHouseholdId());
            LocalDate today = LocalDate.now(zone);
            var parsed = parser.parse(ics, zone, today.minusMonths(1), today.plusMonths(12));
            syncStore.replaceEvents(subscription.getId(), parsed);
            publisher.publishEvent(new HouseholdEvent(subscription.getHouseholdId(), CalendarService.TOPIC));
        } catch (Exception e) {
            log.warn("Sync of calendar subscription {} ({}) failed: {}",
                    subscription.getId(), subscription.getUrl(), e.toString());
            syncStore.markError(subscription.getId(), e.toString());
        }
    }

    @Transactional(readOnly = true)
    public List<CalendarSubscription> listAll() {
        return subscriptions.findAll();
    }

    private CalendarSubscription get(UUID householdId, UUID subscriptionId) {
        return subscriptions.findByHouseholdIdAndId(householdId, subscriptionId)
                .orElseThrow(() -> new EntityNotFoundException("Calendar subscription not found: " + subscriptionId));
    }

    private ZoneId householdZone(UUID householdId) {
        String timezone = household.get(householdId).timezone();
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException e) {
            log.warn("Unknown timezone {} for household {}; falling back to system default", timezone, householdId);
            return ZoneId.systemDefault();
        }
    }

    private CalendarSubscriptionDto toDto(CalendarSubscription subscription) {
        return new CalendarSubscriptionDto(subscription.getId(), subscription.getHouseholdId(),
                subscription.getName(), subscription.getUrl(), subscription.getColor(),
                subscription.getLastSyncedAt(), subscription.getLastError());
    }
}
