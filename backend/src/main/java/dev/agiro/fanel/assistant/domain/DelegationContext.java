package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.Attachment;
import dev.agiro.fanel.assistant.api.Delegation;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Carries the calling context of the orchestrator into delegation tool callbacks via the
 * Spring AI {@code ToolContext} (key {@link #KEY}). {@code collector} accumulates the
 * delegations performed during a single chat call.
 */
public record DelegationContext(UUID householdId, UUID memberId, Locale locale, String conversationId,
                                List<Attachment> attachments, CopyOnWriteArrayList<Delegation> collector) {
    public static final String KEY = "fanel.delegation";
}
