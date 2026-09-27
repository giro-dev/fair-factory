package dev.agiro.fanel.assistant.domain;

import dev.agiro.fanel.assistant.api.AgentConfigUpdate;
import dev.agiro.fanel.assistant.infra.AgentConfigRepository;
import dev.agiro.fanel.assistant.infra.AgentProperties;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Manages the persisted, instance-wide overrides of the agent catalogue defined in
 * {@code fanel.assistant.agents}. The effective configuration of an agent is the catalogue
 * default merged with its override row (override fields win when non-null).
 */
@Service
public class AgentConfigService {
    private static final Set<String> PROVIDERS = Set.of("openai", "ollama", "anthropic");

    private final AgentConfigRepository repository;
    private final AgentProperties properties;

    public AgentConfigService(AgentConfigRepository repository, AgentProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** All persisted overrides, indexed by agent id. */
    public Map<String, AgentConfigEntity> overrides() {
        return repository.findAll().stream()
                .collect(Collectors.toMap(AgentConfigEntity::getAgentId, Function.identity()));
    }

    @Transactional
    public AgentConfigEntity upsert(String agentId, AgentConfigUpdate update, String updatedBy) {
        AgentConfigEntity entity = repository.findById(agentId).orElseGet(() -> {
            AgentConfigEntity created = new AgentConfigEntity(agentId);
            created.setEnabled(true);
            return created;
        });
        apply(entity, update);
        entity.setUpdatedAt(Instant.now());
        entity.setUpdatedBy(updatedBy);
        return repository.save(entity);
    }

    @Transactional
    public AgentConfigEntity upsert(String agentId, AgentConfigUpdate update) {
        return upsert(agentId, update, currentUsername());
    }

    @Transactional
    public void reset(String agentId) {
        requireCatalogueAgent(agentId);
        repository.deleteById(agentId);
    }

    /** Merges the catalogue defaults with an override row; override fields win when non-null. */
    public ModelProfile effectiveProfile(ModelProfile defaults, AgentConfigEntity override) {
        if (defaults == null && override == null) {
            return null;
        }
        if (defaults == null) {
            return new ModelProfile(override.getProvider(), override.getModel(),
                    override.getTemperature(), override.getMaxTokens());
        }
        if (override == null) {
            return defaults;
        }
        return new ModelProfile(
                override.getProvider() != null ? override.getProvider() : defaults.provider(),
                override.getModel() != null ? override.getModel() : defaults.model(),
                override.getTemperature() != null ? override.getTemperature() : defaults.temperature(),
                override.getMaxTokens() != null ? override.getMaxTokens() : defaults.maxTokens());
    }

    private void apply(AgentConfigEntity entity, AgentConfigUpdate update) {
        requireCatalogueAgent(entity.getAgentId());
        if (update.enabled() != null) {
            entity.setEnabled(update.enabled());
        }
        if (update.provider() != null) {
            String provider = update.provider().toLowerCase();
            if (!PROVIDERS.contains(provider)) {
                throw new IllegalArgumentException("Unknown provider: " + update.provider()
                        + " (expected one of " + PROVIDERS + ")");
            }
            if (update.model() == null || update.model().isBlank()) {
                throw new IllegalArgumentException("A model is required when setting a provider");
            }
            entity.setProvider(provider);
        }
        if (update.model() != null) {
            if (update.model().isBlank()) {
                throw new IllegalArgumentException("Model must not be blank");
            }
            entity.setModel(update.model());
        }
        if (update.temperature() != null) {
            if (update.temperature() < 0 || update.temperature() > 2) {
                throw new IllegalArgumentException("Temperature must be between 0 and 2");
            }
            entity.setTemperature(update.temperature());
        }
        if (update.maxTokens() != null) {
            if (update.maxTokens() <= 0) {
                throw new IllegalArgumentException("maxTokens must be positive");
            }
            entity.setMaxTokens(update.maxTokens());
        }
    }

    private void requireCatalogueAgent(String agentId) {
        if (!properties.getAgents().containsKey(agentId)) {
            throw new EntityNotFoundException("Unknown agent: " + agentId);
        }
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
