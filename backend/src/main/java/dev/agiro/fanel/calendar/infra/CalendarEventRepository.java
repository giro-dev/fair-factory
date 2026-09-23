package dev.agiro.fanel.calendar.infra;

import dev.agiro.fanel.calendar.domain.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, UUID> {
    List<CalendarEvent> findAllByHouseholdIdOrderByDateAsc(UUID householdId);
    List<CalendarEvent> findAllByHouseholdIdAndDateBetweenOrderByDateAsc(UUID householdId, LocalDate from, LocalDate to);
    List<CalendarEvent> findBySubscriptionId(UUID subscriptionId);
    void deleteBySubscriptionId(UUID subscriptionId);

    @Modifying
    @Query(value = "DELETE FROM calendar_event_assignee WHERE member_id = :memberId", nativeQuery = true)
    void removeAssignee(@Param("memberId") String memberId);
}
