package dev.agiro.fanel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One-off ICS upload: every VEVENT becomes a local, editable event. Simple recurrences stay native;
 * complex ones (BYDAY, EXDATE) arrive pre-expanded as single events.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class IcsImportIT {
    @Autowired MockMvc mvc;

    static String fixture() throws Exception {
        try (var in = IcsImportIT.class.getResourceAsStream("/ics/sample.ics")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void importingAnIcsFileCreatesLocalEditableEvents() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = com.jayway.jsonpath.JsonPath.read(householdJson, "$.id");
        String base = "/api/v1/households/" + householdId;

        // 9 ParsedEvents: single + all-day + 1 native weekly + 4 BYDAY + 2 (weekly minus EXDATE)
        mvc.perform(post(base + "/calendar/import").with(httpBasic("admin", "admin"))
                        .contentType("text/calendar")
                        .content(fixture()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(9));

        String eventsJson = mvc.perform(get(base + "/calendar?from=2026-10-01&to=2026-10-31")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                // 12 October occurrences: 1 timed + 1 all-day + 4 native-weekly + 4 BYDAY + 2 EXDATE series
                .andExpect(jsonPath("$.length()").value(12))
                .andReturn().getResponse().getContentAsString();

        var ctx = com.jayway.jsonpath.JsonPath.parse(eventsJson);

        java.util.List<String> bydayDates = ctx.read("$[?(@.title=='Anglès')].date");
        org.assertj.core.api.Assertions.assertThat(bydayDates)
                .containsExactly("2026-10-05", "2026-10-07", "2026-10-12", "2026-10-14");

        java.util.List<String> exdateDates = ctx.read("$[?(@.title=='Sopar família')].date");
        org.assertj.core.api.Assertions.assertThat(exdateDates)
                .containsExactly("2026-10-05", "2026-10-19");

        java.util.List<String> weeklyDates = ctx.read("$[?(@.title=='Pilates')].date");
        org.assertj.core.api.Assertions.assertThat(weeklyDates)
                .containsExactly("2026-10-07", "2026-10-14", "2026-10-21", "2026-10-28");

        java.util.List<String> times = ctx.read("$[?(@.title=='Dentista')].time");
        java.util.List<Object> durations = ctx.read("$[?(@.title=='Dentista')].durationMinutes");
        org.assertj.core.api.Assertions.assertThat(times.get(0)).startsWith("10:00");
        org.assertj.core.api.Assertions.assertThat(durations).containsExactly(90);

        java.util.List<Object> allDayTimes = ctx.read("$[?(@.title=='Festa major')].time");
        org.assertj.core.api.Assertions.assertThat(allDayTimes).containsExactly((Object) null);

        java.util.List<String> sources = ctx.read("$[*].source");
        org.assertj.core.api.Assertions.assertThat(sources).containsOnly("LOCAL");

        // Imported events are local: updating one works.
        String eventId = ctx.read("$[?(@.title=='Dentista')].id", java.util.List.class).get(0).toString();
        mvc.perform(patch(base + "/calendar/" + eventId).with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Dentista (reprogramat)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Dentista (reprogramat)"));
    }
}
