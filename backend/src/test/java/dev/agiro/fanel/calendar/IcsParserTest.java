package dev.agiro.fanel.calendar;

import dev.agiro.fanel.calendar.api.RecurrenceFrequency;
import dev.agiro.fanel.calendar.infra.IcsParser;
import dev.agiro.fanel.calendar.infra.IcsParser.ParsedEvent;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IcsParserTest {
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate FROM = LocalDate.of(2026, 10, 1);
    private static final LocalDate TO = LocalDate.of(2026, 10, 31);

    private final IcsParser parser = new IcsParser();

    private String fixture() throws Exception {
        try (var in = getClass().getResourceAsStream("/ics/sample.ics")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesTimedAllDayAndRecurrenceKinds() throws Exception {
        List<ParsedEvent> events = parser.parse(fixture(), MADRID, FROM, TO);

        ParsedEvent single = byUid(events, "evt-single@fanel.test").get(0);
        assertThat(single.date()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(single.time()).isEqualTo(LocalTime.of(10, 0));
        assertThat(single.durationMinutes()).isEqualTo(90);
        assertThat(single.freq()).isNull();

        ParsedEvent allDay = byUid(events, "evt-allday@fanel.test").get(0);
        assertThat(allDay.date()).isEqualTo(LocalDate.of(2026, 10, 6));
        assertThat(allDay.time()).isNull();
        assertThat(allDay.durationMinutes()).isNull();
    }

    @Test
    void simpleRruleMapsToNativeRecurrence() throws Exception {
        List<ParsedEvent> events = parser.parse(fixture(), MADRID, FROM, TO);
        List<ParsedEvent> weekly = byUid(events, "evt-weekly@fanel.test");
        assertThat(weekly).hasSize(1);
        ParsedEvent e = weekly.get(0);
        assertThat(e.freq()).isEqualTo(RecurrenceFrequency.WEEKLY);
        assertThat(e.interval()).isEqualTo(1);
        assertThat(e.until()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(e.date()).isEqualTo(LocalDate.of(2026, 10, 7));
        assertThat(e.time()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void bydayRruleIsExpandedIntoOccurrences() throws Exception {
        List<ParsedEvent> events = parser.parse(fixture(), MADRID, FROM, TO);
        List<ParsedEvent> byday = events.stream()
                .filter(e -> e.uid().startsWith("evt-byday@fanel.test")).toList();
        assertThat(byday).hasSize(4);
        assertThat(byday.stream().map(ParsedEvent::date).toList())
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7),
                        LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14));
        assertThat(byday).allSatisfy(e -> {
            assertThat(e.freq()).isNull();
            assertThat(e.time()).isEqualTo(LocalTime.of(9, 0));
        });
    }

    @Test
    void exdateRemovesAnOccurrence() throws Exception {
        List<ParsedEvent> events = parser.parse(fixture(), MADRID, FROM, TO);
        List<ParsedEvent> exdated = events.stream()
                .filter(e -> e.uid().startsWith("evt-exdate@fanel.test")).toList();
        assertThat(exdated.stream().map(ParsedEvent::date).toList())
                .containsExactly(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 19));
    }

    @Test
    void invalidDocumentThrows() {
        assertThatThrownBy(() -> parser.parse("this is not ics", MADRID, FROM, TO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static List<ParsedEvent> byUid(List<ParsedEvent> events, String uid) {
        return events.stream().filter(e -> e.uid().equals(uid)).toList();
    }
}
