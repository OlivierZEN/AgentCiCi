package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_task_event")
public class AgentTaskEventEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "company_id", nullable = false, length = 64) private String companyId;
    @Column(name = "run_id", nullable = false) private Long runId;
    @Column(name = "step_id") private Long stepId;
    @Column(name = "event_type", nullable = false, length = 48) private String eventType;
    @Column(name = "payload_redacted_json", nullable = false, columnDefinition = "TEXT") private String payloadRedactedJson;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    protected AgentTaskEventEntity() { }
    public AgentTaskEventEntity(String companyId, long runId, Long stepId, String eventType, String payloadRedactedJson, Instant occurredAt) {
        this.companyId = companyId; this.runId = runId; this.stepId = stepId; this.eventType = eventType;
        this.payloadRedactedJson = payloadRedactedJson; this.occurredAt = occurredAt;
    }
    public Long getId() { return id; } public String getCompanyId() { return companyId; } public Long getRunId() { return runId; }
    public Long getStepId() { return stepId; } public String getEventType() { return eventType; }
    public String getPayloadRedactedJson() { return payloadRedactedJson; } public Instant getOccurredAt() { return occurredAt; }
}
