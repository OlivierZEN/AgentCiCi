package com.codehouse.ciciassistant.model.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "org_model_config")
public class OrgModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "scene_code", nullable = false, length = 32)
    private String sceneCode;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    protected OrgModelConfigEntity() {
    }

    public OrgModelConfigEntity(String orgId, String sceneCode, String provider, String modelName) {
        this.orgId = orgId;
        this.sceneCode = sceneCode;
        this.provider = provider;
        this.modelName = modelName;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getSceneCode() {
        return sceneCode;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void update(String provider, String modelName) {
        this.provider = provider;
        this.modelName = modelName;
    }
}
