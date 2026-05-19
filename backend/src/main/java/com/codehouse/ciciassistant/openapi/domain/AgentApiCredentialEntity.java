package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_credential")
public class AgentApiCredentialEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";
    public static final String STATUS_REVOKED = "REVOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 32)
    private String publicId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 64)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, length = 128)
    private String keyHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "run_as_user_id", nullable = false, length = 64)
    private String runAsUserId;

    @Column(name = "allowed_ips_json", nullable = false, columnDefinition = "TEXT")
    private String allowedIpsJson;

    @Column(name = "scopes_json", nullable = false, columnDefinition = "TEXT")
    private String scopesJson;

    @Column(name = "rate_limit_per_minute", nullable = false)
    private int rateLimitPerMinute;

    @Column(name = "daily_quota", nullable = false)
    private int dailyQuota;

    @Column(name = "max_prompt_chars", nullable = false)
    private int maxPromptChars;

    @Column(name = "max_response_chars", nullable = false)
    private int maxResponseChars;

    @Column(name = "allow_stream", nullable = false)
    private boolean allowStream;

    @Column(name = "allow_trace_read", nullable = false)
    private boolean allowTraceRead;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentApiCredentialEntity() {
    }

    public AgentApiCredentialEntity(String publicId,
                                    String orgId,
                                    String agentId,
                                    String name,
                                    String keyPrefix,
                                    String keyHash,
                                    String runAsUserId,
                                    String allowedIpsJson,
                                    String scopesJson,
                                    int rateLimitPerMinute,
                                    int dailyQuota,
                                    int maxPromptChars,
                                    int maxResponseChars,
                                    boolean allowStream,
                                    boolean allowTraceRead,
                                    Instant expiresAt,
                                    String createdBy) {
        this.publicId = publicId;
        this.orgId = orgId;
        this.agentId = agentId;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = STATUS_ACTIVE;
        this.runAsUserId = runAsUserId;
        this.allowedIpsJson = allowedIpsJson;
        this.scopesJson = scopesJson;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.dailyQuota = dailyQuota;
        this.maxPromptChars = maxPromptChars;
        this.maxResponseChars = maxResponseChars;
        this.allowStream = allowStream;
        this.allowTraceRead = allowTraceRead;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getOrgId() { return orgId; }
    public String getAgentId() { return agentId; }
    public String getName() { return name; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getKeyHash() { return keyHash; }
    public String getStatus() { return status; }
    public String getRunAsUserId() { return runAsUserId; }
    public String getAllowedIpsJson() { return allowedIpsJson; }
    public String getScopesJson() { return scopesJson; }
    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public int getDailyQuota() { return dailyQuota; }
    public int getMaxPromptChars() { return maxPromptChars; }
    public int getMaxResponseChars() { return maxResponseChars; }
    public boolean isAllowStream() { return allowStream; }
    public boolean isAllowTraceRead() { return allowTraceRead; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getRevokedBy() { return revokedBy; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateMutableFields(String name,
                                    String runAsUserId,
                                    String allowedIpsJson,
                                    String scopesJson,
                                    int rateLimitPerMinute,
                                    int dailyQuota,
                                    int maxPromptChars,
                                    int maxResponseChars,
                                    boolean allowStream,
                                    boolean allowTraceRead,
                                    Instant expiresAt,
                                    String status) {
        this.name = name;
        this.runAsUserId = runAsUserId;
        this.allowedIpsJson = allowedIpsJson;
        this.scopesJson = scopesJson;
        this.rateLimitPerMinute = rateLimitPerMinute;
        this.dailyQuota = dailyQuota;
        this.maxPromptChars = maxPromptChars;
        this.maxResponseChars = maxResponseChars;
        this.allowStream = allowStream;
        this.allowTraceRead = allowTraceRead;
        this.expiresAt = expiresAt;
        this.status = status;
        if (!STATUS_REVOKED.equals(status)) {
            this.revokedBy = null;
            this.revokedAt = null;
        }
        this.updatedAt = Instant.now();
    }

    public void rotate(String publicId, String keyPrefix, String keyHash) {
        this.publicId = publicId;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = STATUS_ACTIVE;
        this.revokedBy = null;
        this.revokedAt = null;
        this.updatedAt = Instant.now();
    }

    public void revoke(String actorUserId) {
        this.status = STATUS_REVOKED;
        this.revokedBy = actorUserId;
        this.revokedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markUsed() {
        this.lastUsedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
