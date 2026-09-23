package dev.agiro.fanel;

import dev.agiro.fanel.calendar.infra.IcsFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * External ICS subscriptions: creating one syncs it immediately via the (fake) fetcher, its events
 * are read-only (409 on update/delete), re-syncing replaces rather than duplicates, and deleting
 * the subscription removes its events.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class CalendarSubscriptionIT {
    @Autowired MockMvc mvc;

    /** Imported by the concrete subclasses; serves the fixture (or throws) instead of doing HTTP. */
    @Primary
    static class FakeIcsFetcher implements IcsFetcher {
        static String body;
        static boolean fail;
        static final List<String> requestedUrls = new CopyOnWriteArrayList<>();

        @Override
        public String fetch(String url) {
            requestedUrls.add(url);
            if (fail) throw new IllegalStateException("boom: cannot fetch");
            return body;
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        FakeIcsFetcher.body = IcsImportIT.fixture();
        FakeIcsFetcher.fail = false;
        FakeIcsFetcher.requestedUrls.clear();
    }

    private String createHousehold() throws Exception {
        String json = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test
    void subscriptionLifecycleImportsReadOnlyEvents() throws Exception {
        String householdId = createHousehold();
        String base = "/api/v1/households/" + householdId;

        String subJson = mvc.perform(post(base + "/calendar/subscriptions").with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Escola\",\"url\":\"webcal://school.example/cal.ics\",\"color\":\"#ff8800\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.lastError").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String subId = com.jayway.jsonpath.JsonPath.read(subJson, "$.id");
        org.assertj.core.api.Assertions.assertThat(FakeIcsFetcher.requestedUrls)
                .containsExactly("https://school.example/cal.ics");

        String eventsJson = mvc.perform(get(base + "/calendar?from=2026-10-01&to=2026-10-31")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andReturn().getResponse().getContentAsString();
        var ctx = com.jayway.jsonpath.JsonPath.parse(eventsJson);
        List<String> sources = ctx.read("$[*].source");
        List<String> subIds = ctx.read("$[*].subscriptionId");
        org.assertj.core.api.Assertions.assertThat(sources).containsOnly("ICS");
        org.assertj.core.api.Assertions.assertThat(subIds).containsOnly(subId);

        String eventId = ctx.read("$[0].id");
        mvc.perform(patch(base + "/calendar/" + eventId).with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Nope\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(base + "/calendar/" + eventId).with(httpBasic("admin", "admin")))
                .andExpect(status().isConflict());

        // Re-sync replaces the events instead of duplicating them.
        mvc.perform(post(base + "/calendar/subscriptions/" + subId + "/sync").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSyncedAt").isNotEmpty());
        mvc.perform(get(base + "/calendar?from=2026-10-01&to=2026-10-31").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));

        mvc.perform(delete(base + "/calendar/subscriptions/" + subId).with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());
        mvc.perform(get(base + "/calendar?from=2026-10-01&to=2026-10-31").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void failingFetchIsRecordedOnTheSubscription() throws Exception {
        String householdId = createHousehold();
        String base = "/api/v1/households/" + householdId;
        FakeIcsFetcher.fail = true;

        mvc.perform(post(base + "/calendar/subscriptions").with(httpBasic("admin", "admin"))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trencat\",\"url\":\"https://broken.example/cal.ics\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastSyncedAt").isEmpty())
                .andExpect(jsonPath("$.lastError").isNotEmpty());
    }
}
