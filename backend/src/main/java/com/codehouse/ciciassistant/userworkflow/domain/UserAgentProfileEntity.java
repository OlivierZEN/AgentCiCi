package com.codehouse.ciciassistant.userworkflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_agent_profile")
public class UserAgentProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "locale", nullable = false, length = 32)
    private String locale;

    @Column(name = "notification_target_json", nullable = false, columnDefinition = "TEXT")
    private String notificationTargetJson;

    @Column(name = "personal_context_json", nullable = false, columnDefinition = "TEXT")
    private String personalContextJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAgentProfileEntity() {
    }

    public UserAgentProfileEntity(String orgId,
                                  String userId,
                                  String agentId,
                                  String timezone,
                                  String locale,
                                  String notificationTargetJson,
                                  String personalContextJson,
                                  boolean enabled) {
        this.orgId = orgId;
        this.userId = userId;
        this.agentId = agentId;
        this.timezone = timezone;
        this.locale = locale;
        this.notificationTargetJson = notificationTargetJson;
        this.personalContextJson = personalContextJson;
        this.enabled = enabled;
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

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public String getNotificationTargetJson() {
        return notificationTargetJson;
    }

    public String getPersonalContextJson() {
        return personalContextJson;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String timezone,
                       String locale,
                       String notificationTargetJson,
                       String personalContextJson,
                       boolean enabled) {
        this.timezone = timezone;
        this.locale = locale;
        this.notificationTargetJson = notificationTargetJson;
        this.personalContextJson = personalContextJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
