package dev.agiro.fanel.shared.web;

import dev.agiro.fanel.shared.events.HouseholdEvent;
import dev.agiro.fanel.shared.security.CurrentAccess;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** Server-sent events endpoint: pushes household-scoped change notifications to clients. */
@RestController
@RequestMapping("/api/v1/events")
public class SseController {
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final CurrentAccess access;

    public SseController(CurrentAccess access) {
        this.access = access;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam UUID household) {
        boolean allowed = access.hasFullAccess()
                || access.householdId().map(household::equals).orElse(false);
        if (!allowed) throw new ForbiddenException("Not allowed to subscribe to this household");
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(household, emitter);
        subscriptions.add(subscription);
        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> subscriptions.remove(subscription));
        emitter.onError(error -> subscriptions.remove(subscription));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException ignored) {
            subscriptions.remove(subscription);
        }
        return emitter;
    }

    @EventListener
    void onHouseholdEvent(HouseholdEvent event) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.householdId().equals(event.householdId())) continue;
            try {
                subscription.emitter().send(SseEmitter.event().name(event.topic()).data(event.householdId().toString()));
            } catch (IOException | IllegalStateException ignored) {
                subscriptions.remove(subscription);
            }
        }
    }

    private record Subscription(UUID householdId, SseEmitter emitter) {
    }
}
