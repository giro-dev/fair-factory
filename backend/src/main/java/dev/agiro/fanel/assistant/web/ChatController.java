package dev.agiro.fanel.assistant.web;

import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.domain.AgentService;
import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.security.MemberPrincipal;
import dev.agiro.fanel.shared.web.ForbiddenException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/assistant")
public class ChatController {
    private final AgentService agentService;
    private final HouseholdApi households;
    private final CurrentAccess access;

    public ChatController(AgentService agentService, HouseholdApi households, CurrentAccess access) {
        this.agentService = agentService;
        this.households = households;
        this.access = access;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@PathVariable UUID householdId,
                              @Valid @RequestBody AgentRequest request,
                              @AuthenticationPrincipal MemberPrincipal principal) {
        UUID memberId = resolveActingMember(householdId, principal, request.memberId());
        Locale locale = resolveLocale(householdId);
        return agentService.chat(householdId, memberId, request, locale);
    }

    /**
     * Members always chat as themselves. Full-access callers (global admin, API token) may pass the
     * member picked in the "who am I" selector as {@code request.memberId}; without it the chat
     * proceeds unattributed.
     */
    private UUID resolveActingMember(UUID householdId, MemberPrincipal principal, UUID requestedMemberId) {
        if (principal != null) {
            if (!principal.householdId().equals(householdId)) {
                throw new ForbiddenException("You are not a member of this household");
            }
            return principal.memberId();
        }
        if (!access.hasFullAccess()) {
            throw new ForbiddenException("You are not a member of this household");
        }
        return requestedMemberId != null ? households.getMember(householdId, requestedMemberId).id() : null;
    }

    private Locale resolveLocale(UUID householdId) {
        try {
            HouseholdDto household = households.get(householdId);
            return household != null && household.locale() != null
                    ? Locale.forLanguageTag(household.locale())
                    : Locale.of("ca");
        } catch (Exception e) {
            return Locale.of("ca");
        }
    }
}
