package dev.agiro.fanel.export.domain;

import tools.jackson.databind.ObjectMapper;
import dev.agiro.fanel.export.api.HouseholdExport;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.zone.ZoneRulesException;

/**
 * Once a day, at {@code fanel.backups.hour} in each household's own timezone, writes the household
 * export as pretty JSON to {@code <fanel.backups.dir>/<householdId>/<yyyy-MM-dd>.json} and prunes
 * backups older than {@code fanel.backups.keep-days}. Disabled when the directory is not configured.
 */
@Component
public class BackupScheduler {
    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final HouseholdApi households;
    private final HouseholdExportService exportService;
    private final ObjectMapper mapper;
    private final String backupDir;
    private final int backupHour;
    private final int keepDays;

    public BackupScheduler(HouseholdApi households, HouseholdExportService exportService, ObjectMapper mapper,
                           @Value("${fanel.backups.dir:}") String backupDir,
                           @Value("${fanel.backups.hour:3}") int backupHour,
                           @Value("${fanel.backups.keep-days:30}") int keepDays) {
        this.households = households;
        this.exportService = exportService;
        this.mapper = mapper;
        this.backupDir = backupDir;
        this.backupHour = backupHour;
        this.keepDays = keepDays;
    }

    @Scheduled(cron = "0 0 * * * *")
    public void runDueBackups() {
        if (backupDir.isBlank()) return;
        Instant now = Instant.now();
        for (HouseholdDto household : households.list()) {
            ZonedDateTime local;
            try {
                local = now.atZone(ZoneId.of(household.timezone()));
            } catch (ZoneRulesException e) {
                log.warn("Unknown timezone {} for household {}", household.timezone(), household.id());
                continue;
            }
            if (local.getHour() != backupHour) continue;
            try {
                backup(household, local.toLocalDate());
            } catch (Exception e) {
                log.warn("Backup failed for household {}: {}", household.id(), e.getMessage());
            }
        }
    }

    void backup(HouseholdDto household, LocalDate today) throws IOException {
        Path dir = Paths.get(backupDir).resolve(household.id().toString());
        Files.createDirectories(dir);
        Path file = dir.resolve(today + ".json");
        if (!Files.exists(file)) {
            HouseholdExport payload = exportService.build(household.id());
            Files.writeString(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
        }
        prune(dir, today.minusDays(keepDays));
    }

    private void prune(Path dir, LocalDate cutoff) throws IOException {
        try (var stream = Files.list(dir)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                try {
                    if (LocalDate.parse(name.substring(0, name.length() - 5)).isBefore(cutoff)) {
                        Files.delete(file);
                    }
                } catch (DateTimeParseException ignored) {
                    // Not a dated backup file; leave it alone.
                }
            }
        }
    }
}
