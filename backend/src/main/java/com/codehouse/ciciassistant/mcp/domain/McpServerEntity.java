package com.codehouse.ciciassistant.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "mcp_server")
public class McpServerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "transport_type", nullable = false, length = 32)
    private String transportType = "streamableHttp";

    @Column(nullable = false, length = 512)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String headers;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds = 60;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "tool_cache_json", columnDefinition = "TEXT")
    private String toolCacheJson;

    @Column(name = "tool_cache_count", nullable = false)
    private int toolCacheCount = 0;

    @Column(name = "tool_cache_status", nullable = false, length = 32)
    private String toolCacheStatus = "empty";

    @Column(name = "tool_cache_updated_at")
    private Instant toolCacheUpdatedAt;

    @Column(name = "tool_cache_error_message", columnDefinition = "TEXT")
    private String toolCacheErrorMessage;

    @Column(name = "tool_cache_last_attempt_at")
    private Instant toolCacheLastAttemptAt;

    @Column(name = "tool_cache_version", length = 64)
    private String toolCacheVersion;

    protected McpServerEntity() {}

    public McpServerEntity(String companyId, String name, String description,
                           String transportType, String url, String headers,
                           int timeoutSeconds) {
        this.companyId = companyId;
        this.name = name;
        this.description = description;
        this.transportType = transportType;
        this.url = url;
        this.headers = headers;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getTransportType() { return transportType; }
    public String getUrl() { return url; }
    public String getHeaders() { return headers; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getToolCacheJson() { return toolCacheJson; }
    public int getToolCacheCount() { return toolCacheCount; }
    public String getToolCacheStatus() { return toolCacheStatus; }
    public Instant getToolCacheUpdatedAt() { return toolCacheUpdatedAt; }
    public String getToolCacheErrorMessage() { return toolCacheErrorMessage; }
    public Instant getToolCacheLastAttemptAt() { return toolCacheLastAttemptAt; }
    public String getToolCacheVersion() { return toolCacheVersion; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setTransportType(String transportType) { this.transportType = transportType; }
    public void setUrl(String url) { this.url = url; }
    public void setHeaders(String headers) { this.headers = headers; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setToolCacheJson(String toolCacheJson) { this.toolCacheJson = toolCacheJson; }
    public void setToolCacheCount(int toolCacheCount) { this.toolCacheCount = toolCacheCount; }
    public void setToolCacheStatus(String toolCacheStatus) { this.toolCacheStatus = toolCacheStatus; }
    public void setToolCacheUpdatedAt(Instant toolCacheUpdatedAt) { this.toolCacheUpdatedAt = toolCacheUpdatedAt; }
    public void setToolCacheErrorMessage(String toolCacheErrorMessage) { this.toolCacheErrorMessage = toolCacheErrorMessage; }
    public void setToolCacheLastAttemptAt(Instant toolCacheLastAttemptAt) { this.toolCacheLastAttemptAt = toolCacheLastAttemptAt; }
    public void setToolCacheVersion(String toolCacheVersion) { this.toolCacheVersion = toolCacheVersion; }
    public void touch() { this.updatedAt = Instant.now(); }

    public boolean hasToolCache() {
        return toolCacheJson != null && !toolCacheJson.isBlank() && toolCacheCount > 0;
    }
}
