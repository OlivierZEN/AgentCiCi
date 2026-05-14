package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "embed_app_definition")
public class EmbedAppDefinitionEntity {

    public static final String STATUS_ENABLED = "ENABLED";

    @Id
    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "embed_mode", nullable = false, length = 32)
    private String embedMode;

    @Column(name = "stable_sdk_url", nullable = false, length = 256)
    private String stableSdkUrl;

    @Column(name = "versioned_sdk_url", nullable = false, length = 256)
    private String versionedSdkUrl;

    @Column(name = "embed_url", nullable = false, length = 256)
    private String embedUrl;

    @Column(name = "required_scopes_json", nullable = false, columnDefinition = "TEXT")
    private String requiredScopesJson;

    @Column(name = "supported_sources_json", nullable = false, columnDefinition = "TEXT")
    private String supportedSourcesJson;

    @Column(name = "default_token_ttl_seconds", nullable = false)
    private int defaultTokenTtlSeconds;

    @Column(name = "doc_json", nullable = false, columnDefinition = "TEXT")
    private String docJson;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmbedAppDefinitionEntity() {
    }

    public String getAppCode() { return appCode; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getEmbedMode() { return embedMode; }
    public String getStableSdkUrl() { return stableSdkUrl; }
    public String getVersionedSdkUrl() { return versionedSdkUrl; }
    public String getEmbedUrl() { return embedUrl; }
    public String getRequiredScopesJson() { return requiredScopesJson; }
    public String getSupportedSourcesJson() { return supportedSourcesJson; }
    public int getDefaultTokenTtlSeconds() { return defaultTokenTtlSeconds; }
    public String getDocJson() { return docJson; }
    public String getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
