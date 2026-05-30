package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.model.service.ModelProviderService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ModelRouterService {

    private final ModelProviderService modelProviderService;

    public ModelRouterService(ModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    public Map<String, String> route(String orgId, String sceneCode) {
        List<Map<String, Object>> candidates = modelProviderService.agentBaseModels(orgId);
        if (candidates.isEmpty()) {
            throw new IllegalStateException("暂无平台可用模型，请联系平台运营启用模型厂商。");
        }
        Map<String, Object> selected = candidates.getFirst();
        return Map.of(
                "provider", String.valueOf(selected.get("providerCode")),
                "modelName", String.valueOf(selected.get("modelName")));
    }
}
