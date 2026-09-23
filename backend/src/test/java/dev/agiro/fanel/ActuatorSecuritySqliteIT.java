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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actuator endpoints: health stays public, prometheus requires authentication when enabled. */
@SpringBootTest(properties = "management.endpoint.prometheus.access=read-only")
@AutoConfigureMockMvc
@ActiveProfiles("sqlite")
class ActuatorSecuritySqliteIT {
    @Autowired MockMvc mvc;

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("FANEL_SQLITE_PATH", () -> tempDir.resolve("fanel.db").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fanel.db"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void prometheusRequiresAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test
    void prometheusServesMetricsToAuthenticatedUsers() throws Exception {
        mvc.perform(get("/actuator/prometheus").with(httpBasic("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("jvm_")));
    }
}
