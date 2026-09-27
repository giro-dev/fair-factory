package dev.agiro.fanel.assistant.web;

import dev.agiro.fanel.assistant.api.AgentConfigDto;
import dev.agiro.fanel.assistant.api.AgentConfigUpdate;
import dev.agiro.fanel.assistant.api.AgentTestResult;
import dev.agiro.fanel.assistant.api.ProviderDto;
import dev.agiro.fanel.assistant.domain.AgentConfigEntity;
import dev.agiro.fanel.assistant.domain.AgentConfigService;
import dev.agiro.fanel.assistant.domain.AgentRegistry;
import dev.agiro.fanel.assistant.domain.ModelProfile;
import dev.agiro.fanel.assistant.infra.AgentProperties;
import dev.agiro.fanel.assistant.infra.ChatClientFactory;
import dev.agiro.fanel.assistant.infra.ModelCatalogService;
import dev.agiro.fanel.shared.security.CurrentAccess;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import dev.agiro.fanel.shared.web.ForbiddenException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Instance-wide assistant configuration: model provider/parameters per catalogue agent and
 * provider model discovery. Restricted to the global admin (or the API token); the persisted
 * overrides are applied by reloading the {@link AgentRegistry} in place, without a restart.
 */
@RestController
@RequestMapping("/api/v1/admin/assistant")
public class AssistantAdminController {
    private static final String PING_MESSAGE = "Reply with the single word OK.";

    private final AgentProperties properties;
    private final AgentConfigService configService;
    private final AgentRegistry registry;
    private final ModelCatalogService modelCatalog;
    private final ChatClientFactory chatClientFactory;
    private final CurrentAccess access;

    public AssistantAdminController(AgentProperties properties, AgentConfigService configService,
                                    AgentRegistry registry, ModelCatalogService modelCatalog,
                                    ChatClientFactory chatClientFactory, CurrentAccess access) {
        this.properties = properties;
        this.configService = configService;
        this.registry = registry;
        this.modelCatalog = modelCatalog;
        this.chatClientFactory = chatClientFactory;
        this.access = access;
    }

    @GetMapping("/agents")
    public List<AgentConfigDto> agents() {
        requireGlobalAdmin();
        Map<String, AgentConfigEntity> overrides = configService.overrides();
        return properties.getAgents().entrySet().stream()
                .map(e -> toDto(e.getKey(), e.getValue(), overrides.get(e.getKey())))
                .toList();
    }

    @PutMapping("/agents/{agentId}")
    public AgentConfigDto update(@PathVariable String agentId, @RequestBody AgentConfigUpdate update) {
        requireGlobalAdmin();
        configService.upsert(agentId, update);
        registry.reload();
        return toDto(agentId, properties.getAgents().get(agentId),
                configService.overrides().get(agentId));
    }

    @DeleteMapping("/agents/{agentId}")
    public ResponseEntity<Void> reset(@PathVariable String agentId) {
        requireGlobalAdmin();
        configService.reset(agentId);
        registry.reload();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/providers")
    public List<ProviderDto> providers(@RequestParam(defaultValue = "false") boolean refresh) {
        requireGlobalAdmin();
        return modelCatalog.providers(refresh);
    }

    /**
     * Connectivity ping of the agent's effective model profile: a single LLM call without tools
     * or memory. It verifies provider reachability and model name, not agent behaviour.
     */
    @PostMapping("/agents/{agentId}/test")
    public AgentTestResult test(@PathVariable String agentId) {
        requireGlobalAdmin();
        AgentProperties.AgentConfig config = properties.getAgents().get(agentId);
        if (config == null) {
            throw new EntityNotFoundException("Unknown agent: " + agentId);
        }
        AgentConfigEntity override = configService.overrides().get(agentId);
        ModelProfile profile = configService.effectiveProfile(config.getModel(), override);
        Optional<ChatModel> model = chatClientFactory.resolve(profile);
        if (model.isEmpty()) {
            return new AgentTestResult(false, 0, null,
                    "No ChatModel available for provider '" + (profile != null ? profile.provider() : null) + "'");
        }
        ChatOptions.Builder options = ChatOptions.builder();
        if (profile.model() != null) {
            options.model(profile.model());
        }
        if (profile.temperature() != null) {
            options.temperature(profile.temperature());
        }
        if (profile.maxTokens() != null) {
            options.maxTokens(profile.maxTokens());
        }
        long start = System.nanoTime();
        try {
            String text = org.springframework.ai.chat.client.ChatClient.create(model.get())
                    .prompt()
                    .options(options)
                    .user(PING_MESSAGE)
                    .call()
                    .content();
            return new AgentTestResult(true, elapsedMs(start), text, null);
        } catch (Exception e) {
            return new AgentTestResult(false, elapsedMs(start), null, e.getMessage());
        }
    }

    private AgentConfigDto toDto(String id, AgentProperties.AgentConfig config, AgentConfigEntity override) {
        ModelProfile defaults = config.getModel();
        ModelProfile effective = configService.effectiveProfile(defaults, override);
        boolean enabled = override == null || override.isEnabled();
        return new AgentConfigDto(id,
                config.getNameKey() != null ? config.getNameKey() : "assistant." + id + ".name",
                config.getDescriptionKey() != null ? config.getDescriptionKey() : "assistant." + id + ".description",
                config.isSupportsMedia(),
                id.equals(properties.getOrchestratorId()),
                enabled,
                effective != null ? effective.provider() : null,
                effective != null ? effective.model() : null,
                effective != null ? effective.temperature() : null,
                effective != null ? effective.maxTokens() : null,
                new AgentConfigDto.ModelDefaults(
                        defaults != null ? defaults.provider() : null,
                        defaults != null ? defaults.model() : null,
                        defaults != null ? defaults.temperature() : null,
                        defaults != null ? defaults.maxTokens() : null),
                override != null,
                registry.get(id).isPresent());
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private void requireGlobalAdmin() {
        if (!access.hasGlobalAccess()) {
            throw new ForbiddenException("Only the global admin can configure the assistant");
        }
    }
}
