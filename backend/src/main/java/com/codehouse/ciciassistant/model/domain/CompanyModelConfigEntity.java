package com.codehouse.ciciassistant.model.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "company_model_config")
public class CompanyModelConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "scene_code", nullable = false, length = 32)
    private String sceneCode;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    protected CompanyModelConfigEntity() {
    }

    public CompanyModelConfigEntity(String companyId, String sceneCode, String provider, String modelName) {
        this.companyId = companyId;
        this.sceneCode = sceneCode;
        this.provider = provider;
        this.modelName = modelName;
    }

    public String getCompanyId() {
        return companyId;
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
