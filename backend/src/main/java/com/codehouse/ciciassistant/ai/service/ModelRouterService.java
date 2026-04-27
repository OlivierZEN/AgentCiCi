package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.model.domain.OrgModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.OrgModelConfigRepository;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {

    private final OrgModelConfigRepository repository;

    public ModelRouterService(OrgModelConfigRepository repository) {
        this.repository = repository;
    }

    public Map<String, String> route(String orgId, String sceneCode) {
        OrgModelConfigEntity config = repository.findByOrgIdAndSceneCode(orgId, sceneCode)
                .orElse(new OrgModelConfigEntity(orgId, sceneCode, "mock", "cici-default"));
        return Map.of("provider", config.getProvider(), "modelName", config.getModelName());
    }
}
