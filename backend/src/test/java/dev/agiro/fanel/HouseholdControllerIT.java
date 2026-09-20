package dev.agiro.fanel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;

@SpringBootTest
@AutoConfigureMockMvc
abstract class HouseholdControllerIT {
    @Autowired MockMvc mvc;

    @Test
    void createsAndListsHousehold() throws Exception {
        mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Casa"));

        mvc.perform(get("/api/v1/households").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Casa"));
    }

    @Test
    void adultCanLoginWithOwnCredentialsOnceSet() throws Exception {
        String householdId = createHousehold("Credencials");
        String memberId = addMember(householdId, "Mireia", "ADULT");

        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + memberId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"mireia","password":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("mireia"));

        mvc.perform(get("/api/v1/households").with(httpBasic("mireia", "secret123")))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/households").with(httpBasic("mireia", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void childrenCanAlsoHaveLoginCredentials() throws Exception {
        String householdId = createHousehold("Amb credencials");
        String memberId = addMember(householdId, "Nen", "CHILD");

        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + memberId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"nen","password":"secret123"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/households").with(httpBasic("nen", "secret123")))
                .andExpect(status().isOk());
    }

    @Test
    void usernameMustBeUnique() throws Exception {
        String householdId = createHousehold("Duplicats");
        String firstId = addMember(householdId, "Adult 1", "ADULT");
        String secondId = addMember(householdId, "Adult 2", "ADULT");

        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + firstId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"duplicat","password":"secret123"}
                                """))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + secondId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"duplicat","password":"secret456"}
                                """))
                .andExpect(status().isConflict());
    }

    private String createHousehold(String name) throws Exception {
        String response = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","locale":"ca","timezone":"Europe/Madrid"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    private String addMember(String householdId, String name, String role) throws Exception {
        String response = mvc.perform(post("/api/v1/households/" + householdId + "/members").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","role":"%s"}
                                """.formatted(name, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }
}
