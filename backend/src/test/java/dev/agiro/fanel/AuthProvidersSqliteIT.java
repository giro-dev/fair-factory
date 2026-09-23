package dev.agiro.fanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Without OIDC configured there are no external providers and no authorization endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("sqlite")
class AuthProvidersSqliteIT {
    @Autowired MockMvc mvc;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("FANEL_SQLITE_PATH", () -> tempDir.resolve("fanel.db").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fanel.db"));
    }

    @Test
    void providersIsEmpty() throws Exception {
        mvc.perform(get("/api/v1/auth/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void noAuthorizationRedirectExists() throws Exception {
        // Whatever the SPA fallback answers, it must not be a redirect to an IdP.
        mvc.perform(get("/oauth2/authorization/sso"))
                .andExpect(header().doesNotExist("Location"));
    }
}
