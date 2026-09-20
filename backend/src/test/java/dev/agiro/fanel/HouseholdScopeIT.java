package dev.agiro.fanel;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Household scoping: a member may only see and touch its own household. The global admin keeps
 * cross-household access; members get a filtered view and 403 everywhere else.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class HouseholdScopeIT {
    @Autowired MockMvc mvc;

    @Test
    void meResolvesTheAuthenticatedIdentity() throws Exception {
        String householdId = createHousehold("Identitat");
        addMember(householdId, "Marta", "ADULT", "marta", "secret123");

        mvc.perform(get("/api/v1/me").with(httpBasic("marta", "secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("MEMBER"))
                .andExpect(jsonPath("$.member.username").value("marta"))
                .andExpect(jsonPath("$.household.id").value(householdId));

        mvc.perform(get("/api/v1/me").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("ADMIN"));

        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void membersOnlySeeTheirOwnHousehold() throws Exception {
        String own = createHousehold("La seva");
        String other = createHousehold("D'altri");
        addMember(own, "Jordi", "ADULT", "jordi", "secret123");

        String list = mvc.perform(get("/api/v1/households").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(JsonPath.<java.util.List<String>>read(list, "$[*].id"))
                .containsExactly(own);

        mvc.perform(get("/api/v1/households/" + other).with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/households/" + other + "/members").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/households/" + other + "/recipes").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/households/" + other + "/calendar").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/households/" + other + "/export").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/events?household=" + other).with(httpBasic("jordi", "secret123")))
                .andExpect(status().isForbidden());

        // ...but can still operate on their own household.
        mvc.perform(get("/api/v1/households/" + own + "/members").with(httpBasic("jordi", "secret123")))
                .andExpect(status().isOk());
    }

    @Test
    void membersCannotCreateOrImportHouseholds() throws Exception {
        String own = createHousehold("Limitada");
        addMember(own, "Nora", "ADULT", "nora", "secret123");

        mvc.perform(post("/api/v1/households").with(httpBasic("nora", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nova","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/households/import").with(httpBasic("nora", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"household":{"name":"Importada","locale":"ca","timezone":"Europe/Madrid"},
                                 "members":[],"mealPlans":[],"shoppingLists":[],"calendarEvents":[],"chores":[]}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void membersCannotSetPinsOfOthers() throws Exception {
        String householdId = createHousehold("PINs");
        String adultId = addMember(householdId, "Adult", "ADULT", "adultp", "secret123");
        String otherId = addMember(householdId, "Altre", "ADULT", "altrep", "secret123");

        // Adults cannot set another member's PIN, but can set their own.
        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + otherId + "/pin")
                        .with(httpBasic("adultp", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + adultId + "/pin")
                        .with(httpBasic("adultp", "secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPin").value(true));

        // Admins can set anyone's PIN.
        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + otherId + "/pin")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"9999\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void assistantIsUsableByMembersAndByAdminWithAPickedMember() throws Exception {
        String own = createHousehold("Llar assistent");
        String other = createHousehold("Llar aliena");
        String memberId = addMember(own, "Pau", "ADULT", "pau", "secret123");

        // Members list agents of their own household; other households are out of scope.
        mvc.perform(get("/api/v1/households/" + own + "/assistant/agents").with(httpBasic("pau", "secret123")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/households/" + other + "/assistant/agents").with(httpBasic("pau", "secret123")))
                .andExpect(status().isForbidden());

        // The global admin can list agents; a memberId from another household is rejected.
        mvc.perform(get("/api/v1/households/" + own + "/assistant/agents").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/households/" + other + "/assistant/chat").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"hola\",\"memberId\":\"" + memberId + "\"}"))
                .andExpect(status().isNotFound());
    }

    private String createHousehold(String name) throws Exception {
        String response = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","locale":"ca","timezone":"Europe/Madrid"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String addMember(String householdId, String name, String role, String username, String password) throws Exception {
        String response = mvc.perform(post("/api/v1/households/" + householdId + "/members").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","role":"%s"}
                                """.formatted(name, role)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberId = JsonPath.read(response, "$.id");
        if (username != null) {
            mvc.perform(put("/api/v1/households/" + householdId + "/members/" + memberId + "/credentials")
                            .with(httpBasic("admin", "admin"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"%s","password":"%s"}
                                    """.formatted(username, password)))
                    .andExpect(status().isOk());
        }
        return memberId;
    }
}
