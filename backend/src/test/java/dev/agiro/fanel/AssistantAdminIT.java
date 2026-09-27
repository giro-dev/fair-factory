package dev.agiro.fanel;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the global admin endpoints of {@code /api/v1/admin/assistant}: agent configuration
 * overrides (persisted and reloaded in place), provider discovery and access control.
 * Test profiles configure no OpenAI/Anthropic API keys, so those providers are unavailable.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class AssistantAdminIT {
    private static final String BASE = "/api/v1/admin/assistant";

    @Autowired MockMvc mvc;

    @Test
    void agentConfigOverridesArePersistedAndCanBeReset() throws Exception {
        // Catalogue defaults point to openai, which has no key in tests: nothing registered.
        mvc.perform(get(BASE + "/agents").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='orchestrator')].orchestrator").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.id=='orchestrator')].overridden").value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$[?(@.id=='menu-planner')].provider").value(org.hamcrest.Matchers.hasItem("openai")))
                .andExpect(jsonPath("$[*].available").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(false))));

        mvc.perform(put(BASE + "/agents/menu-planner").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ollama\",\"model\":\"llama3.2\",\"temperature\":0.4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overridden").value(true))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.model").value("llama3.2"))
                .andExpect(jsonPath("$.temperature").value(0.4))
                .andExpect(jsonPath("$.defaults.provider").value("openai"));

        mvc.perform(get(BASE + "/agents").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='menu-planner')].overridden").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.id=='menu-planner')].provider").value(org.hamcrest.Matchers.hasItem("ollama")));

        mvc.perform(delete(BASE + "/agents/menu-planner").with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());

        mvc.perform(get(BASE + "/agents").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='menu-planner')].overridden").value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath("$[?(@.id=='menu-planner')].provider").value(org.hamcrest.Matchers.hasItem("openai")));
    }

    @Test
    void invalidProviderIsRejectedAndUnknownAgentIs404() throws Exception {
        mvc.perform(put(BASE + "/agents/menu-planner").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"foo\",\"model\":\"x\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(put(BASE + "/agents/nope").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ollama\",\"model\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void providersReturnsTheThreeCatalogueProviders() throws Exception {
        String json = mvc.perform(get(BASE + "/providers").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].models").isArray())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> providers = JsonPath.read(json, "$[*].provider");
        org.assertj.core.api.Assertions.assertThat(providers)
                .containsExactlyInAnyOrder("openai", "ollama", "anthropic");
        // No API keys configured in tests: openai and anthropic are unavailable and not called.
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<Boolean>) JsonPath.read(json, "$[?(@.provider=='openai')].available"))
                .containsExactly(false);
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<Boolean>) JsonPath.read(json, "$[?(@.provider=='anthropic')].available"))
                .containsExactly(false);
        org.assertj.core.api.Assertions.assertThat(
                        (java.util.List<String>) JsonPath.read(json, "$[?(@.provider=='openai')].error"))
                .containsExactly((String) null);
    }

    @Test
    void householdMembersGetForbidden() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa\",\"locale\":\"ca\",\"timezone\":\"Europe/Madrid\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = JsonPath.read(householdJson, "$.id");

        String memberJson = mvc.perform(post("/api/v1/households/" + householdId + "/members")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pare\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String memberId = JsonPath.read(memberJson, "$.id");
        mvc.perform(put("/api/v1/households/" + householdId + "/members/" + memberId + "/credentials")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"pare\",\"password\":\"secret123\"}"))
                .andExpect(status().isOk());

        mvc.perform(get(BASE + "/agents").with(httpBasic("pare", "secret123")))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/providers").with(httpBasic("pare", "secret123")))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatWithoutAgentIdUsesTheOrchestratorAnd404sWithoutModel() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Casa\",\"locale\":\"ca\",\"timezone\":\"Europe/Madrid\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = JsonPath.read(householdJson, "$.id");

        // OpenAI has no key in tests, so no agent (including the orchestrator) is registered.
        mvc.perform(get("/api/v1/households/" + householdId + "/assistant/agents")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mvc.perform(post("/api/v1/households/" + householdId + "/assistant/chat")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"no-such-agent\",\"message\":\"Hola\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("no-such-agent")));

        mvc.perform(post("/api/v1/households/" + householdId + "/assistant/chat")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hola\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("orchestrator")));
    }
}
