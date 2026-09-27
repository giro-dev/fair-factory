package dev.agiro.fanel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class RecipeSearchIT {
    @Autowired MockMvc mvc;

    private String household() throws Exception {
        String json = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Receptari\",\"locale\":\"ca\",\"timezone\":\"Europe/Madrid\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    private String createRecipe(String householdId, String json) throws Exception {
        String response = mvc.perform(post("/api/v1/households/" + householdId + "/recipes")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(response, "$.id");
    }

    @Test
    void searchFiltersTagsIngredientsAndPaginates() throws Exception {
        String householdId = household();
        createRecipe(householdId, """
                {"name":"Canelons de Nadal","servings":6,"description":"Pasta farcida","tags":["festiu","pasta"],
                 "ingredients":[{"name":"canelons","quantity":12,"unit":"unitat"},{"name":"carn picada","quantity":400,"unit":"g"}],
                 "steps":["Farcir","Gratinar"]}
                """);
        createRecipe(householdId, """
                {"name":"Crema de carbassa","servings":4,"description":"Crema suau","tags":["sopa","vegetariana"],
                 "ingredients":[{"name":"carbassa","quantity":1,"unit":"kg"},{"name":"ceba","quantity":1,"unit":"unitat"}]}
                """);
        createRecipe(householdId, """
                {"name":"Truita de patates","servings":4,"tags":["sopar"],
                 "ingredients":[{"name":"patata","quantity":500,"unit":"g"},{"name":"ou","quantity":6,"unit":"unitat"}]}
                """);

        // Free-text OR group over name/description (what the UI sends for the search box)
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"filter":{"or":[
                                  {"field":"name","operator":"LIKE","value":"canelons"},
                                  {"field":"description","operator":"LIKE","value":"canelons"}]}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(1))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Canelons de Nadal"));

        // Tag filter
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search?tag=sopa")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].name").value("Crema de carbassa"));

        // Ingredient filter (diacritic-insensitive is not required; substring is)
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search?ingredient=carbassa")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)));

        // Empty filter lists everything, paginated and sorted
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search?size=2&sort=name&direction=asc")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\":{\"and\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.content[0].name").value("Canelons de Nadal"));

        // Second page
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search?size=2&page=1&sort=name")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\":{\"and\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.hasMore").value(false));

        // Unknown field -> 400
        mvc.perform(post("/api/v1/households/" + householdId + "/recipes/search")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\":{\"field\":\"nope\",\"operator\":\"EQ\",\"value\":\"x\"}}"))
                .andExpect(status().isBadRequest());

        // Household isolation: another household sees nothing
        String other = household();
        mvc.perform(post("/api/v1/households/" + other + "/recipes/search")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filter\":{\"and\":[]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHits").value(0));
    }

    @Test
    void updateReplacesRecipeFields() throws Exception {
        String householdId = household();
        String id = createRecipe(householdId, """
                {"name":"Vella","servings":2,"tags":["a"],
                 "ingredients":[{"name":"farina","quantity":100,"unit":"g"}]}
                """);

        mvc.perform(put("/api/v1/households/" + householdId + "/recipes/" + id)
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nova","servings":8,"description":"desc","notes":"notes",
                                 "tags":["b","c"],"steps":["pas 1","pas 2"],
                                 "ingredients":[{"name":"sucre","quantity":50,"unit":"g","category":"despensa"},
                                                {"name":"llet","quantity":250,"unit":"ml"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nova"))
                .andExpect(jsonPath("$.servings").value(8))
                .andExpect(jsonPath("$.tags", hasSize(2)))
                .andExpect(jsonPath("$.steps", hasSize(2)))
                .andExpect(jsonPath("$.ingredients", hasSize(2)))
                .andExpect(jsonPath("$.ingredients[0].name").value("sucre"));

        // Old ingredients are gone, not duplicated
        mvc.perform(get("/api/v1/households/" + householdId + "/recipes/" + id)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingredients", hasSize(2)));

        // Update of a recipe from another household -> 404
        String other = household();
        mvc.perform(put("/api/v1/households/" + other + "/recipes/" + id)
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"servings\":1}"))
                .andExpect(status().isNotFound());
    }
}
