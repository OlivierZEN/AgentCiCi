package com.codehouse.ciciassistant.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_workflow_skill_ref")
public class AgentWorkflowSkillRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "workflow_version_id", nullable = false)
    private Long workflowVersionId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Column(name = "skill_version_id")
    private Long skillVersionId;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "template_version_no")
    private Integer templateVersionNo;

    @Column(name = "reference_mode", nullable = false, length = 32)
    private String referenceMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentWorkflowSkillRefEntity() {
    }

    public AgentWorkflowSkillRefEntity(String orgId,
                                       Long workflowVersionId,
                                       Long skillId,
                                       Long skillVersionId,
                                       String templateCode,
                                       Integer templateVersionNo,
                                       String referenceMode) {
        this.orgId = orgId;
        this.workflowVersionId = workflowVersionId;
        this.skillId = skillId;
        this.skillVersionId = skillVersionId;
        this.templateCode = templateCode;
        this.templateVersionNo = templateVersionNo;
        this.referenceMode = referenceMode;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public Long getWorkflowVersionId() {
        return workflowVersionId;
    }

    public Long getSkillId() {
        return skillId;
    }

    public Long getSkillVersionId() {
        return skillVersionId;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public Integer getTemplateVersionNo() {
        return templateVersionNo;
    }

    public String getReferenceMode() {
        return referenceMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
