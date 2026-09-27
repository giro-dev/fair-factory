package dev.agiro.fanel.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Instance-wide override of a catalogue agent's model configuration. Rows only exist for
 * agents that diverge from the yml defaults; this table is global (no householdId) on purpose.
 */
@Entity
@Table(name = "assistant_agent_config")
public class AgentConfigEntity {
    @Id
    @Column(name = "agent_id", length = 64)
    private String agentId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(length = 32)
    private String provider;
    @Column(length = 128)
    private String model;
    private Double temperature;
    @Column(name = "max_tokens")
    private Integer maxTokens;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "updated_by", length = 128)
    private String updatedBy;

    protected AgentConfigEntity() {}

    public AgentConfigEntity(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentId() { return agentId; }
    public boolean isEnabled() { return enabled; }
    public String getProvider() { return provider; }
    public String getModel() { return model; }
    public Double getTemperature() { return temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setModel(String model) { this.model = model; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
