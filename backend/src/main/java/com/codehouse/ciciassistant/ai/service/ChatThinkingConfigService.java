package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import org.springframework.stereotype.Service;

@Service
public class ChatThinkingConfigService {

    public static final String SCENE_CODE = "chat_show_thinking";

    private final CompanyModelConfigRepository repository;

    public ChatThinkingConfigService(CompanyModelConfigRepository repository) {
        this.repository = repository;
    }

    public boolean isEnabled(String companyId) {
        CompanyModelConfigEntity entity = repository.findByCompanyIdAndSceneCode(companyId, SCENE_CODE).orElse(null);
        if (entity == null) {
            return false;
        }
        String value = entity.getModelName();
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase();
        return "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }
}
