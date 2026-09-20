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

/**
 * First-run flow: while no household exists, {@code /api/v1/setup} is open and creates the first
 * household plus its admin member with login credentials in one shot. Single test method because
 * the whole class shares one database and emptiness can only be asserted once.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class SetupIT {
    @Autowired MockMvc mvc;

    @Test
    void firstRunSetupCreatesHouseholdAndAdminMember() throws Exception {
        mvc.perform(get("/api/v1/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true));

        // No authentication needed while the instance is empty.
        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"La nostra llar","memberName":"Pare",
                                 "username":"pare","password":"secret123",
                                 "locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.username").value("pare"))
                .andExpect(jsonPath("$.hasCredentials").value(true));

        // The new member can log in and /me resolves member + household.
        mvc.perform(get("/api/v1/me").with(httpBasic("pare", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.member.username").value("pare"))
                .andExpect(jsonPath("$.member.role").value("ADMIN"))
                .andExpect(jsonPath("$.household.name").value("La nostra llar"));

        // Setup is closed afterwards.
        mvc.perform(get("/api/v1/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(false));

        mvc.perform(post("/api/v1/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"Intrus","memberName":"Intrus",
                                 "username":"intrus","password":"secret123"}
                                """))
                .andExpect(status().isForbidden());
    }
}
