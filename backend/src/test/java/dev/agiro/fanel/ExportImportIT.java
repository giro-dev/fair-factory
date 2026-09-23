package dev.agiro.fanel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ExportImportIT {
    @Autowired MockMvc mvc;

    @Test
    void exportsAndReimportsAHousehold() throws Exception {
        String householdId = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa Original","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(householdId, "$.id");

        mvc.perform(post("/api/v1/households/" + id + "/members").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Maria","role":"ADULT","color":"#ff0000"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/households/" + id + "/chores").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Treure les escombraries"}
                                """))
                .andExpect(status().isCreated());

        // A subscription whose fetch fails (connection refused, fast) still exports — with lastError set.
        mvc.perform(post("/api/v1/households/" + id + "/calendar/subscriptions").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Escola\",\"url\":\"http://127.0.0.1:1/cal.ics\"}"))
                .andExpect(status().isCreated());

        String export = mvc.perform(get("/api/v1/households/" + id + "/export").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].name").value("Maria"))
                .andExpect(jsonPath("$.chores[0].title").value("Treure les escombraries"))
                .andExpect(jsonPath("$.calendarSubscriptions[0].name").value("Escola"))
                .andExpect(jsonPath("$.calendarEvents[*].source").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.is("LOCAL"))))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(post("/api/v1/households/import").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(export))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Casa Original"));

        mvc.perform(get("/api/v1/households").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
