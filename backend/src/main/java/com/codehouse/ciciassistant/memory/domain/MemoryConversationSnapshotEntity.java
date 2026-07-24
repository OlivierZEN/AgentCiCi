package com.codehouse.ciciassistant.memory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "memory_conversation_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uq_memory_conversation_snapshot",
        columnNames = {"company_id", "application_code", "conversation_ref"}))
public class MemoryConversationSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "application_code", nullable = false, length = 96)
    private String applicationCode;

    @Column(name = "conversation_ref", nullable = false, length = 160)
    private String conversationRef;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "active_agent_id", length = 64)
    private String activeAgentId;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "state_json", nullable = false, columnDefinition = "TEXT")
    private String stateJson;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MemoryConversationSnapshotEntity() {
    }

    public MemoryConversationSnapshotEntity(String companyId, String applicationCode, String conversationRef,
                                            Long subjectId, String activeAgentId, String summary,
                                            String stateJson) {
        this.companyId = companyId;
        this.applicationCode = applicationCode;
        this.conversationRef = conversationRef;
        this.subjectId = subjectId;
        update(activeAgentId, summary, stateJson);
    }

    public void update(String activeAgentId, String summary, String stateJson) {
        this.activeAgentId = activeAgentId;
        this.summary = summary;
        this.stateJson = stateJson;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getActiveAgentId() { return activeAgentId; }
    public String getSummary() { return summary; }
    public String getStateJson() { return stateJson; }
    public Instant getUpdatedAt() { return updatedAt; }
}
