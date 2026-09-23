package dev.agiro.fanel.automation.infra;

import dev.agiro.fanel.automation.domain.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findByHouseholdIdOrderByTypeAsc(UUID householdId);
    Optional<AutomationRule> findByHouseholdIdAndId(UUID householdId, UUID id);

    /**
     * Single-statement stamp: avoids a read-then-write upgrade inside the transaction, which on
     * SQLite fails fast with SQLITE_BUSY when a concurrent writer (e.g. the event-publication
     * listener) commits in between — {@code busy_timeout} cannot retry a promotion conflict.
     */
    @Modifying
    @Query("UPDATE AutomationRule r SET r.lastRunAt = :now WHERE r.householdId = :householdId AND r.id = :id")
    int stampLastRunAt(@Param("householdId") UUID householdId, @Param("id") UUID id, @Param("now") Instant now);
}
