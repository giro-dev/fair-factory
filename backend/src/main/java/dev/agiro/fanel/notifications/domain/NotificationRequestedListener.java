package dev.agiro.fanel.notifications.domain;

import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.notifications.infra.PushSubscriptionRepository;
import dev.agiro.fanel.shared.events.NotificationRequested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Delivers {@link NotificationRequested} events as Web Push notifications to the target members. */
@Component
public class NotificationRequestedListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

    private final HouseholdApi household;
    private final PushSubscriptionRepository subscriptions;
    private final PushSender pushSender;
    private final MessageSource messages;

    public NotificationRequestedListener(HouseholdApi household, PushSubscriptionRepository subscriptions,
                                         PushSender pushSender, MessageSource messages) {
        this.household = household;
        this.subscriptions = subscriptions;
        this.pushSender = pushSender;
        this.messages = messages;
    }

    @ApplicationModuleListener
    public void on(NotificationRequested event) {
        HouseholdDto householdDto = household.get(event.householdId());
        Locale locale = Locale.forLanguageTag(householdDto.locale());
        String title = messages.getMessage(event.titleKey(), null, event.titleKey(), locale);
        String body = messages.getMessage(event.bodyKey(),
                event.bodyArgs() != null ? event.bodyArgs().toArray() : null, event.bodyKey(), locale);

        List<UUID> targets = event.memberIds() != null && !event.memberIds().isEmpty()
                ? event.memberIds()
                : household.listMembers(event.householdId()).stream().map(MemberDto::id).toList();

        for (UUID memberId : targets) {
            for (PushSubscription subscription
                    : subscriptions.findByHouseholdIdAndMemberId(event.householdId(), memberId)) {
                try {
                    pushSender.send(subscription, title, body);
                } catch (Exception e) {
                    log.warn("Could not send push notification to member {} of household {}",
                            memberId, event.householdId(), e);
                }
            }
        }
    }
}
