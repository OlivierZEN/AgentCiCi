package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.customer.domain.CustomerCrmWriteAuditEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerCrmWriteAuditRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerRecommendationFeedbackRepository;
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
        CustomerRecommendationFeedbackRepository recommendationFeedbackRepository = mock(CustomerRecommendationFeedbackRepository.class);
        CustomerCrmWriteAuditRepository writeAuditRepository = mock(CustomerCrmWriteAuditRepository.class);
        CustomerCrmProjectionService crmProjectionService = mock(CustomerCrmProjectionService.class);
        CloudccOpenApiService cloudccOpenApiService = mock(CloudccOpenApiService.class);
        CloudccAccessTokenService cloudccAccessTokenService = mock(CloudccAccessTokenService.class);
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
        ChatOrchestratorService chatOrchestratorService = mock(ChatOrchestratorService.class);
        CustomerWorkbenchService service = new CustomerWorkbenchService(
                snapshotRepository,
                eventRepository,
                recommendationRepository,
                recommendationFeedbackRepository,
                writeAuditRepository,
                crmProjectionService,
                cloudccOpenApiService,
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

    @Test
    void recommendationRequiresConfirmationBeforeApplying() {
        CustomerWorkbenchRecommendationEntity recommendation = new CustomerWorkbenchRecommendationEntity(
                "rec-state", "org-demo", "001-demo", "CREATE_TASK", "跟进客户", "存在待办",
                BigDecimal.valueOf(.9), "{}");
        recommendation.configureTarget("Task", "", "[]");

        assertThatThrownBy(() -> recommendation.confirm("user-demo"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("先采纳");
        assertThatThrownBy(recommendation::markApplying)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("确认");
        recommendation.accept();
        recommendation.confirm("user-demo");
        recommendation.markApplying();
        recommendation.apply("task-001");

        assertThat(recommendation.getStatus()).isEqualTo(CustomerWorkbenchRecommendationEntity.STATUS_APPLIED);
        assertThat(recommendation.getAppliedCrmId()).isEqualTo("task-001");
        assertThat(recommendation.getConfirmedBy()).isEqualTo("user-demo");
    }

    @Test
    void confirmedRecommendationWritesAndReadsBackWithCurrentUserToken() {
        CustomerWorkbenchSnapshotRepository snapshotRepository = mock(CustomerWorkbenchSnapshotRepository.class);
        CustomerInteractionEventRepository eventRepository = mock(CustomerInteractionEventRepository.class);
        CustomerWorkbenchRecommendationRepository recommendationRepository = mock(CustomerWorkbenchRecommendationRepository.class);
        CustomerRecommendationFeedbackRepository recommendationFeedbackRepository = mock(CustomerRecommendationFeedbackRepository.class);
        CustomerCrmWriteAuditRepository writeAuditRepository = mock(CustomerCrmWriteAuditRepository.class);
        CustomerCrmProjectionService crmProjectionService = mock(CustomerCrmProjectionService.class);
        CloudccOpenApiService cloudccOpenApiService = mock(CloudccOpenApiService.class);
        CloudccAccessTokenService cloudccAccessTokenService = mock(CloudccAccessTokenService.class);
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
        ChatOrchestratorService chatOrchestratorService = mock(ChatOrchestratorService.class);
        CustomerWorkbenchService service = new CustomerWorkbenchService(
                snapshotRepository, eventRepository, recommendationRepository, recommendationFeedbackRepository, writeAuditRepository,
                crmProjectionService, cloudccOpenApiService, cloudccAccessTokenService, skillDefinitionService,
                agentDefinitionService, chatOrchestratorService, new ObjectMapper());
        CustomerWorkbenchRecommendationEntity recommendation = new CustomerWorkbenchRecommendationEntity(
                "rec-write", "org-demo", "001-demo", "CREATE_TASK", "创建跟进任务", "客户要求三日内反馈",
                BigDecimal.valueOf(.92), """
                        {"subject":"反馈方案","expiredate":"2026-07-13"}
                        """);
        recommendation.configureTarget("Task", "", "[]");
        recommendation.accept();
        recommendation.confirm("user-demo");

        when(recommendationRepository.findByOrgIdAndPublicId("org-demo", "rec-write")).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudccAccessTokenService.getSessionContext("org-demo", "user-demo")).thenReturn(Optional.of(
                new CloudccAccessTokenService.CloudccSessionContext("token", "https://ap6.lightning.cloudcc.cn", "")));
        when(writeAuditRepository.findByOrgIdAndUserIdAndIdempotencyKey(eq("org-demo"), eq("user-demo"), any()))
                .thenReturn(Optional.empty());
        when(cloudccOpenApiService.writeRecords(eq("org-demo"), eq("user-demo"), eq("INSERT"), eq("Task"), anyList()))
                .thenReturn(new CloudccOpenApiService.WriteResult("insertWithRoleRight", "Task", List.of("task-001"), "0", "success"));
        when(cloudccOpenApiService.queryRecordById(eq("org-demo"), eq("user-demo"), eq("Task"), any(), eq("task-001")))
                .thenReturn(Optional.of(Map.of("id", "task-001", "subject", "反馈方案")));
        when(writeAuditRepository.save(any(CustomerCrmWriteAuditEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> result = service.applyRecommendation("org-demo", "user-demo", "rec-write");

        assertThat(result)
                .containsEntry("status", CustomerWorkbenchRecommendationEntity.STATUS_APPLIED)
                .containsEntry("appliedCrmId", "task-001")
                .containsEntry("verified", true)
                .containsEntry("writeMode", "CLOUDCC_LIVE");
        verify(cloudccOpenApiService).writeRecords(eq("org-demo"), eq("user-demo"), eq("INSERT"), eq("Task"), anyList());
        verify(crmProjectionService).detail("org-demo", "user-demo", "001-demo", false);
        verify(writeAuditRepository, times(2)).save(any(CustomerCrmWriteAuditEntity.class));
        verify(crmProjectionService).invalidate("org-demo", "user-demo");
    }

    @Test
    void applyingRecommendationRecoversRemoteRecordFromFailedAuditWithoutDuplicateWrite() {
        CustomerWorkbenchSnapshotRepository snapshotRepository = mock(CustomerWorkbenchSnapshotRepository.class);
        CustomerInteractionEventRepository eventRepository = mock(CustomerInteractionEventRepository.class);
        CustomerWorkbenchRecommendationRepository recommendationRepository = mock(CustomerWorkbenchRecommendationRepository.class);
        CustomerRecommendationFeedbackRepository recommendationFeedbackRepository = mock(CustomerRecommendationFeedbackRepository.class);
        CustomerCrmWriteAuditRepository writeAuditRepository = mock(CustomerCrmWriteAuditRepository.class);
        CustomerCrmProjectionService crmProjectionService = mock(CustomerCrmProjectionService.class);
        CloudccOpenApiService cloudccOpenApiService = mock(CloudccOpenApiService.class);
        CloudccAccessTokenService cloudccAccessTokenService = mock(CloudccAccessTokenService.class);
        CustomerWorkbenchService service = new CustomerWorkbenchService(
                snapshotRepository, eventRepository, recommendationRepository, recommendationFeedbackRepository, writeAuditRepository,
                crmProjectionService, cloudccOpenApiService, cloudccAccessTokenService, mock(SkillDefinitionService.class),
                mock(AgentDefinitionService.class), mock(ChatOrchestratorService.class), new ObjectMapper());
        CustomerWorkbenchRecommendationEntity recommendation = new CustomerWorkbenchRecommendationEntity(
                "rec-recover", "org-demo", "001-demo", "CREATE_TASK", "创建跟进任务", "客户要求反馈",
                BigDecimal.valueOf(.92), "{\"subject\":\"反馈方案\"}");
        recommendation.configureTarget("Task", "", "[]");
        recommendation.accept();
        recommendation.confirm("user-demo");
        recommendation.markApplying();
        CustomerCrmWriteAuditEntity audit = new CustomerCrmWriteAuditEntity(
                "audit-recover", "org-demo", "user-demo", "rec-recover", "rec-recover:hash",
                "Task", "INSERT", "FAILED", "hash", "task-existing", "OptimisticLock", "stale",
                "{}", "{}");

        when(recommendationRepository.findByOrgIdAndPublicId("org-demo", "rec-recover")).thenReturn(Optional.of(recommendation));
        when(recommendationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudccAccessTokenService.getSessionContext("org-demo", "user-demo")).thenReturn(Optional.of(
                new CloudccAccessTokenService.CloudccSessionContext("token", "https://ap6.lightning.cloudcc.cn", "")));
        when(writeAuditRepository.findByOrgIdAndUserIdAndIdempotencyKey(eq("org-demo"), eq("user-demo"), any()))
                .thenReturn(Optional.of(audit));
        when(writeAuditRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(cloudccOpenApiService.queryRecordById(eq("org-demo"), eq("user-demo"), eq("Task"), any(), eq("task-existing")))
                .thenReturn(Optional.of(Map.of("id", "task-existing", "subject", "反馈方案")));

        Map<String, Object> result = service.applyRecommendation("org-demo", "user-demo", "rec-recover");

        assertThat(result)
                .containsEntry("status", CustomerWorkbenchRecommendationEntity.STATUS_APPLIED)
                .containsEntry("appliedCrmId", "task-existing")
                .containsEntry("idempotent", true)
                .containsEntry("verified", true);
        verify(cloudccOpenApiService, never()).writeRecords(anyString(), anyString(), anyString(), anyString(), anyList());
        verify(writeAuditRepository).save(audit);
        assertThat(audit.getStatus()).isEqualTo("SUCCEEDED");
    }

    @Test
    void interactionIngestionDeduplicatesAndAssistantHistoryHidesInternalContext() {
        CustomerWorkbenchSnapshotRepository snapshotRepository = mock(CustomerWorkbenchSnapshotRepository.class);
        CustomerInteractionEventRepository eventRepository = mock(CustomerInteractionEventRepository.class);
        CustomerWorkbenchRecommendationRepository recommendationRepository = mock(CustomerWorkbenchRecommendationRepository.class);
        CustomerRecommendationFeedbackRepository recommendationFeedbackRepository = mock(CustomerRecommendationFeedbackRepository.class);
        CustomerCrmWriteAuditRepository writeAuditRepository = mock(CustomerCrmWriteAuditRepository.class);
        CustomerCrmProjectionService crmProjectionService = mock(CustomerCrmProjectionService.class);
        CloudccOpenApiService cloudccOpenApiService = mock(CloudccOpenApiService.class);
        CloudccAccessTokenService cloudccAccessTokenService = mock(CloudccAccessTokenService.class);
        SkillDefinitionService skillDefinitionService = mock(SkillDefinitionService.class);
        AgentDefinitionService agentDefinitionService = mock(AgentDefinitionService.class);
        ChatOrchestratorService chatOrchestratorService = mock(ChatOrchestratorService.class);
        CustomerWorkbenchService service = new CustomerWorkbenchService(
                snapshotRepository, eventRepository, recommendationRepository, recommendationFeedbackRepository, writeAuditRepository,
                crmProjectionService, cloudccOpenApiService, cloudccAccessTokenService, skillDefinitionService,
                agentDefinitionService, chatOrchestratorService, new ObjectMapper());
        CustomerWorkbenchSnapshotEntity snapshot = new CustomerWorkbenchSnapshotEntity(
                "cw-history", "org-demo", "001-demo", "客户甲", "王销售", "NEW",
                80, 70, 1, 1, "{\"stage\":\"需求确认\",\"risks\":[\"预算待确认\"]}");
        when(cloudccAccessTokenService.getSessionContext("org-demo", "user-demo")).thenReturn(Optional.empty());
        when(snapshotRepository.findByOrgIdAndCrmAccountId("org-demo", "001-demo")).thenReturn(Optional.of(snapshot));
        when(chatOrchestratorService.sessionMessages(eq("org-demo"), eq("user-demo"), anyString())).thenReturn(List.of(
                Map.of("role", "user", "content", "[客户互动工作台上下文]\n内部客户 JSON\n[用户问题]\n请查看风险", "createdAt", "2026-07-10T10:00:00Z"),
                Map.of("role", "assistant", "content", "存在预算风险。", "createdAt", "2026-07-10T10:00:01Z")));
        when(eventRepository.findByOrgIdAndPublicId(eq("org-demo"), anyString())).thenReturn(Optional.empty());
        when(eventRepository.save(any(CustomerInteractionEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerWorkbenchRecommendationEntity feedbackRecommendation = new CustomerWorkbenchRecommendationEntity(
                "rec-feedback", "org-demo", "001-demo", "CREATE_TASK", "跟进客户", "预算需要确认",
                BigDecimal.valueOf(.8), "{}");
        when(recommendationRepository.findByOrgIdAndPublicId("org-demo", "rec-feedback"))
                .thenReturn(Optional.of(feedbackRecommendation));
        when(recommendationFeedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.assistantHistory("org-demo", "user-demo", "001-demo"))
                .extracting(item -> item.get("content"))
                .containsExactly("请查看风险", "存在预算风险。");
        Map<String, Object> saved = service.saveInteraction("org-demo", "user-demo", "001-demo",
                new CustomerWorkbenchService.InteractionCommand("WECHAT", "预算沟通", "客户确认预算需要财务负责人再次审批。", "2026-07-10T09:30:00Z"));

        assertThat(saved).containsEntry("sourceType", "WECHAT").containsEntry("deduplicated", false);
        assertThat(service.recommendationFeedback("org-demo", "user-demo", "rec-feedback",
                new CustomerWorkbenchService.RecommendationFeedbackCommand("HELPFUL", "建议明确")))
                .containsEntry("rating", "HELPFUL").containsEntry("comment", "建议明确");
        verify(eventRepository).save(any(CustomerInteractionEventEntity.class));
    }
}
