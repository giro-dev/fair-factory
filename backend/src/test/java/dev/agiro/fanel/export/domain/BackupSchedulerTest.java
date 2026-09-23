package dev.agiro.fanel.export.domain;

import tools.jackson.databind.json.JsonMapper;
import dev.agiro.fanel.export.api.HouseholdExport;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackupSchedulerTest {
    @TempDir
    Path dir;

    private final HouseholdApi households = mock(HouseholdApi.class);
    private final HouseholdExportService exportService = mock(HouseholdExportService.class);
    private BackupScheduler scheduler;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        scheduler = new BackupScheduler(households, exportService,
                JsonMapper.builder().findAndAddModules().build(), dir.toString(), 3, 30);
    }

    private HouseholdDto household() {
        return new HouseholdDto(UUID.randomUUID(), "Llar", "ca", "Europe/Madrid", Instant.now());
    }

    @Test
    void writesTheExportAsJsonAndIsIdempotentWithinADay() throws Exception {
        HouseholdDto household = household();
        HouseholdExport payload = new HouseholdExport(household, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
        when(exportService.build(household.id())).thenReturn(payload);

        LocalDate today = LocalDate.of(2026, 9, 23);
        scheduler.backup(household, today);

        Path file = dir.resolve(household.id().toString()).resolve("2026-09-23.json");
        assertThat(file).exists();
        var tree = JsonMapper.builder().findAndAddModules().build().readTree(Files.readString(file));
        assertThat(tree.at("/household/id").asText()).isEqualTo(household.id().toString());

        // Second call on the same day must not rebuild the export.
        scheduler.backup(household, today);
        verify(exportService, times(1)).build(household.id());
    }

    @Test
    void prunesBackupsOlderThanKeepDays() throws Exception {
        HouseholdDto household = household();
        when(exportService.build(household.id())).thenReturn(
                new HouseholdExport(household, List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of()));

        Path householdDir = Files.createDirectories(dir.resolve(household.id().toString()));
        Path old = householdDir.resolve("2026-08-14.json"); // 40 days before
        Path recent = householdDir.resolve("2026-09-13.json"); // 10 days before
        Files.writeString(old, "{}");
        Files.writeString(recent, "{}");

        scheduler.backup(household, LocalDate.of(2026, 9, 23));

        assertThat(old).doesNotExist();
        assertThat(recent).exists();
        assertThat(householdDir.resolve("2026-09-23.json")).exists();
    }
}
