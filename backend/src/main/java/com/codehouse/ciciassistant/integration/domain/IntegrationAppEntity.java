package com.codehouse.ciciassistant.integration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "integration_app")
public class IntegrationAppEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "app_code", nullable = false, length = 64)
    private String appCode;

    @Column(name = "app_name", nullable = false, length = 128)
    private String appName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected IntegrationAppEntity() {
    }

    public IntegrationAppEntity(String companyId, String appCode, String appName, String description, boolean enabled, String configJson) {
        this.companyId = companyId;
        this.appCode = appCode;
        this.appName = appName;
        this.description = description;
        this.enabled = enabled;
        this.configJson = configJson == null || configJson.isBlank() ? "{}" : configJson;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getAppCode() { return appCode; }
    public String getAppName() { return appName; }
    public String getDescription() { return description; }
    public boolean isEnabled() { return enabled; }
    public String getConfigJson() { return configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAppName(String appName) { this.appName = appName; }
    public void setDescription(String description) { this.description = description; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setConfigJson(String configJson) { this.configJson = configJson == null || configJson.isBlank() ? "{}" : configJson; }
    public void touch() { this.updatedAt = Instant.now(); }
}
