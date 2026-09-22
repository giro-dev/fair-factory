package dev.agiro.fanel.automation.web;

import dev.agiro.fanel.automation.api.AutomationApi;
import dev.agiro.fanel.automation.api.AutomationRuleDto;
import dev.agiro.fanel.automation.api.RuleType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/automation/rules")
public class AutomationController {
    private final AutomationApi automation;

    public AutomationController(AutomationApi automation) {
        this.automation = automation;
    }

    @GetMapping
    public List<AutomationRuleDto> list(@PathVariable UUID householdId) {
        return automation.list(householdId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AutomationRuleDto create(@PathVariable UUID householdId, @Valid @RequestBody CreateRule request) {
        return automation.create(householdId, request.type(), request.dayOfWeek(), request.hour());
    }

    @PutMapping("/{ruleId}")
    public AutomationRuleDto update(@PathVariable UUID householdId, @PathVariable UUID ruleId,
                                    @Valid @RequestBody UpdateRule request) {
        return automation.update(householdId, ruleId, request.enabled(), request.dayOfWeek(), request.hour());
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID householdId, @PathVariable UUID ruleId) {
        automation.delete(householdId, ruleId);
    }

    @PostMapping("/{ruleId}/run")
    public AutomationRuleDto run(@PathVariable UUID householdId, @PathVariable UUID ruleId) {
        return automation.runNow(householdId, ruleId);
    }

    public record CreateRule(@NotNull RuleType type,
                             @NotNull @Min(1) @Max(7) Integer dayOfWeek,
                             @NotNull @Min(0) @Max(23) Integer hour) {
    }

    public record UpdateRule(Boolean enabled, @Min(1) @Max(7) Integer dayOfWeek, @Min(0) @Max(23) Integer hour) {
    }
}
