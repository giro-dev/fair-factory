package dev.agiro.fanel.automation.infra;

import dev.agiro.fanel.automation.domain.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findByHouseholdIdOrderByTypeAsc(UUID householdId);
    Optional<AutomationRule> findByHouseholdIdAndId(UUID householdId, UUID id);
}
