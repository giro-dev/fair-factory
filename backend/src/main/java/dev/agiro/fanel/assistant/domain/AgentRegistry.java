package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentDefinition;
import dev.agiro.fanel.assistant.domain.agents.DefaultAgent;
import dev.agiro.fanel.assistant.domain.agents.OrchestratorAgent;
import dev.agiro.fanel.assistant.domain.tools.AssistantTools;
import dev.agiro.fanel.assistant.infra.AgentProperties;
import dev.agiro.fanel.assistant.infra.ChatClientFactory;
import dev.agiro.fanel.assistant.infra.PromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the active agents. Built from the {@code fanel.assistant.agents} catalogue merged with
 * the persisted overrides; rebuilt via {@link #reload()} on startup and after every admin
 * configuration change. Agents that are disabled or whose model cannot be resolved are skipped.
 */
@Component
public class AgentRegistry {
    private static final Logger LOG = LoggerFactory.getLogger(AgentRegistry.class);

    private final AgentProperties properties;
    private final ChatClientFactory chatClientFactory;
    private final PromptLoader promptLoader;
    private final AssistantTools assistantTools;
    private final AgentConfigService configService;

    private final AtomicReference<Map<String, Agent>> agents = new AtomicReference<>(Map.of());

    public AgentRegistry(AgentProperties properties,
                         ChatClientFactory chatClientFactory,
                         PromptLoader promptLoader,
                         AssistantTools assistantTools,
                         AgentConfigService configService) {
        this.properties = properties;
        this.chatClientFactory = chatClientFactory;
        this.promptLoader = promptLoader;
        this.assistantTools = assistantTools;
        this.configService = configService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        reload();
    }

    public synchronized void reload() {
        Map<String, AgentConfigEntity> overrides = configService.overrides();
        Map<String, Agent> built = new LinkedHashMap<>();
        List<Agent> subagents = new ArrayList<>();
        Agent orchestrator = null;

        // Build all non-orchestrator agents first: the orchestrator delegates to them.
        for (Map.Entry<String, AgentProperties.AgentConfig> entry : properties.getAgents().entrySet()) {
            String id = entry.getKey();
            if (id.equals(properties.getOrchestratorId())) {
                continue;
            }
            buildAgent(id, entry.getValue(), overrides.get(id), List.of())
                    .ifPresent(agent -> {
                        built.put(id, agent);
                        subagents.add(agent);
                    });
        }

        AgentProperties.AgentConfig orchestratorConfig = properties.getAgents().get(properties.getOrchestratorId());
        if (orchestratorConfig != null) {
            List<ToolCallback> delegationCallbacks = orchestratorConfig.getTools().contains("Subagents")
                    ? SubagentToolFactory.build(subagents)
                    : List.of();
            orchestrator = buildAgent(properties.getOrchestratorId(), orchestratorConfig,
                    overrides.get(properties.getOrchestratorId()), delegationCallbacks)
                    .orElse(null);
            if (orchestrator != null) {
                built.put(properties.getOrchestratorId(), orchestrator);
            }
        }

        agents.set(Collections.unmodifiableMap(built));
        LOG.info("Registered agents: {}", built.keySet());
    }

    public Optional<Agent> get(String id) {
        return Optional.ofNullable(agents.get().get(id));
    }

    public List<AgentDefinition> list() {
        return agents.get().values().stream().map(Agent::definition).toList();
    }

    public boolean isEnabled(String id) {
        return agents.get().containsKey(id);
    }

    private Optional<Agent> buildAgent(String id, AgentProperties.AgentConfig config,
                                       AgentConfigEntity override, List<ToolCallback> toolCallbacks) {
        if (override != null && !override.isEnabled()) {
            LOG.info("Agent '{}' is disabled; skipping", id);
            return Optional.empty();
        }
        ModelProfile profile = configService.effectiveProfile(config.getModel(), override);
        if (profile == null || profile.model() == null) {
            LOG.debug("Agent '{}' has no model configured; skipping", id);
            return Optional.empty();
        }
        List<Object> toolObjects = config.getTools().contains("AssistantTools")
                ? List.of(assistantTools)
                : List.of();
        boolean orchestrator = id.equals(properties.getOrchestratorId());
        Optional<ChatClient> chatClient = chatClientFactory.build(profile, null, toolObjects, toolCallbacks);
        chatClient.ifPresentOrElse(
                c -> LOG.info("Registered agent '{}' (model {}:{})", id, profile.provider(), profile.model()),
                () -> LOG.info("Agent '{}' not registered: no ChatModel for its provider", id));
        return chatClient.map(client -> {
            AgentDefinition definition = new AgentDefinition(id,
                    config.getNameKey() != null ? config.getNameKey() : "assistant." + id + ".name",
                    config.getDescriptionKey() != null ? config.getDescriptionKey() : "assistant." + id + ".description",
                    config.isSupportsMedia(),
                    config.getTools(),
                    config.getToolDescription(),
                    orchestrator,
                    profile.provider() + ":" + profile.model());
            return orchestrator
                    ? new OrchestratorAgent(definition, client, promptLoader)
                    : new DefaultAgent(definition, client, promptLoader);
        });
    }
}
