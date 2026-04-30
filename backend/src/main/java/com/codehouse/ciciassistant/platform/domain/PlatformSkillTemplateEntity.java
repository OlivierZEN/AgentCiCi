package com.codehouse.ciciassistant.platform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_skill_template")
public class PlatformSkillTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "category", length = 64)
    private String category;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "current_version_no", nullable = false)
    private Integer currentVersionNo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformSkillTemplateEntity() {
    }

    public PlatformSkillTemplateEntity(String orgId,
                                       String templateCode,
                                       String name,
                                       String category,
                                       String description,
                                       String status,
                                       Integer currentVersionNo) {
        this.orgId = orgId;
        this.templateCode = templateCode;
        this.name = name;
        this.category = category;
        this.description = description;
        this.status = status;
        this.currentVersionNo = currentVersionNo;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Integer getCurrentVersionNo() {
        return currentVersionNo;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateMetadata(String name, String category, String description, String status) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void setCurrentVersionNo(Integer currentVersionNo) {
        this.currentVersionNo = currentVersionNo;
        this.updatedAt = Instant.now();
    }
}
