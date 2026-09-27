package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.AssistantApi;
import dev.agiro.fanel.assistant.infra.AgentProperties;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class AgentService implements AssistantApi {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRegistry registry;
    private final AgentProperties properties;

    public AgentService(AgentRegistry registry, AgentProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    public AgentResponse chat(UUID householdId, UUID memberId, AgentRequest request, Locale locale) {
        String agentId = request.agentId() != null && !request.agentId().isBlank()
                ? request.agentId() : properties.getOrchestratorId();
        Agent agent = registry.get(agentId)
                .orElseThrow(() -> new EntityNotFoundException("Agent not found: " + agentId));
        return agent.execute(request, householdId, memberId, locale);
    }

    @Override
    public Optional<AgentResponse> run(UUID householdId, String agentId, String message, Locale locale) {
        Optional<Agent> agent = registry.get(agentId);
        if (agent.isEmpty()) {
            log.warn("Agent '{}' is not registered (no model provider configured); skipping run", agentId);
            return Optional.empty();
        }
        try {
            return Optional.of(agent.get().execute(
                    new AgentRequest(agentId, null, message, List.of(), null), householdId, null, locale));
        } catch (Exception e) {
            log.warn("Agent '{}' run failed for household {}", agentId, householdId, e);
            return Optional.empty();
        }
    }
}
