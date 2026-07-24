package com.codehouse.ciciassistant.agent.api;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/public/agents")
public class PublicAgentAvatarController {

    private final AgentDefinitionRepository agentDefinitionRepository;

    public PublicAgentAvatarController(AgentDefinitionRepository agentDefinitionRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    @GetMapping("/avatars")
    public ApiResponse<List<Map<String, Object>>> listAvatars(@RequestParam("companyId") @NotBlank String companyId) {
        List<Map<String, Object>> avatars = agentDefinitionRepository
                .findTop24ByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(companyId.trim())
                .stream()
                .filter(item -> item.isBuiltin() || item.getPublishedVersionId() != null)
                .filter(item -> item.getAvatarBase64() != null && !item.getAvatarBase64().isBlank())
                .map(this::toAvatarPayload)
                .toList();
        return ApiResponse.ok(avatars);
    }

    private Map<String, Object> toAvatarPayload(AgentDefinitionEntity item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", item.getAgentId());
        payload.put("name", item.getName());
        payload.put("avatarBase64", item.getAvatarBase64());
        payload.put("builtin", item.isBuiltin());
        return payload;
    }
}
