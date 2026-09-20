package dev.agiro.fanel.assistant.web;

import dev.agiro.fanel.assistant.api.AgentDefinition;
import dev.agiro.fanel.assistant.domain.AgentRegistry;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.security.MemberPrincipal;
import dev.agiro.fanel.shared.web.ForbiddenException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/assistant/agents")
public class AgentController {
    private final AgentRegistry registry;
    private final CurrentAccess access;

    public AgentController(AgentRegistry registry, CurrentAccess access) {
        this.registry = registry;
        this.access = access;
    }

    @GetMapping
    public List<AgentDefinition> list(@PathVariable UUID householdId,
                                      @AuthenticationPrincipal MemberPrincipal principal) {
        boolean allowed = principal != null
                ? principal.householdId().equals(householdId)
                : access.hasFullAccess();
        if (!allowed) {
            throw new ForbiddenException("You are not a member of this household");
        }
        return registry.list();
    }
}
