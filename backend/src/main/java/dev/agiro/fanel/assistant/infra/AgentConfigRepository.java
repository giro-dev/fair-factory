package dev.agiro.fanel.assistant.infra;

import dev.agiro.fanel.assistant.domain.AgentConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentConfigRepository extends JpaRepository<AgentConfigEntity, String> {
}
