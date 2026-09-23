package dev.agiro.fanel.calendar.web;

import dev.agiro.fanel.calendar.api.CalendarApi;
import dev.agiro.fanel.calendar.api.CalendarSubscriptionDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/households/{householdId}/calendar/subscriptions")
public class CalendarSubscriptionController {
    private final CalendarApi calendar;

    public CalendarSubscriptionController(CalendarApi calendar) {
        this.calendar = calendar;
    }

    @GetMapping
    public List<CalendarSubscriptionDto> list(@PathVariable UUID householdId) {
        return calendar.listSubscriptions(householdId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CalendarSubscriptionDto create(@PathVariable UUID householdId,
                                          @Valid @RequestBody CreateSubscription request) {
        return calendar.createSubscription(householdId, request.name(), request.url(), request.color());
    }

    @PutMapping("/{subscriptionId}")
    public CalendarSubscriptionDto update(@PathVariable UUID householdId, @PathVariable UUID subscriptionId,
                                          @RequestBody UpdateSubscription request) {
        return calendar.updateSubscription(householdId, subscriptionId, request.name(), request.url(), request.color());
    }

    @DeleteMapping("/{subscriptionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID householdId, @PathVariable UUID subscriptionId) {
        calendar.deleteSubscription(householdId, subscriptionId);
    }

    @PostMapping("/{subscriptionId}/sync")
    public CalendarSubscriptionDto sync(@PathVariable UUID householdId, @PathVariable UUID subscriptionId) {
        return calendar.syncSubscription(householdId, subscriptionId);
    }

    public record CreateSubscription(@NotBlank String name, @NotBlank String url, String color) {
    }

    public record UpdateSubscription(String name, String url, String color) {
    }
}
