package com.codehouse.ciciassistant.userworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_workflow_trigger")
public class UserWorkflowTriggerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "routine_key", nullable = false, length = 128)
    private String routineKey;

    @Column(name = "routine_name", nullable = false, length = 256)
    private String routineName;

    @Column(name = "trigger_type", nullable = false, length = 32)
    private String triggerType;

    @Column(name = "cron_expr", length = 64)
    private String cronExpr;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "interval_seconds")
    private Integer intervalSeconds;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "event_filter_json", nullable = false, columnDefinition = "TEXT")
    private String eventFilterJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserWorkflowTriggerEntity() {
    }

    public UserWorkflowTriggerEntity(String orgId,
                                     String userId,
                                     String agentId,
                                     Long versionId,
                                     String routineKey,
                                     String routineName,
                                     String triggerType,
                                     String cronExpr,
                                     String timezone,
                                     Integer intervalSeconds,
                                     String eventType,
                                     String eventFilterJson,
                                     boolean enabled,
                                     Instant nextFireAt) {
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.versionId = versionId;
        this.routineKey = routineKey;
        this.routineName = routineName;
        this.triggerType = triggerType;
        this.cronExpr = cronExpr;
        this.timezone = timezone;
        this.intervalSeconds = intervalSeconds;
        this.eventType = eventType;
        this.eventFilterJson = eventFilterJson;
        this.enabled = enabled;
        this.nextFireAt = nextFireAt;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getUserId() {
        return userId;
    }

    public String getAgentId() {
        return agentId;
    }

    public Long getVersionId() {
        return versionId;
    }

    public String getRoutineKey() {
        return routineKey;
    }

    public String getRoutineName() {
        return routineName;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public String getTimezone() {
        return timezone;
    }

    public Integer getIntervalSeconds() {
        return intervalSeconds;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventFilterJson() {
        return eventFilterJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getNextFireAt() {
        return nextFireAt;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateEnabled(boolean enabled, Instant nextFireAt) {
        this.enabled = enabled;
        this.nextFireAt = nextFireAt;
        this.updatedAt = Instant.now();
    }

    public void markTriggered(Instant triggeredAt, Instant nextFireAt) {
        this.lastTriggeredAt = triggeredAt;
        this.nextFireAt = nextFireAt;
        this.updatedAt = Instant.now();
    }
}
