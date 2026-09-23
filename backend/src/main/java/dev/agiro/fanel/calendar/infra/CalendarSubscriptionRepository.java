package dev.agiro.fanel.calendar.infra;

import dev.agiro.fanel.calendar.domain.CalendarSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarSubscriptionRepository extends JpaRepository<CalendarSubscription, UUID> {
    List<CalendarSubscription> findByHouseholdIdOrderByNameAsc(UUID householdId);
    Optional<CalendarSubscription> findByHouseholdIdAndId(UUID householdId, UUID id);
}
