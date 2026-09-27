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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class ShoppingFeaturesIT {
    @Autowired MockMvc mvc;

    @Test
    void multipleListsAndItemAttributesAndClearPurchased() throws Exception {
        String householdJson = mvc.perform(post("/api/v1/households").with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Casa Compra","locale":"ca","timezone":"Europe/Madrid"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String householdId = com.jayway.jsonpath.JsonPath.read(householdJson, "$.id");

        // No default list until one is created explicitly
        mvc.perform(get("/api/v1/households/" + householdId + "/shopping/lists/default")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isNotFound());

        String defaultListJson = mvc.perform(post("/api/v1/households/" + householdId + "/shopping/lists")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Compra"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String defaultListId = com.jayway.jsonpath.JsonPath.read(defaultListJson, "$.id");

        // Default list resolves to the first one once it exists
        mvc.perform(get("/api/v1/households/" + householdId + "/shopping/lists/default")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Compra"));

        // Create second list "Farmàcia"
        String pharmaListJson = mvc.perform(post("/api/v1/households/" + householdId + "/shopping/lists")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Farmàcia"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Farmàcia"))
                .andReturn().getResponse().getContentAsString();
        String pharmaListId = com.jayway.jsonpath.JsonPath.read(pharmaListJson, "$.id");

        // List lists returns 2
        mvc.perform(get("/api/v1/households/" + householdId + "/shopping/lists")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Update list name
        mvc.perform(put("/api/v1/households/" + householdId + "/shopping/lists/" + pharmaListId)
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Parafarmàcia"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Parafarmàcia"));

        // Add item to default list with quantity, unit, category, recurring = true
        String recurringItemJson = mvc.perform(post("/api/v1/households/" + householdId + "/shopping/lists/" + defaultListId + "/items")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Llet sencera",
                                  "quantity": 2.5,
                                  "unit": "l",
                                  "category": "Lactis",
                                  "recurring": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Llet sencera"))
                .andExpect(jsonPath("$.quantity").value(2.5))
                .andExpect(jsonPath("$.unit").value("l"))
                .andExpect(jsonPath("$.category").value("Lactis"))
                .andExpect(jsonPath("$.recurring").value(true))
                .andExpect(jsonPath("$.done").value(false))
                .andReturn().getResponse().getContentAsString();
        String recurringItemId = com.jayway.jsonpath.JsonPath.read(recurringItemJson, "$.id");

        // Add non-recurring item
        String nonRecurringItemJson = mvc.perform(post("/api/v1/households/" + householdId + "/shopping/lists/" + defaultListId + "/items")
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Xocolata",
                                  "quantity": 1.0,
                                  "unit": "rajola",
                                  "category": "Dolços",
                                  "recurring": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Xocolata"))
                .andExpect(jsonPath("$.recurring").value(false))
                .andReturn().getResponse().getContentAsString();
        String nonRecurringItemId = com.jayway.jsonpath.JsonPath.read(nonRecurringItemJson, "$.id");

        // Mark both as done
        mvc.perform(patch("/api/v1/households/" + householdId + "/shopping/items/" + recurringItemId)
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        mvc.perform(patch("/api/v1/households/" + householdId + "/shopping/items/" + nonRecurringItemId)
                        .with(httpBasic("admin", "admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"done": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(true));

        // Clear purchased: non-recurring item is deleted, recurring item is reset to done=false
        mvc.perform(post("/api/v1/households/" + householdId + "/shopping/lists/" + defaultListId + "/clear-purchased")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(2));

        mvc.perform(get("/api/v1/households/" + householdId + "/shopping/lists/" + defaultListId)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(recurringItemId))
                .andExpect(jsonPath("$.items[0].done").value(false))
                .andExpect(jsonPath("$.items[0].recurring").value(true));

        // Delete list
        mvc.perform(delete("/api/v1/households/" + householdId + "/shopping/lists/" + pharmaListId)
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/households/" + householdId + "/shopping/lists")
                        .with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }
}
