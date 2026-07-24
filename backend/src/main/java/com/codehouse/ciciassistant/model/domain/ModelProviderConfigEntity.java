package com.codehouse.ciciassistant.model.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "model_provider_config")
public class ModelProviderConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "provider_code", nullable = false, length = 64)
    private String providerCode;

    @Column(name = "provider_name", nullable = false, length = 128)
    private String providerName;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "api_base_url", nullable = false, length = 512)
    private String apiBaseUrl;

    @Column(name = "api_key", nullable = false, length = 512)
    private String apiKey = "";

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson = "{}";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ModelProviderConfigEntity() {
    }

    public ModelProviderConfigEntity(String companyId, String providerCode, String providerName,
                                     boolean enabled, String apiBaseUrl, String apiKey, String configJson) {
        this.companyId = companyId;
        this.providerCode = providerCode;
        this.providerName = providerName;
        this.enabled = enabled;
        this.apiBaseUrl = apiBaseUrl;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.configJson = configJson == null || configJson.isBlank() ? "{}" : configJson;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getProviderCode() { return providerCode; }
    public String getProviderName() { return providerName; }
    public boolean isEnabled() { return enabled; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public String getApiKey() { return apiKey; }
    public String getConfigJson() { return configJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setProviderName(String providerName) { this.providerName = providerName; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setApiBaseUrl(String apiBaseUrl) { this.apiBaseUrl = apiBaseUrl; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public void setConfigJson(String configJson) { this.configJson = configJson == null || configJson.isBlank() ? "{}" : configJson; }
    public void touch() { this.updatedAt = Instant.now(); }
}
