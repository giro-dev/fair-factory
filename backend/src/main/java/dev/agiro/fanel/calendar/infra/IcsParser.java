package dev.agiro.fanel.calendar.infra;

import biweekly.Biweekly;
import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.parameter.ICalParameters;
import biweekly.property.DateStart;
import biweekly.property.ExceptionDates;
import biweekly.property.RecurrenceRule;
import biweekly.util.DateTimeComponents;
import biweekly.util.Frequency;
import biweekly.util.ICalDate;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import dev.agiro.fanel.calendar.api.RecurrenceFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses ICS documents into {@link ParsedEvent}s. Simple RRULEs (bare FREQ/INTERVAL/UNTIL/COUNT)
 * map to the app's native recurrence; anything else is expanded into dated occurrences inside the
 * requested window.
 */
@Component
public class IcsParser {
    private static final Logger log = LoggerFactory.getLogger(IcsParser.class);
    private static final int MAX_OCCURRENCES = 2000;

    public record ParsedEvent(String uid, String title, LocalDate date, LocalTime time,
                              Integer durationMinutes, RecurrenceFrequency freq, Integer interval,
                              LocalDate until) {
    }

    public List<ParsedEvent> parse(String ics, ZoneId zone, LocalDate windowFrom, LocalDate windowTo) {
        ICalendar calendar;
        try {
            calendar = Biweekly.parse(ics).first();
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid iCalendar document: " + e.getMessage());
        }
        if (calendar == null) {
            throw new IllegalArgumentException("Not a valid iCalendar document: no VCALENDAR found");
        }

        List<ParsedEvent> out = new ArrayList<>();
        for (VEvent event : calendar.getEvents()) {
            try {
                out.addAll(parseEvent(event, zone, windowFrom, windowTo));
            } catch (Exception e) {
                log.warn("Skipping malformed VEVENT {}: {}", uidOf(event), e.toString());
            }
        }
        return out;
    }

    private List<ParsedEvent> parseEvent(VEvent event, ZoneId zone, LocalDate from, LocalDate to) {
        DateStart start = event.getDateStart();
        if (start == null || start.getValue() == null) {
            throw new IllegalArgumentException("VEVENT without DTSTART");
        }
        String uid = uidOf(event);
        String title = event.getSummary() != null && event.getSummary().getValue() != null
                ? event.getSummary().getValue() : "(untitled)";

        DateTimeComponents raw = start.getValue().getRawComponents();
        ZoneId sourceZone = sourceZone(raw, start.getParameter(ICalParameters.TZID), zone);
        boolean allDay = !raw.hasTime();
        LocalDate date = LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate());
        LocalTime time = null;
        Integer duration = null;
        if (!allDay) {
            var zoned = LocalDateTime.of(date, LocalTime.of(raw.getHour(), raw.getMinute(), raw.getSecond()))
                    .atZone(sourceZone).withZoneSameInstant(zone);
            date = zoned.toLocalDate();
            time = zoned.toLocalTime();
            duration = durationMinutes(event);
        }

        RecurrenceRule rruleProp = event.getRecurrenceRule();
        List<ExceptionDates> exdates = event.getExceptionDates();
        if (rruleProp == null || rruleProp.getValue() == null) {
            return List.of(new ParsedEvent(uid, title, date, time, duration, null, null, null));
        }
        Recurrence rrule = rruleProp.getValue();
        if (isSimple(rrule) && exdates.isEmpty()) {
            return List.of(new ParsedEvent(uid, title, date, time, duration,
                    mapFreq(rrule.getFrequency()),
                    rrule.getInterval() != null && rrule.getInterval() > 0 ? rrule.getInterval() : 1,
                    recurrenceUntil(rrule, start.getValue(), sourceZone, zone)));
        }
        return expand(event, uid, title, sourceZone, zone, allDay, duration, from, to);
    }

    /** True when the rule only uses FREQ + optional INTERVAL/UNTIL/COUNT — mappable to native recurrence. */
    private boolean isSimple(Recurrence rrule) {
        Frequency freq = rrule.getFrequency();
        if (freq == null) return false;
        switch (freq) {
            case DAILY, WEEKLY, MONTHLY, YEARLY -> { }
            default -> { return false; }
        }
        return rrule.getBySecond().isEmpty() && rrule.getByMinute().isEmpty() && rrule.getByHour().isEmpty()
                && rrule.getByDay().isEmpty() && rrule.getByMonthDay().isEmpty() && rrule.getByYearDay().isEmpty()
                && rrule.getByWeekNo().isEmpty() && rrule.getByMonth().isEmpty() && rrule.getBySetPos().isEmpty()
                && rrule.getXRules().isEmpty();
    }

    private List<ParsedEvent> expand(VEvent event, String uid, String title, ZoneId sourceZone, ZoneId zone,
                                     boolean allDay, Integer duration, LocalDate from, LocalDate to) {
        List<ParsedEvent> out = new ArrayList<>();
        Recurrence rrule = event.getRecurrenceRule().getValue();
        Set<Instant> exdateInstants = new HashSet<>();
        Set<LocalDate> exdateDays = new HashSet<>();
        for (ExceptionDates exdates : event.getExceptionDates()) {
            String tzid = exdates.getParameter(ICalParameters.TZID);
            for (ICalDate exdate : exdates.getValues()) {
                DateTimeComponents raw = exdate.getRawComponents();
                if (raw.hasTime()) {
                    exdateInstants.add(LocalDateTime.of(
                                    LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate()),
                                    LocalTime.of(raw.getHour(), raw.getMinute(), raw.getSecond()))
                            .atZone(sourceZone(raw, tzid, zone)).toInstant());
                } else {
                    exdateDays.add(LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate()));
                }
            }
        }

        DateIterator iterator = rrule.getDateIterator(event.getDateStart().getValue(),
                java.util.TimeZone.getTimeZone(sourceZone));
        for (int i = 0; i < MAX_OCCURRENCES && iterator.hasNext(); i++) {
            Instant occurrence = iterator.next().toInstant();
            var zoned = occurrence.atZone(zone);
            LocalDate occDate = zoned.toLocalDate();
            if (occDate.isAfter(to)) break;
            if (occDate.isBefore(from)) continue;
            if (exdateInstants.contains(occurrence) || exdateDays.contains(occDate)) continue;
            out.add(new ParsedEvent(uid + "#" + occDate, title, occDate,
                    allDay ? null : zoned.toLocalTime(), duration, null, null, null));
        }
        return out;
    }

    private LocalDate recurrenceUntil(Recurrence rrule, ICalDate start, ZoneId sourceZone, ZoneId zone) {
        ICalDate until = rrule.getUntil();
        if (until != null) {
            DateTimeComponents raw = until.getRawComponents();
            if (raw.hasTime()) {
                return LocalDateTime.of(LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate()),
                                LocalTime.of(raw.getHour(), raw.getMinute(), raw.getSecond()))
                        .atZone(sourceZone(raw, null, zone)).withZoneSameInstant(zone).toLocalDate();
            }
            return LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate());
        }
        Integer count = rrule.getCount();
        if (count == null || count < 1) return null;
        // Native recurrence has no COUNT: use the last occurrence's date as UNTIL.
        DateIterator iterator = rrule.getDateIterator(start, java.util.TimeZone.getTimeZone(sourceZone));
        Instant last = null;
        for (int i = 0; i < count && i < MAX_OCCURRENCES && iterator.hasNext(); i++) {
            last = iterator.next().toInstant();
        }
        return last != null ? last.atZone(zone).toLocalDate() : null;
    }

    private Integer durationMinutes(VEvent event) {
        Long millis = null;
        if (event.getDuration() != null && event.getDuration().getValue() != null) {
            millis = event.getDuration().getValue().toMillis();
        } else if (event.getDateEnd() != null && event.getDateEnd().getValue() != null) {
            millis = event.getDateEnd().getValue().getTime() - event.getDateStart().getValue().getTime();
        }
        if (millis == null || millis <= 0) return null;
        return (int) (millis / 60_000);
    }

    /** The zone the wall-clock fields of {@code raw} are expressed in: TZID param, UTC marker, else household. */
    private ZoneId sourceZone(DateTimeComponents raw, String tzid, ZoneId fallback) {
        if (tzid != null && !tzid.isBlank()) {
            try {
                return ZoneId.of(tzid);
            } catch (Exception e) {
                log.warn("Unknown TZID {}; treating as household timezone", tzid);
            }
        }
        return raw.isUtc() ? ZoneOffset.UTC : fallback;
    }

    private static String uidOf(VEvent event) {
        return event.getUid() != null && event.getUid().getValue() != null
                ? event.getUid().getValue() : "no-uid-" + Integer.toHexString(System.identityHashCode(event));
    }

    private static RecurrenceFrequency mapFreq(Frequency freq) {
        return switch (freq) {
            case DAILY -> RecurrenceFrequency.DAILY;
            case WEEKLY -> RecurrenceFrequency.WEEKLY;
            case MONTHLY -> RecurrenceFrequency.MONTHLY;
            case YEARLY -> RecurrenceFrequency.YEARLY;
            default -> throw new IllegalArgumentException("Unsupported frequency: " + freq);
        };
    }
}
