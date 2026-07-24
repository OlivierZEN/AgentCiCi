package com.codehouse.ciciassistant.feishu.service;

import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotConversationService {

    private final ChatOrchestratorService chatOrchestratorService;

    public FeishuBotConversationService(ChatOrchestratorService chatOrchestratorService) {
        this.chatOrchestratorService = chatOrchestratorService;
    }

    public String ask(FeishuBotBindingEntity binding, String tenantKey, String chatId, String question) {
        String sessionId = "feishu:" + tenantKey + ":" + chatId;
        Map<String, Object> result = chatOrchestratorService.chat(
                binding.getCompanyId(),
                binding.getUserId(),
                sessionId,
                question,
                List.of(),
                binding.getAgentCode(),
                null
        );
        Object answer = result.get("answer");
        return answer == null ? "" : String.valueOf(answer);
    }
}
