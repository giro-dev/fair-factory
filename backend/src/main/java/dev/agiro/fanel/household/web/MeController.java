package dev.agiro.fanel.household.web;

import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.household.domain.HouseholdService;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.web.ForbiddenException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Returns the authenticated identity: which member/household the caller is bound to, or that it
 * is the global bootstrap admin / an API-token client with no member attached.
 */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {
    private final HouseholdService service;
    private final CurrentAccess access;

    public MeController(HouseholdService service, CurrentAccess access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping
    public MeDto me() {
        var memberId = access.memberId();
        var householdId = access.householdId();
        if (memberId.isPresent() && householdId.isPresent()) {
            MemberDto member = service.getMember(householdId.get(), memberId.get());
            HouseholdDto household = service.get(householdId.get());
            return new MeDto("MEMBER", member, household);
        }
        if (access.isAdmin()) {
            return new MeDto("ADMIN", null, null);
        }
        return new MeDto("API", null, null);
    }

    public record MeDto(String kind, MemberDto member, HouseholdDto household) {}
}
