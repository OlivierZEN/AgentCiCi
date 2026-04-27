package com.codehouse.ciciassistant.feishu.service;

import com.codehouse.ciciassistant.integration.domain.IntegrationAppEntity;
import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotConfigService {

    private final IntegrationAppRepository integrationAppRepository;
    private final ObjectMapper objectMapper;

    public FeishuBotConfigService(IntegrationAppRepository integrationAppRepository, ObjectMapper objectMapper) {
        this.integrationAppRepository = integrationAppRepository;
        this.objectMapper = objectMapper;
    }

    public Optional<FeishuBotConfig> getEnabledConfig(String orgId) {
        return integrationAppRepository.findByOrgIdAndAppCode(orgId, IntegrationAppService.APP_CODE_FEISHU_BOT)
                .map(this::toConfig)
                .filter(FeishuBotConfig::ready);
    }

    public List<FeishuBotConfig> listEnabledConfigs() {
        return integrationAppRepository.findByAppCodeAndEnabledTrueOrderByIdAsc(IntegrationAppService.APP_CODE_FEISHU_BOT)
                .stream()
                .map(this::toConfig)
                .filter(FeishuBotConfig::ready)
                .toList();
    }

    private FeishuBotConfig toConfig(IntegrationAppEntity entity) {
        Map<String, Object> raw = readJsonToMap(entity.getConfigJson());
        String appId = asText(raw.get("appId"));
        String appSecret = asText(raw.get("appSecret"));
        String defaultAgentCode = asText(raw.get("defaultAgentCode"));
        String pairingCommandHint = asText(raw.get("pairingCommandHint"));
        return new FeishuBotConfig(
                entity.getOrgId(),
                entity.isEnabled(),
                appId,
                appSecret,
                defaultAgentCode.isBlank() ? "cici" : defaultAgentCode,
                pairingCommandHint.isBlank() ? "配对" : pairingCommandHint
        );
    }

    private Map<String, Object> readJsonToMap(String json) {
        try {
            if (json == null || json.isBlank()) {
                return new HashMap<>();
            }
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record FeishuBotConfig(
            String orgId,
            boolean enabled,
            String appId,
            String appSecret,
            String defaultAgentCode,
            String pairingCommandHint
    ) {
        public boolean ready() {
            return enabled && !appId.isBlank() && !appSecret.isBlank();
        }
    }
}
