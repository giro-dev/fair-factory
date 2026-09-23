package dev.agiro.fanel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Asserts the OpenAPI spec is served and regenerates the committed {@code docs/openapi.json}
 * (the contract for the generated TypeScript client). Set {@code -Dfanel.openapi.skipWrite=true}
 * to assert without writing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlite")
class OpenApiSpecSqliteIT {
    @Autowired MockMvc mvc;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("FANEL_SQLITE_PATH", () -> tempDir.resolve("fanel.db").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fanel.db"));
    }

    @Test
    void servesAndRegeneratesTheOpenApiSpec() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/households/{householdId}/automation/rules']").exists())
                .andReturn();

        if (Boolean.getBoolean("fanel.openapi.skipWrite")) return;

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        String pretty = mapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(mapper.readTree(result.getResponse().getContentAsString()));
        Path output = repoRoot().resolve("docs/openapi.json");
        Files.writeString(output, pretty + "\n", StandardCharsets.UTF_8);
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        while (dir != null && !(Files.exists(dir.resolve("pom.xml")) && Files.isDirectory(dir.resolve("docs")))) {
            dir = dir.getParent();
        }
        if (dir == null) throw new IllegalStateException("Could not locate the repository root");
        return dir;
    }
}
