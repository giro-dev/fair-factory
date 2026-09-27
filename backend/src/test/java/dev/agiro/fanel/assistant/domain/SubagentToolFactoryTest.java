package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentDefinition;
import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.Attachment;
import dev.agiro.fanel.assistant.api.Delegation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class SubagentToolFactoryTest {

    /** Fake subagent that records the invocation and returns a fixed text. */
    static class RecordingAgent implements Agent {
        private final AgentDefinition definition;
        private final String responseText;
        AgentRequest request;
        UUID householdId;
        UUID memberId;
        Locale locale;

        RecordingAgent(String id, boolean supportsMedia, String toolDescription, String responseText) {
            this.definition = new AgentDefinition(id, "assistant." + id + ".name",
                    "assistant." + id + ".description", supportsMedia, List.of("AssistantTools"),
                    toolDescription, false, "test:model");
            this.responseText = responseText;
        }

        @Override
        public AgentDefinition definition() {
            return definition;
        }

        @Override
        public AgentResponse execute(AgentRequest request, UUID householdId, UUID memberId, Locale locale) {
            this.request = request;
            this.householdId = householdId;
            this.memberId = memberId;
            this.locale = locale;
            return new AgentResponse(definition.id(), request.conversationId(), responseText,
                    List.of(), List.of());
        }
    }

    private DelegationContext context(CopyOnWriteArrayList<Delegation> collector) {
        return new DelegationContext(HOUSEHOLD_ID, MEMBER_ID, Locale.of("ca"), "conv-1",
                List.of(ATTACHMENT), collector);
    }

    private static final UUID HOUSEHOLD_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Attachment ATTACHMENT = new Attachment("image/jpeg", "aGVsbG8=");

    @Test
    void buildsOneCallbackPerSubagentWithExpectedNameAndDescription() {
        RecordingAgent menuPlanner = new RecordingAgent("menu-planner", false,
                "Plan the household weekly menu", "menu done");
        RecordingAgent recipeFromImage = new RecordingAgent("recipe-from-image", true,
                "Extract a recipe from an image", "recipe done");

        List<ToolCallback> callbacks = SubagentToolFactory.build(List.of(menuPlanner, recipeFromImage));

        assertThat(callbacks).hasSize(2);
        assertThat(callbacks.get(0).getToolDefinition().name()).isEqualTo("delegate_to_menu_planner");
        assertThat(callbacks.get(0).getToolDefinition().description())
                .isEqualTo("Plan the household weekly menu");
        assertThat(callbacks.get(1).getToolDefinition().name()).isEqualTo("delegate_to_recipe_from_image");
        assertThat(callbacks.get(1).getToolDefinition().description())
                .isEqualTo("Extract a recipe from an image");
    }

    @Test
    void invocationRunsSubagentWithDerivedConversationAndCollectsDelegation() {
        RecordingAgent menuPlanner = new RecordingAgent("menu-planner", false, "Plan menus", "week planned");
        ToolCallback callback = SubagentToolFactory.build(List.of(menuPlanner)).getFirst();
        CopyOnWriteArrayList<Delegation> collector = new CopyOnWriteArrayList<>();

        String result = callback.call("{\"task\":\"plan week 2026-W40\"}",
                new ToolContext(Map.of(DelegationContext.KEY, context(collector))));

        assertThat(result).isEqualTo("week planned");
        assertThat(menuPlanner.request.conversationId()).isEqualTo("conv-1:menu-planner");
        assertThat(menuPlanner.request.message()).isEqualTo("plan week 2026-W40");
        assertThat(menuPlanner.householdId).isEqualTo(HOUSEHOLD_ID);
        assertThat(menuPlanner.memberId).isEqualTo(MEMBER_ID);
        assertThat(menuPlanner.locale).isEqualTo(Locale.of("ca"));
        assertThat(menuPlanner.request.attachments()).isEmpty();
        assertThat(collector).hasSize(1);
        assertThat(collector.getFirst().agentId()).isEqualTo("menu-planner");
        assertThat(collector.getFirst().text()).isEqualTo("week planned");
        assertThat(collector.getFirst().latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void attachmentsAreForwardedOnlyWhenSubagentSupportsMedia() {
        RecordingAgent recipeFromImage = new RecordingAgent("recipe-from-image", true,
                "Extract recipes", "{\"name\":\"Coca\"}");
        ToolCallback callback = SubagentToolFactory.build(List.of(recipeFromImage)).getFirst();

        callback.call("{\"task\":\"extract the recipe\"}",
                new ToolContext(Map.of(DelegationContext.KEY, context(new CopyOnWriteArrayList<>()))));

        assertThat(recipeFromImage.request.attachments()).containsExactly(ATTACHMENT);
    }

    @Test
    void subagentFailureIsReturnedAsTextInsteadOfThrowing() {
        RecordingAgent failing = new RecordingAgent("menu-planner", false, "Plan menus", null) {
            @Override
            public AgentResponse execute(AgentRequest request, UUID householdId, UUID memberId, Locale locale) {
                throw new IllegalStateException("boom");
            }
        };
        ToolCallback callback = SubagentToolFactory.build(List.of(failing)).getFirst();

        String result = callback.call("{\"task\":\"plan\"}",
                new ToolContext(Map.of(DelegationContext.KEY, context(new CopyOnWriteArrayList<>()))));

        assertThat(result).isEqualTo("Subagent menu-planner failed: boom");
    }
}
