package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "org_embed_app_config")
public class OrgEmbedAppConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "allowed_origins_json", nullable = false, columnDefinition = "TEXT")
    private String allowedOriginsJson;

    @Column(name = "run_as_user_id", length = 64)
    private String runAsUserId;

    @Column(name = "source_bindings_json", nullable = false, columnDefinition = "TEXT")
    private String sourceBindingsJson;

    @Column(name = "scope_overrides_json", nullable = false, columnDefinition = "TEXT")
    private String scopeOverridesJson;

    @Column(name = "token_ttl_seconds", nullable = false)
    private int tokenTtlSeconds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrgEmbedAppConfigEntity() {
    }

    public OrgEmbedAppConfigEntity(String orgId,
                                   String appCode,
                                   boolean enabled,
                                   String allowedOriginsJson,
                                   String runAsUserId,
                                   String sourceBindingsJson,
                                   String scopeOverridesJson,
                                   int tokenTtlSeconds) {
        this.orgId = orgId;
        this.appCode = appCode;
        this.enabled = enabled;
        this.allowedOriginsJson = allowedOriginsJson;
        this.runAsUserId = runAsUserId;
        this.sourceBindingsJson = sourceBindingsJson;
        this.scopeOverridesJson = scopeOverridesJson;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOrgId() { return orgId; }
    public String getAppCode() { return appCode; }
    public boolean isEnabled() { return enabled; }
    public String getAllowedOriginsJson() { return allowedOriginsJson; }
    public String getRunAsUserId() { return runAsUserId; }
    public String getSourceBindingsJson() { return sourceBindingsJson; }
    public String getScopeOverridesJson() { return scopeOverridesJson; }
    public int getTokenTtlSeconds() { return tokenTtlSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(boolean enabled,
                       String allowedOriginsJson,
                       String runAsUserId,
                       String sourceBindingsJson,
                       String scopeOverridesJson,
                       int tokenTtlSeconds) {
        this.enabled = enabled;
        this.allowedOriginsJson = allowedOriginsJson;
        this.runAsUserId = runAsUserId;
        this.sourceBindingsJson = sourceBindingsJson;
        this.scopeOverridesJson = scopeOverridesJson;
        this.tokenTtlSeconds = tokenTtlSeconds;
        this.updatedAt = Instant.now();
    }
}
