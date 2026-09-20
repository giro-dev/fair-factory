package dev.agiro.fanel.household.web;

import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.household.domain.HouseholdService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * First-run setup: while the instance has no household at all, these endpoints are open so the
 * SPA can create the first household and its admin member in a single step.
 */
@RestController
@RequestMapping("/api/v1/setup")
public class SetupController {
    private final HouseholdService service;

    public SetupController(HouseholdService service) {
        this.service = service;
    }

    @GetMapping
    public SetupStatus status() {
        return new SetupStatus(service.isSetupRequired());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MemberDto setup(@Valid @RequestBody SetupRequest request) {
        return service.bootstrap(request.householdName(), request.locale(), request.timezone(),
                request.memberName(), request.username(), request.password());
    }

    public record SetupStatus(boolean required) {}

    public record SetupRequest(@NotBlank String householdName, String locale, String timezone,
                               @NotBlank String memberName, @NotBlank String username,
                               @NotBlank String password) {}
}
