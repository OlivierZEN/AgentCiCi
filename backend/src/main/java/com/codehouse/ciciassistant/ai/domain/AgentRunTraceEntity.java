package com.codehouse.ciciassistant.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_run_trace")
public class AgentRunTraceEntity {

    @Id
    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "summary", nullable = false, length = 512)
    private String summary;

    @Column(name = "model_name", length = 96)
    private String modelName;

    @Column(name = "active_skill_code", length = 128)
    private String activeSkillCode;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at", nullable = false)
    private Instant endedAt;

    @Column(name = "elapsed_ms", nullable = false)
    private int elapsedMs;

    @Column(name = "model_call_count", nullable = false)
    private int modelCallCount;

    @Column(name = "tool_call_count", nullable = false)
    private int toolCallCount;

    @Column(name = "rag_context_count", nullable = false)
    private int ragContextCount;

    @Column(name = "knowledge_base_names_json", nullable = false, columnDefinition = "TEXT")
    private String knowledgeBaseNamesJson;

    @Column(name = "skill_names_json", nullable = false, columnDefinition = "TEXT")
    private String skillNamesJson;

    @Column(name = "nodes_json", nullable = false, columnDefinition = "TEXT")
    private String nodesJson;

    @Column(name = "detail_json", nullable = false, columnDefinition = "TEXT")
    private String detailJson;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "credential_id")
    private Long credentialId;

    @Column(name = "external_user_id", length = 128)
    private String externalUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentRunTraceEntity() {
    }

    public AgentRunTraceEntity(
            String traceId,
            String companyId,
            String userId,
            String sessionId,
            String agentId,
            String channel,
            String status,
            String title,
            String summary,
            String modelName,
            String activeSkillCode,
            Instant startedAt,
            Instant endedAt,
            int elapsedMs,
            int modelCallCount,
            int toolCallCount,
            int ragContextCount,
            String knowledgeBaseNamesJson,
            String skillNamesJson,
            String nodesJson,
            String detailJson,
            Instant createdAt) {
        this.traceId = traceId;
        this.companyId = companyId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.channel = channel;
        this.status = status;
        this.title = title;
        this.summary = summary;
        this.modelName = modelName;
        this.activeSkillCode = activeSkillCode;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.elapsedMs = elapsedMs;
        this.modelCallCount = modelCallCount;
        this.toolCallCount = toolCallCount;
        this.ragContextCount = ragContextCount;
        this.knowledgeBaseNamesJson = knowledgeBaseNamesJson;
        this.skillNamesJson = skillNamesJson;
        this.nodesJson = nodesJson;
        this.detailJson = detailJson;
        this.sourceType = "internal";
        this.createdAt = createdAt;
    }

    public String getTraceId() { return traceId; }
    public String getCompanyId() { return companyId; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public String getAgentId() { return agentId; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getTitle() { return title; }
    public String getSummary() { return summary; }
    public String getModelName() { return modelName; }
    public String getActiveSkillCode() { return activeSkillCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndedAt() { return endedAt; }
    public int getElapsedMs() { return elapsedMs; }
    public int getModelCallCount() { return modelCallCount; }
    public int getToolCallCount() { return toolCallCount; }
    public int getRagContextCount() { return ragContextCount; }
    public String getKnowledgeBaseNamesJson() { return knowledgeBaseNamesJson; }
    public String getSkillNamesJson() { return skillNamesJson; }
    public String getNodesJson() { return nodesJson; }
    public String getDetailJson() { return detailJson; }
    public String getSourceType() { return sourceType; }
    public String getRequestId() { return requestId; }
    public Long getCredentialId() { return credentialId; }
    public String getExternalUserId() { return externalUserId; }
    public Instant getCreatedAt() { return createdAt; }

    public void markOpenApi(String requestId, Long credentialId, String externalUserId) {
        this.sourceType = "open_api";
        this.requestId = requestId;
        this.credentialId = credentialId;
        this.externalUserId = externalUserId;
    }

    public void markExternalSource(String sourceType, String requestId, String externalUserId) {
        this.sourceType = sourceType;
        this.requestId = requestId;
        this.credentialId = null;
        this.externalUserId = externalUserId;
    }
}
