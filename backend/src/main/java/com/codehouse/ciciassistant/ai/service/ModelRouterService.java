package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.model.service.ModelProviderService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {

    private final ModelProviderService modelProviderService;

    public ModelRouterService(ModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    public Map<String, String> route(String companyId, String sceneCode) {
        return route(companyId, sceneCode, null);
    }

    public Map<String, String> route(String companyId, String sceneCode, String preferredModelName) {
        return modelProviderService.resolveRuntimeModelRoute(companyId, sceneCode, preferredModelName);
    }
}
