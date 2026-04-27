package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "user_memory")
public class UserMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    /** FACT | PREFERENCE | CONTEXT | INSTRUCTION */
    @Column(name = "category", nullable = false, length = 32)
    private String category;

    /** MANUAL | EXTRACTED */
    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /** 可选语义键，用于 upsert 去重，如 user.role */
    @Column(name = "memory_key", length = 128)
    private String memoryKey;

    @Column(name = "confidence", nullable = false, precision = 3, scale = 2)
    private BigDecimal confidence;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "pinned", nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserMemoryEntity() {
    }

    public UserMemoryEntity(String orgId,
                            String userId,
                            String agentId,
                            String category,
                            String source,
                            String content,
                            String memoryKey,
                            BigDecimal confidence) {
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.category = category;
        this.source = source;
        this.content = content;
        this.memoryKey = memoryKey;
        this.confidence = confidence != null ? confidence : BigDecimal.ONE;
        this.enabled = true;
        this.pinned = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void updateContent(String content, String category, boolean enabled, boolean pinned) {
        this.content = content;
        this.category = category;
        this.enabled = enabled;
        this.pinned = pinned;
        this.updatedAt = Instant.now();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public String getUserId() { return userId; }
    public String getAgentId() { return agentId; }
    public String getCategory() { return category; }
    public String getSource() { return source; }
    public String getContent() { return content; }
    public String getMemoryKey() { return memoryKey; }
    public BigDecimal getConfidence() { return confidence; }
    public boolean isEnabled() { return enabled; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
