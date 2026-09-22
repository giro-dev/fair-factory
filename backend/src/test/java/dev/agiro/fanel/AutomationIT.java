package dev.agiro.fanel;

import dev.agiro.fanel.shared.events.NotificationRequested;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the automation rules REST API and the SHOPPING_REMINDER runner, which publishes a
 * {@link NotificationRequested} domain event consumed by the notifications module.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AutomationIT {
    @Autowired MockMvc mvc;

    /** Test listener imported by the concrete subclasses to capture published domain events. */
    static class NotificationCapturer {
        // Static: listener beans may be proxied in a way that skips instance field initializers.
        static final List<NotificationRequested> events = new CopyOnWriteArrayList<>();

        @ApplicationModuleListener
        void on(NotificationRequested event) {
            events.add(event);
        }
    }

    @Test
    void shoppingReminderRuleLifecyclePublishesNotification() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = com.jayway.jsonpath.JsonPath.read(householdJson, "$.id");
        String base = "/api/v1/households/" + householdId;

        mvc.perform(post(base + "/members").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pare\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated());

        String defaultListJson = mvc.perform(get(base + "/shopping/lists/default")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String listId = com.jayway.jsonpath.JsonPath.read(defaultListJson, "$.id");
        mvc.perform(post(base + "/shopping/lists/" + listId + "/items").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Llet\"}"))
                .andExpect(status().isCreated());

        String ruleJson = mvc.perform(post(base + "/automation/rules").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"SHOPPING_REMINDER\",\"dayOfWeek\":5,\"hour\":18}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.type").value("SHOPPING_REMINDER"))
                .andReturn().getResponse().getContentAsString();
        String ruleId = com.jayway.jsonpath.JsonPath.read(ruleJson, "$.id");

        mvc.perform(get(base + "/automation/rules").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ruleId));

        mvc.perform(put(base + "/automation/rules/" + ruleId).with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        int before = NotificationCapturer.events.size();
        mvc.perform(post(base + "/automation/rules/" + ruleId + "/run").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRunAt").isNotEmpty());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                org.assertj.core.api.Assertions.assertThat(NotificationCapturer.events.size()).isGreaterThan(before));
        org.assertj.core.api.Assertions.assertThat(NotificationCapturer.events.getLast().bodyKey())
                .isEqualTo("notifications.shopping-reminder.body");

        mvc.perform(delete(base + "/automation/rules/" + ruleId).with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());
        mvc.perform(get(base + "/automation/rules").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void menuProposalRunSucceedsWithoutConfiguredModel() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = com.jayway.jsonpath.JsonPath.read(householdJson, "$.id");
        String base = "/api/v1/households/" + householdId;

        String ruleJson = mvc.perform(post(base + "/automation/rules").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MENU_PROPOSAL\",\"dayOfWeek\":7,\"hour\":10}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String ruleId = com.jayway.jsonpath.JsonPath.read(ruleJson, "$.id");

        mvc.perform(post(base + "/automation/rules/" + ruleId + "/run").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastRunAt").isNotEmpty());
    }
}
