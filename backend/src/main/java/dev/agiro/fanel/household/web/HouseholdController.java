package dev.agiro.fanel.household.web;

import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.household.api.MemberRole;
import dev.agiro.fanel.household.domain.HouseholdService;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.web.ForbiddenException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households")
public class HouseholdController {
    private final HouseholdService service;
    private final CurrentAccess access;

    public HouseholdController(HouseholdService service, CurrentAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public List<HouseholdDto> list() {
        if (access.hasGlobalAccess()) {
            return service.list();
        }
        // Members are bound to a single household: they only ever see their own.
        return access.householdId()
                .map(id -> List.of(service.get(id)))
                .orElseGet(List::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HouseholdDto create(@Valid @RequestBody CreateHousehold request) {
        if (!access.hasGlobalAccess()) throw new ForbiddenException("Only the global admin can create households");
        return service.create(request.name(), request.locale(), request.timezone());
    }

    @GetMapping("/{id}")
    public HouseholdDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto addMember(@PathVariable UUID id, @Valid @RequestBody CreateMember request) {
        boolean adultAddingChild = access.isAdult() && request.role() == MemberRole.CHILD;
        if (!access.hasFullAccess() && !adultAddingChild) {
            throw new ForbiddenException("Only admins can add this kind of member");
        }
        MemberDto created = service.addMember(id, request.name(), request.role(), request.color());
        if (adultAddingChild) {
            UUID creatorId = access.memberId().orElseThrow();
            created = service.setMemberGuardians(id, created.id(), List.of(creatorId));
        }
        if (request.pin() != null && !request.pin().isBlank()) {
            created = service.setMemberPin(id, created.id(), request.pin());
        }
        if (request.username() != null && !request.username().isBlank()
                && request.password() != null && !request.password().isBlank()) {
            created = service.setMemberCredentials(id, created.id(), request.username(), request.password());
        }
        return created;
    }

    @GetMapping("/{id}/members")
    public List<MemberDto> listMembers(@PathVariable UUID id) {
        return service.listMembers(id);
    }

    @PutMapping("/{id}/members/{memberId}/pin")
    public MemberDto setPin(@PathVariable UUID id, @PathVariable UUID memberId,
                            @RequestBody SetPin request) {
        requireCanManageMember(id, memberId);
        return service.setMemberPin(id, memberId, request.pin());
    }

    @PostMapping("/{id}/members/{memberId}/verify-pin")
    public Map<String, Boolean> verifyPin(@PathVariable UUID id, @PathVariable UUID memberId,
                                          @RequestBody SetPin request) {
        return Map.of("valid", service.verifyMemberPin(id, memberId, request.pin()));
    }

    @PatchMapping("/{id}/members/{memberId}")
    public MemberDto updateMember(@PathVariable UUID id, @PathVariable UUID memberId,
                                  @RequestBody UpdateMember request) {
        requireCanManageMember(id, memberId);
        return service.updateMember(id, memberId, request.name(), request.color());
    }

    @DeleteMapping("/{id}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMember(@PathVariable UUID id, @PathVariable UUID memberId) {
        requireCanManageMember(id, memberId);
        service.deleteMember(id, memberId);
    }

    @PutMapping("/{id}/members/{memberId}/credentials")
    public MemberDto setCredentials(@PathVariable UUID id, @PathVariable UUID memberId,
                                    @Valid @RequestBody SetCredentials request) {
        requireCanManageMember(id, memberId);
        return service.setMemberCredentials(id, memberId, request.username(), request.password());
    }

    @PutMapping("/{id}/members/{memberId}/role")
    public MemberDto setRole(@PathVariable UUID id, @PathVariable UUID memberId,
                             @Valid @RequestBody SetRole request) {
        if (!access.hasFullAccess()) throw new ForbiddenException("Only admins can change a member's role");
        return service.setMemberRole(id, memberId, request.role());
    }

    @PutMapping("/{id}/members/{childId}/guardians")
    public MemberDto setGuardians(@PathVariable UUID id, @PathVariable UUID childId,
                                  @Valid @RequestBody SetGuardians request) {
        if (!access.hasFullAccess()) throw new ForbiddenException("Only admins can manage guardians");
        return service.setMemberGuardians(id, childId, request.guardianIds());
    }

    /** Admins can manage anyone; adults only themselves or their related children; others only themselves. */
    private void requireCanManageMember(UUID householdId, UUID targetMemberId) {
        if (access.hasFullAccess()) return;
        if (access.memberId().map(id -> id.equals(targetMemberId)).orElse(false)) return;
        if (access.isAdult() && access.memberId()
                .map(id -> service.relatedChildIds(householdId, id).contains(targetMemberId))
                .orElse(false)) {
            return;
        }
        throw new ForbiddenException("Not allowed to manage this member");
    }

    public record CreateHousehold(@NotBlank String name, @NotBlank String locale, @NotBlank String timezone) {}
    public record CreateMember(@NotBlank String name, MemberRole role, String color,
                               String pin, String username, String password) {}
    public record UpdateMember(String name, String color) {}
    public record SetPin(String pin) {}
    public record SetCredentials(@NotBlank String username, @NotBlank String password) {}
    public record SetRole(@NotNull MemberRole role) {}
    public record SetGuardians(@NotNull List<UUID> guardianIds) {}
}
