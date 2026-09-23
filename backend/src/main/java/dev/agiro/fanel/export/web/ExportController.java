package dev.agiro.fanel.export.web;

import dev.agiro.fanel.export.api.HouseholdExport;
import dev.agiro.fanel.export.domain.HouseholdExportService;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.web.ForbiddenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Full-household JSON export/import, used for backups and moving a household between instances. */
@RestController
@RequestMapping("/api/v1/households")
public class ExportController {
    private final HouseholdExportService exportService;
    private final CurrentAccess access;

    public ExportController(HouseholdExportService exportService, CurrentAccess access) {
        this.exportService = exportService;
        this.access = access;
    }

    @GetMapping("/{householdId}/export")
    public HouseholdExport export(@PathVariable UUID householdId) {
        if (!access.hasFullAccess()) throw new ForbiddenException("Only admins can export a household");
        return exportService.build(householdId);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDto importHousehold(@RequestBody HouseholdExport payload) {
        if (!access.hasGlobalAccess()) throw new ForbiddenException("Only the global admin can import households");
        return exportService.restore(payload);
    }
}
