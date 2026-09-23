package dev.agiro.fanel.calendar.domain;

import dev.agiro.fanel.calendar.api.CalendarApi;
import dev.agiro.fanel.calendar.api.CalendarEventDto;
import dev.agiro.fanel.calendar.api.CalendarSubscriptionDto;
import dev.agiro.fanel.calendar.api.EventSource;
import dev.agiro.fanel.calendar.api.RecurrenceFrequency;
import dev.agiro.fanel.calendar.infra.CalendarEventRepository;
import dev.agiro.fanel.calendar.infra.IcsParser;
import dev.agiro.fanel.calendar.infra.IcsParser.ParsedEvent;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.shared.events.HouseholdEvent;
import dev.agiro.fanel.shared.web.ConflictException;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CalendarService implements CalendarApi {
    public static final String TOPIC = "calendar";

    /** Safety valve so an unbounded recurrence (no "until") never expands past this many occurrences per request. */
    private static final int MAX_OCCURRENCES_PER_EVENT = 1000;

    private final CalendarEventRepository events;
    private final ApplicationEventPublisher publisher;
    private final CalendarSubscriptionService subscriptions;
    private final IcsParser icsParser;
    private final HouseholdApi household;

    public CalendarService(CalendarEventRepository events, ApplicationEventPublisher publisher,
                           CalendarSubscriptionService subscriptions, IcsParser icsParser, HouseholdApi household) {
        this.events = events;
        this.publisher = publisher;
        this.subscriptions = subscriptions;
        this.icsParser = icsParser;
        this.household = household;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventDto> list(UUID householdId, LocalDate from, LocalDate to) {
        List<CalendarEvent> all = events.findAllByHouseholdIdOrderByDateAsc(householdId);
        LocalDate rangeFrom = from != null ? from : all.stream()
                .map(CalendarEvent::getDate)
                .min(LocalDate::compareTo)
                .orElseGet(() -> LocalDate.now().minusMonths(1));
        LocalDate rangeTo = to != null ? to : LocalDate.now().plusMonths(12);
        return all.stream()
                .flatMap(e -> expand(e, rangeFrom, rangeTo).stream())
                .sorted(Comparator.comparing(CalendarEventDto::date)
                        .thenComparing(dto -> dto.time() == null ? LocalTime.MIN : dto.time()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarEventDto> listAll(UUID householdId) {
        return events.findAllByHouseholdIdOrderByDateAsc(householdId).stream()
                .map(e -> toDto(e, e.getDate())).toList();
    }

    @Override
    public CalendarEventDto create(UUID householdId, String title, LocalDate date, LocalTime time,
                                   Integer durationMinutes, UUID addedBy, List<UUID> assigneeIds,
                                   RecurrenceFrequency recurrenceFreq, Integer recurrenceInterval,
                                   LocalDate recurrenceUntil) {
        CalendarEvent saved = events.save(new CalendarEvent(householdId, title, date, time,
                positiveOrNull(durationMinutes), addedBy, assigneeIds,
                recurrenceFreq, recurrenceInterval, recurrenceUntil));
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(saved, saved.getDate());
    }

    @Override
    public CalendarEventDto update(UUID householdId, UUID eventId, String title, LocalDate date, LocalTime time,
                                   Integer durationMinutes, List<UUID> assigneeIds,
                                   RecurrenceFrequency recurrenceFreq, Integer recurrenceInterval,
                                   LocalDate recurrenceUntil) {
        CalendarEvent event = find(householdId, eventId);
        rejectExternal(event);
        if (title != null) event.setTitle(title);
        if (date != null) event.setDate(date);
        if (time != null) event.setTime(time);
        event.setDurationMinutes(positiveOrNull(durationMinutes));
        if (assigneeIds != null) event.setAssigneeIds(assigneeIds);
        event.setRecurrence(recurrenceFreq, recurrenceInterval, recurrenceUntil);
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
        return toDto(events.save(event), event.getDate());
    }

    @Override
    public void delete(UUID householdId, UUID eventId) {
        CalendarEvent event = find(householdId, eventId);
        rejectExternal(event);
        events.delete(event);
        publisher.publishEvent(new HouseholdEvent(householdId, TOPIC));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CalendarSubscriptionDto> listSubscriptions(UUID householdId) {
        return subscriptions.list(householdId);
    }

    // NOT_SUPPORTED: subscription creation/sync fetches a remote URL and must not hold a DB transaction.
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CalendarSubscriptionDto createSubscription(UUID householdId, String name, String url, String color) {
        return subscriptions.create(householdId, name, url, color);
    }

    @Override
    public CalendarSubscriptionDto updateSubscription(UUID householdId, UUID subscriptionId, String name,
                                                      String url, String color) {
        return subscriptions.update(householdId, subscriptionId, name, url, color);
    }

    @Override
    public void deleteSubscription(UUID householdId, UUID subscriptionId) {
        subscriptions.delete(householdId, subscriptionId);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CalendarSubscriptionDto syncSubscription(UUID householdId, UUID subscriptionId) {
        return subscriptions.syncSubscription(householdId, subscriptionId);
    }

    @Override
    public int importIcs(UUID householdId, String ics, UUID addedBy) {
        ZoneId zone;
        try {
            zone = ZoneId.of(household.get(householdId).timezone());
        } catch (DateTimeException e) {
            zone = ZoneId.systemDefault();
        }
        LocalDate today = LocalDate.now(zone);
        List<ParsedEvent> parsed = icsParser.parse(ics, zone, today.minusMonths(1), today.plusMonths(12));
        int imported = 0;
        for (ParsedEvent p : parsed) {
            create(householdId, p.title(), p.date(), p.time(), p.durationMinutes(), addedBy, null,
                    p.freq(), p.interval(), p.until());
            imported++;
        }
        return imported;
    }

    private CalendarEvent find(UUID householdId, UUID eventId) {
        return events.findById(eventId)
                .filter(e -> e.getHouseholdId().equals(householdId))
                .orElseThrow(() -> new EntityNotFoundException("Calendar event not found: " + eventId));
    }

    /** Expands a (possibly recurring) event into its occurrence(s) overlapping [from, to]. */
    private List<CalendarEventDto> expand(CalendarEvent event, LocalDate from, LocalDate to) {
        if (event.getRecurrenceFreq() == null) {
            LocalDate date = event.getDate();
            return (!date.isBefore(from) && !date.isAfter(to)) ? List.of(toDto(event, date)) : List.of();
        }
        List<CalendarEventDto> occurrences = new ArrayList<>();
        LocalDate until = event.getRecurrenceUntil();
        int interval = event.getRecurrenceInterval() == null || event.getRecurrenceInterval() < 1
                ? 1 : event.getRecurrenceInterval();
        LocalDate cursor = event.getDate();
        for (int i = 0; i < MAX_OCCURRENCES_PER_EVENT && !cursor.isAfter(to)
                && (until == null || !cursor.isAfter(until)); i++) {
            if (!cursor.isBefore(from)) occurrences.add(toDto(event, cursor));
            cursor = switch (event.getRecurrenceFreq()) {
                case DAILY -> cursor.plusDays(interval);
                case WEEKLY -> cursor.plusWeeks(interval);
                case MONTHLY -> cursor.plusMonths(interval);
                case YEARLY -> cursor.plusYears(interval);
            };
        }
        return occurrences;
    }

    private static Integer positiveOrNull(Integer minutes) {
        return minutes != null && minutes > 0 ? minutes : null;
    }

    private static CalendarEventDto toDto(CalendarEvent event, LocalDate occurrenceDate) {
        return new CalendarEventDto(event.getId(), event.getHouseholdId(), event.getTitle(), occurrenceDate,
                event.getDate(), event.getTime(), event.getDurationMinutes(), event.getAddedBy(),
                event.getAssigneeIds(),
                event.getRecurrenceFreq(), event.getRecurrenceInterval(), event.getRecurrenceUntil(),
                event.getSource(), event.getSubscriptionId());
    }

    private static void rejectExternal(CalendarEvent event) {
        if (event.getSource() == EventSource.ICS) {
            throw new ConflictException("External calendar events are read-only");
        }
    }
}
