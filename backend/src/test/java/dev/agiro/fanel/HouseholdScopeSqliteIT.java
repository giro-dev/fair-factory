package dev.agiro.fanel;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

@ActiveProfiles("sqlite")
class HouseholdScopeSqliteIT extends HouseholdScopeIT {
    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void sqliteProperties(DynamicPropertyRegistry registry) {
        registry.add("FANEL_SQLITE_PATH", () -> tempDir.resolve("fanel.db").toString());
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + tempDir.resolve("fanel.db"));
    }
}
