package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchSnapshotRepository;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.skill.service.SkillDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerWorkbenchServiceTest {

    @Test
    void assistantUsesRealAgentOrchestratorWithCustomerContext() {
        CustomerWorkbenchSnapshotRepository snapshotRepository = mock(CustomerWorkbenchSnapshotRepository.class);
        CustomerInteractionEventRepository eventRepository = mock(CustomerInteractionEventRepository.class);
        CustomerWorkbenchRecommendationRepository recommendationRepository = mock(CustomerWorkbenchRecommendationRepository.class);
        CloudccAccessTokenService cloudccAccessTokenService = mock(CloudccAccessTokenService.class);
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
        ChatOrchestratorService chatOrchestratorService = mock(ChatOrchestratorService.class);
        CustomerWorkbenchService service = new CustomerWorkbenchService(
                snapshotRepository,
                eventRepository,
                recommendationRepository,
                cloudccAccessTokenService,
                skillDefinitionService,
                agentDefinitionService,
                chatOrchestratorService,
                new ObjectMapper());

        String orgId = "org-demo";
        String userId = "user-demo";
        String accountId = "001-demo";
        String expectedSessionId = "customer-workbench:" + UUID.nameUUIDFromBytes(
                (userId + ":" + accountId).getBytes(StandardCharsets.UTF_8));
        CustomerWorkbenchSnapshotEntity snapshot = new CustomerWorkbenchSnapshotEntity(
                "cw-1",
                orgId,
                accountId,
                "北京智造科技有限公司",
                "张伟",
                "NEW",
                82,
                86,
                2,
                3,
                """
                        {"industry":"智能制造","risks":["报价条款仍需确认"],"nextActions":["补齐实施排期"],"tags":["MES集成"],"lastInteraction":"今天 09:30 微信沟通","stage":"方案评审"}
                        """);
        CustomerInteractionEventEntity event = new CustomerInteractionEventEntity(
                "evt-1",
                orgId,
                accountId,
                "contact-1",
                "WECHAT",
                Instant.parse("2026-07-09T01:30:00Z"),
                "微信沟通",
                "客户关注实施周期。",
                "客户关注实施周期和 MES 集成能力。",
                "NEUTRAL",
                "[\"实施周期\"]",
                "NEW_CUSTOMER");
        CustomerWorkbenchRecommendationEntity recommendation = new CustomerWorkbenchRecommendationEntity(
                "rec-1",
                orgId,
                accountId,
                "CREATE_TASK",
                "创建下一次跟进任务",
                "客户要求补充实施排期。",
                BigDecimal.valueOf(0.91),
                "{}");

        when(snapshotRepository.countByOrgId(orgId)).thenReturn(1L);
        when(snapshotRepository.findByOrgIdAndCrmAccountId(orgId, accountId)).thenReturn(Optional.of(snapshot));
        when(eventRepository.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(orgId, accountId)).thenReturn(List.of(event));
        when(recommendationRepository.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(orgId, accountId)).thenReturn(List.of(recommendation));
        when(recommendationRepository.countByOrgIdAndCrmAccountIdAndStatus(orgId, accountId, CustomerWorkbenchRecommendationEntity.STATUS_PENDING))
                .thenReturn(1L);
        when(cloudccAccessTokenService.getSessionContext(orgId, userId)).thenReturn(Optional.empty());
        when(chatOrchestratorService.chat(
                eq(orgId),
                eq(userId),
                any(),
                any(),
                anyList(),
                eq(CustomerWorkbenchService.ASSISTANT_AGENT_ID),
                eq(CustomerWorkbenchService.SKILL_CODE),
                anyMap()))
                .thenReturn(Map.of(
                        "answer", "真实智能体回复",
                        "agentId", CustomerWorkbenchService.ASSISTANT_AGENT_ID,
                        "sessionId", expectedSessionId,
                        "runId", "run-1",
                        "model", Map.of("modelName", "qwen3.5-plus"),
                        "resolvedSkills", List.of(CustomerWorkbenchService.SKILL_CODE),
                        "activeSkillCode", CustomerWorkbenchService.SKILL_CODE));

        Map<String, Object> result = service.assistant(
                orgId,
                userId,
                new CustomerWorkbenchService.AssistantCommand(accountId, "查看风险"));

        assertThat(result)
                .containsEntry("reply", "真实智能体回复")
                .containsEntry("agentId", CustomerWorkbenchService.ASSISTANT_AGENT_ID)
                .containsEntry("activeSkillCode", CustomerWorkbenchService.SKILL_CODE);
        verify(agentDefinitionService).warmupBuiltinAgents(orgId);
        verify(skillDefinitionService, times(2)).ensurePhaseOneDefaults(orgId);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatOrchestratorService).chat(
                eq(orgId),
                eq(userId),
                eq(expectedSessionId),
                promptCaptor.capture(),
                eq(List.of()),
                eq(CustomerWorkbenchService.ASSISTANT_AGENT_ID),
                eq(CustomerWorkbenchService.SKILL_CODE),
                eq(Map.of("source", "customer-workbench", "crmAccountId", accountId)));
        assertThat(promptCaptor.getValue())
                .contains("客户互动工作台上下文")
                .contains("北京智造科技有限公司")
                .contains("客户关注实施周期和 MES 集成能力")
                .contains("查看风险");
        assertThat(expectedSessionId).hasSizeLessThanOrEqualTo(64);
    }
}
