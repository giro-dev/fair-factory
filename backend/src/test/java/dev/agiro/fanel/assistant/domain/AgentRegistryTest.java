package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.domain.tools.AssistantTools;
import dev.agiro.fanel.assistant.infra.AgentProperties;
import dev.agiro.fanel.assistant.infra.ChatClientFactory;
import dev.agiro.fanel.assistant.infra.PromptLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentRegistryTest {

    private ChatClientFactory chatClientFactory;
    private AgentConfigService configService;
    private AgentProperties properties;
    private AgentRegistry registry;
    private final Map<String, AgentConfigEntity> overrides = new LinkedHashMap<>();

    private static AgentProperties.AgentConfig config(boolean supportsMedia, List<String> tools,
                                                      String toolDescription, ModelProfile model) {
        AgentProperties.AgentConfig config = new AgentProperties.AgentConfig();
        config.setSupportsMedia(supportsMedia);
        config.setTools(tools);
        config.setToolDescription(toolDescription);
        config.setModel(model);
        return config;
    }

    @BeforeEach
    void setUp() {
        chatClientFactory = mock(ChatClientFactory.class);
        configService = mock(AgentConfigService.class);
        properties = new AgentProperties();
        properties.setOrchestratorId("orchestrator");
        Map<String, AgentProperties.AgentConfig> agents = new LinkedHashMap<>();
        agents.put("orchestrator", config(false, List.of("AssistantTools", "Subagents"), null,
                new ModelProfile("openai", "gpt-4o", 0.3, null)));
        agents.put("menu-planner", config(false, List.of("AssistantTools"), "Plan menus",
                new ModelProfile("openai", "gpt-4o-mini", 0.5, null)));
        agents.put("recipe-from-image", config(true, List.of("AssistantTools"), "Extract recipes",
                new ModelProfile("openai", "gpt-4o-mini", 0.2, null)));
        properties.setAgents(agents);

        overrides.clear();
        when(configService.overrides()).thenAnswer(inv -> Map.copyOf(overrides));
        when(configService.effectiveProfile(any(), any())).thenAnswer(inv -> {
            ModelProfile defaults = inv.getArgument(0);
            AgentConfigEntity override = inv.getArgument(1);
            if (override == null) {
                return defaults;
            }
            return new ModelProfile(
                    override.getProvider() != null ? override.getProvider() : defaults.provider(),
                    override.getModel() != null ? override.getModel() : defaults.model(),
                    override.getTemperature() != null ? override.getTemperature() : defaults.temperature(),
                    override.getMaxTokens() != null ? override.getMaxTokens() : defaults.maxTokens());
        });
        when(chatClientFactory.build(any(), any(), anyList(), anyList()))
                .thenReturn(Optional.of(mock(ChatClient.class)));

        registry = new AgentRegistry(properties, chatClientFactory, mock(PromptLoader.class),
                mock(AssistantTools.class), configService);
    }

    @Test
    void registersAllAgentsAndMarksTheOrchestrator() {
        registry.reload();

        assertThat(registry.list()).hasSize(3);
        assertThat(registry.get("orchestrator")).isPresent();
        assertThat(registry.get("orchestrator").get().definition().orchestrator()).isTrue();
        assertThat(registry.get("menu-planner").get().definition().orchestrator()).isFalse();
        assertThat(registry.get("menu-planner").get().definition().toolDescription()).isEqualTo("Plan menus");
    }

    @Test
    void disabledSubagentIsSkippedAndOrchestratorGetsFewerDelegationCallbacks() {
        AgentConfigEntity disabled = new AgentConfigEntity("menu-planner");
        disabled.setEnabled(false);
        overrides.put("menu-planner", disabled);

        registry.reload();

        assertThat(registry.get("menu-planner")).isEmpty();
        assertThat(registry.get("recipe-from-image")).isPresent();

        ModelProfile orchestratorProfile = new ModelProfile("openai", "gpt-4o", 0.3, null);
        verify(chatClientFactory).build(eq(orchestratorProfile), isNull(), anyList(),
                org.mockito.ArgumentMatchers.<List<ToolCallback>>argThat(callbacks -> callbacks.size() == 1));
    }

    @Test
    void overrideChangesTheModelProfilePassedToTheFactory() {
        AgentConfigEntity override = new AgentConfigEntity("menu-planner");
        override.setEnabled(true);
        override.setProvider("ollama");
        override.setModel("llama3.2");
        override.setTemperature(0.4);
        overrides.put("menu-planner", override);

        registry.reload();

        ArgumentCaptor<ModelProfile> captor = ArgumentCaptor.forClass(ModelProfile.class);
        verify(chatClientFactory, org.mockito.Mockito.times(3)).build(captor.capture(), any(), anyList(), anyList());
        assertThat(captor.getAllValues())
                .contains(new ModelProfile("ollama", "llama3.2", 0.4, null));
    }

    @Test
    void reloadReflectsChangedOverrides() {
        registry.reload();
        assertThat(registry.get("menu-planner")).isPresent();

        AgentConfigEntity disabled = new AgentConfigEntity("menu-planner");
        disabled.setEnabled(false);
        overrides.put("menu-planner", disabled);
        registry.reload();

        assertThat(registry.get("menu-planner")).isEmpty();
        assertThat(registry.list()).hasSize(2);
    }
}
