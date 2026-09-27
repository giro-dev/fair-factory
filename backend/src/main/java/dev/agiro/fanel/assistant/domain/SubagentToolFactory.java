package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentRequest;
import dev.agiro.fanel.assistant.api.AgentResponse;
import dev.agiro.fanel.assistant.api.Delegation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.Collection;
import java.util.List;

/**
 * Exposes each active subagent as a {@code delegate_to_<agent>} tool the orchestrator's LLM can
 * call. The calling context (household, member, locale, attachments) travels through the
 * {@link ToolContext} under {@link DelegationContext#KEY}.
 */
public final class SubagentToolFactory {
    private static final Logger log = LoggerFactory.getLogger(SubagentToolFactory.class);

    private SubagentToolFactory() {}

    public record DelegationInput(
            @ToolParam(description = "Complete, self-contained task for the subagent") String task) {
    }

    public static List<ToolCallback> build(Collection<Agent> subagents) {
        return subagents.stream().map(SubagentToolFactory::toCallback).toList();
    }

    private static ToolCallback toCallback(Agent subagent) {
        String id = subagent.definition().id();
        String name = "delegate_to_" + id.replace('-', '_');
        String description = subagent.definition().toolDescription() != null
                ? subagent.definition().toolDescription()
                : "Delegate a task to agent " + id;
        return FunctionToolCallback
                .builder(name, (DelegationInput input, ToolContext toolContext) ->
                        invoke(subagent, input, toolContext))
                .description(description)
                .inputType(DelegationInput.class)
                // Return the subagent text verbatim (may be JSON); the default converter would quote it.
                .toolCallResultConverter(SubagentToolFactory::verbatim)
                .build();
    }

    private static String verbatim(Object result, java.lang.reflect.Type returnType) {
        return result == null ? "" : result.toString();
    }

    private static String invoke(Agent subagent, DelegationInput input, ToolContext toolContext) {
        String id = subagent.definition().id();
        try {
            DelegationContext ctx = (DelegationContext) toolContext.getContext().get(DelegationContext.KEY);
            long start = System.nanoTime();
            AgentResponse response = subagent.execute(
                    new AgentRequest(id, ctx.conversationId() + ":" + id, input.task(),
                            subagent.definition().supportsMedia() ? ctx.attachments() : List.of(),
                            ctx.memberId()),
                    ctx.householdId(), ctx.memberId(), ctx.locale());
            ctx.collector().add(new Delegation(id, response.text(),
                    (System.nanoTime() - start) / 1_000_000));
            return response.text();
        } catch (Exception e) {
            log.warn("Subagent '{}' failed: {}", id, e.toString());
            return "Subagent " + id + " failed: " + e.getMessage();
        }
    }
}
