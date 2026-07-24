package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_eval_suite")
public class AgentEvalSuiteEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String GATE_MODE_BLOCKING = "BLOCKING";
    public static final String GATE_MODE_WARN_ONLY = "WARN_ONLY";
    public static final String SCOPE_PLATFORM_CORE = "PLATFORM_CORE";
    public static final String SCOPE_APP_STANDARD = "APP_STANDARD";
    public static final String SCOPE_INDUSTRY_PACK = "INDUSTRY_PACK";
    public static final String SCOPE_TENANT_PRIVATE = "TENANT_PRIVATE";
    public static final String VISIBILITY_SEALED = "SEALED";
    public static final String VISIBILITY_AUTHORIZED = "AUTHORIZED";
    public static final String VISIBILITY_TENANT_ONLY = "TENANT_ONLY";
    public static final String RELEASE_DRAFT = "DRAFT";
    public static final String RELEASE_PUBLISHED = "PUBLISHED";
    public static final String RELEASE_ARCHIVED = "ARCHIVED";
    public static final String PLATFORM_ORG_ID = "__platform__";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "gate_mode", nullable = false, length = 32)
    private String gateMode;

    @Column(name = "min_pass_rate", nullable = false)
    private Double minPassRate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "visibility", nullable = false, length = 32)
    private String visibility;

    @Column(name = "release_status", nullable = false, length = 32)
    private String releaseStatus;

    @Column(name = "template_code", length = 128)
    private String templateCode;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Column(name = "app_code", length = 128)
    private String appCode;

    @Column(name = "industry_code", length = 128)
    private String industryCode;

    @Column(name = "hidden_results", nullable = false)
    private boolean hiddenResults;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory;

    @Column(name = "created_by", length = 128)
    private String createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected AgentEvalSuiteEntity() {
    }

    public AgentEvalSuiteEntity(String companyId,
                                String agentId,
                                String name,
                                String description,
                                String gateMode,
                                Double minPassRate) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.agentId = agentId;
        this.name = name;
        this.description = description;
        this.status = STATUS_ACTIVE;
        this.gateMode = gateMode == null || gateMode.isBlank() ? GATE_MODE_BLOCKING : gateMode;
        this.minPassRate = minPassRate == null ? 1.0d : minPassRate;
        this.scopeType = SCOPE_TENANT_PRIVATE;
        this.visibility = VISIBILITY_TENANT_ONLY;
        this.releaseStatus = RELEASE_PUBLISHED;
        this.versionNo = 1;
        this.hiddenResults = false;
        this.mandatory = false;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public AgentEvalSuiteEntity(String companyId,
                                String agentId,
                                String name,
                                String description,
                                String gateMode,
                                Double minPassRate,
                                String scopeType,
                                String visibility,
                                String releaseStatus,
                                String templateCode,
                                Integer versionNo,
                                String appCode,
                                String industryCode,
                                boolean hiddenResults,
                                boolean mandatory,
                                String createdBy) {
        this(companyId, agentId, name, description, gateMode, minPassRate);
        this.scopeType = scopeType;
        this.visibility = visibility;
        this.releaseStatus = releaseStatus;
        this.templateCode = templateCode;
        this.versionNo = versionNo == null ? 1 : versionNo;
        this.appCode = appCode;
        this.industryCode = industryCode;
        this.hiddenResults = hiddenResults;
        this.mandatory = mandatory;
        this.createdBy = createdBy;
        this.publishedAt = RELEASE_PUBLISHED.equals(releaseStatus) ? Instant.now() : null;
    }

    public Long getId() { return id; }

    public String getCompanyId() { return companyId; }

    public String getAgentId() { return agentId; }

    public String getName() { return name; }

    public String getDescription() { return description; }

    public String getStatus() { return status; }

    public String getGateMode() { return gateMode; }

    public Double getMinPassRate() { return minPassRate; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    public String getScopeType() { return scopeType; }

    public String getVisibility() { return visibility; }

    public String getReleaseStatus() { return releaseStatus; }

    public String getTemplateCode() { return templateCode; }

    public Integer getVersionNo() { return versionNo; }

    public String getAppCode() { return appCode; }

    public String getIndustryCode() { return industryCode; }

    public boolean isHiddenResults() { return hiddenResults; }

    public boolean isMandatory() { return mandatory; }

    public String getCreatedBy() { return createdBy; }

    public Instant getPublishedAt() { return publishedAt; }

    public boolean isPlatformOwned() {
        return !SCOPE_TENANT_PRIVATE.equals(scopeType);
    }

    public void updateDraft(String name,
                            String description,
                            String gateMode,
                            Double minPassRate,
                            String visibility,
                            String appCode,
                            String industryCode,
                            boolean hiddenResults,
                            boolean mandatory) {
        if (!RELEASE_DRAFT.equals(releaseStatus)) {
            throw new IllegalStateException("Published evaluation suite is immutable");
        }
        this.name = name;
        this.description = description;
        this.gateMode = gateMode;
        this.minPassRate = minPassRate;
        this.visibility = visibility;
        this.appCode = appCode;
        this.industryCode = industryCode;
        this.hiddenResults = hiddenResults;
        this.mandatory = mandatory;
        this.updatedAt = Instant.now();
    }

    public void publish() {
        if (!RELEASE_DRAFT.equals(releaseStatus)) {
            throw new IllegalStateException("Only draft evaluation suites can be published");
        }
        this.releaseStatus = RELEASE_PUBLISHED;
        this.publishedAt = Instant.now();
        this.updatedAt = this.publishedAt;
    }

    public void archive() {
        this.status = STATUS_ARCHIVED;
        this.releaseStatus = RELEASE_ARCHIVED;
        this.updatedAt = Instant.now();
    }
}
