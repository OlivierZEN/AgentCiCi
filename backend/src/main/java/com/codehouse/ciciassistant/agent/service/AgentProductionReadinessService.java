package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentKnowledgeBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentRuntimeScheduleTriggerEntity;
import com.codehouse.ciciassistant.agent.domain.AgentRuntimeScheduleTriggerRepository;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentToolBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentProductionReadinessService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentKnowledgeBindingRepository agentKnowledgeBindingRepository;
    private final AgentToolBindingRepository agentToolBindingRepository;
    private final AgentChannelBindingRepository agentChannelBindingRepository;
    private final AgentRuntimeScheduleTriggerRepository scheduleTriggerRepository;
    private final AgentApiCredentialRepository apiCredentialRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ModelProviderService modelProviderService;
    private final AgentEvaluationService agentEvaluationService;

    public AgentProductionReadinessService(AgentDefinitionRepository agentDefinitionRepository,
                                           AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                           AgentKnowledgeBindingRepository agentKnowledgeBindingRepository,
                                           AgentToolBindingRepository agentToolBindingRepository,
                                           AgentChannelBindingRepository agentChannelBindingRepository,
                                           AgentRuntimeScheduleTriggerRepository scheduleTriggerRepository,
                                           AgentApiCredentialRepository apiCredentialRepository,
                                           KnowledgeBaseRepository knowledgeBaseRepository,
                                           ModelProviderService modelProviderService,
                                           AgentEvaluationService agentEvaluationService) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentKnowledgeBindingRepository = agentKnowledgeBindingRepository;
        this.agentToolBindingRepository = agentToolBindingRepository;
        this.agentChannelBindingRepository = agentChannelBindingRepository;
        this.scheduleTriggerRepository = scheduleTriggerRepository;
        this.apiCredentialRepository = apiCredentialRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.modelProviderService = modelProviderService;
        this.agentEvaluationService = agentEvaluationService;
    }

    @Transactional(readOnly = true)
    public ReadinessResult check(String orgId, String agentId, Integer versionNo) {
        Optional<AgentDefinitionEntity> definitionOpt = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        ArrayList<ReadinessCheck> checks = new ArrayList<>();
        if (definitionOpt.isEmpty()) {
            checks.add(blocker("agent_exists", "Agent 不存在，无法发布。"));
            return result(agentId, null, checks, Map.of());
        }
        AgentDefinitionEntity definition = definitionOpt.get();
        checks.add(definition.isEnabled()
                ? pass("agent_enabled", "Agent 已启用。")
                : blocker("agent_enabled", "Agent 已停用，不能发布到生产。"));

        AgentWorkflowVersionEntity targetVersion = resolveVersion(orgId, agentId, versionNo).orElse(null);
        if (targetVersion == null) {
            checks.add(blocker("compiled_version", "目标编译版本不存在。"));
        } else {
            boolean hasArtifact = hasText(targetVersion.getWorkflowCode())
                    && hasText(targetVersion.getWorkflowManifest())
                    && hasText(targetVersion.getWorkflowPreview());
            checks.add(hasArtifact
                    ? pass("compiled_artifacts", "目标版本包含 workflow code、manifest 和 preview graph。")
                    : blocker("compiled_artifacts", "目标版本缺少 workflow code、manifest 或 preview graph。"));
        }

        Map<String, String> modelRoute = resolveModelRoute(orgId, definition, checks);
        List<AgentKnowledgeBindingEntity> knowledgeBindings =
                agentKnowledgeBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, agentId);
        checkKnowledgeBindings(orgId, knowledgeBindings, checks);

        List<AgentToolBindingEntity> toolBindings =
                agentToolBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(orgId, agentId);
        checks.add(toolBindings.isEmpty()
                ? warn("tool_scope", "未绑定直接工具；如果该 Agent 只做纯问答或 Skill 驱动，可继续。")
                : pass("tool_scope", "已绑定 " + toolBindings.size() + " 个直接工具。"));

        List<AgentChannelBindingEntity> channels =
                agentChannelBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByIdAsc(orgId, agentId);
        List<AgentRuntimeScheduleTriggerEntity> schedules =
                scheduleTriggerRepository.findByOrgIdAndAgentIdAndActiveTrueOrderByIdAsc(orgId, agentId);
        long activeApiKeys = activeApiKeyCount(orgId, agentId);
        checkRuntimeEntries(channels, schedules, activeApiKeys, checks);
        AgentEvaluationService.EvaluationGateSummary evaluationGate = agentEvaluationService.latestGateSummary(
                orgId,
                agentId,
                targetVersion == null ? versionNo : targetVersion.getVersionNo());
        checkEvaluationGate(evaluationGate, checks);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("versionNo", targetVersion == null ? null : targetVersion.getVersionNo());
        summary.put("modelRoute", modelRoute);
        summary.put("knowledgeBaseCount", knowledgeBindings.size());
        summary.put("toolCount", toolBindings.size());
        summary.put("channelCount", channels.size());
        summary.put("scheduleCount", schedules.size());
        summary.put("activeApiKeyCount", activeApiKeys);
        summary.put("evaluationGate", evaluationGate);
        return result(agentId, targetVersion == null ? null : targetVersion.getVersionNo(), checks, summary);
    }

    @Transactional(readOnly = true)
    public ReadinessResult requirePublishReady(String orgId, String agentId, Integer versionNo) {
        ReadinessResult readiness = check(orgId, agentId, versionNo);
        if (readiness.blocked()) {
            String reason = readiness.checks().stream()
                    .filter(item -> "blocker".equals(item.severity()) && !"passed".equals(item.status()))
                    .findFirst()
                    .map(ReadinessCheck::message)
                    .orElse("Agent 未通过生产发布检查。");
            throw new ConflictException("Agent 生产发布检查未通过：" + reason);
        }
        return readiness;
    }

    private Optional<AgentWorkflowVersionEntity> resolveVersion(String orgId, String agentId, Integer versionNo) {
        if (versionNo == null) {
            return Optional.empty();
        }
        return agentWorkflowVersionRepository.findByOrgIdAndAgentIdAndVersionNo(orgId, agentId, versionNo);
    }

    private Map<String, String> resolveModelRoute(String orgId,
                                                  AgentDefinitionEntity definition,
                                                  ArrayList<ReadinessCheck> checks) {
        try {
            Map<String, String> route = modelProviderService.resolveRuntimeModelRoute(
                    orgId,
                    "chat",
                    definition.getModel());
            checks.add(pass("model_route", "聊天场景模型路由可用：" + route.getOrDefault("modelName", "")));
            return route;
        } catch (RuntimeException ex) {
            checks.add(blocker("model_route", "聊天场景模型路由不可用：" + ex.getMessage()));
            return Map.of();
        }
    }

    private void checkKnowledgeBindings(String orgId,
                                        List<AgentKnowledgeBindingEntity> bindings,
                                        ArrayList<ReadinessCheck> checks) {
        if (bindings.isEmpty()) {
            checks.add(warn("knowledge_scope", "未绑定知识库；如果该 Agent 不需要 RAG，可继续。"));
            return;
        }
        List<Long> ids = bindings.stream().map(AgentKnowledgeBindingEntity::getKnowledgeBaseId).toList();
        Map<Long, KnowledgeBaseEntity> kbById = new LinkedHashMap<>();
        knowledgeBaseRepository.findByOrgIdAndIdIn(orgId, ids).forEach(item -> kbById.put(item.getId(), item));
        ArrayList<Long> invalid = new ArrayList<>();
        for (Long id : ids) {
            KnowledgeBaseEntity kb = kbById.get(id);
            if (kb == null || !"ACTIVE".equals(kb.getStatus())) {
                invalid.add(id);
            }
        }
        checks.add(invalid.isEmpty()
                ? pass("knowledge_scope", "已绑定 " + ids.size() + " 个可用知识库。")
                : blocker("knowledge_scope", "存在不可用或已删除知识库绑定：" + invalid));
    }

    private void checkRuntimeEntries(List<AgentChannelBindingEntity> channels,
                                     List<AgentRuntimeScheduleTriggerEntity> schedules,
                                     long activeApiKeys,
                                     ArrayList<ReadinessCheck> checks) {
        Set<String> channelIds = new LinkedHashSet<>();
        channels.forEach(item -> channelIds.add(item.getChannelId()));
        boolean hasInteractiveChannel = channelIds.stream().anyMatch(channel ->
                !"api".equals(channel) && !"openapi".equals(channel));
        boolean hasEnabledSchedule = schedules.stream().anyMatch(AgentRuntimeScheduleTriggerEntity::isEnabled);
        boolean apiChannelEnabled = channelIds.contains("api") || channelIds.contains("openapi");
        boolean hasApiEntry = apiChannelEnabled && activeApiKeys > 0;
        if (hasInteractiveChannel || hasEnabledSchedule || hasApiEntry) {
            checks.add(pass("runtime_entry", "生产运行入口可用。"));
            return;
        }
        if (apiChannelEnabled) {
            checks.add(blocker("runtime_entry", "已启用 Open API 渠道，但还没有 active API Key。"));
            return;
        }
        checks.add(blocker("runtime_entry", "缺少可用生产入口：请至少启用一个渠道、计划触发器或 Open API Key。"));
    }

    private void checkEvaluationGate(AgentEvaluationService.EvaluationGateSummary evaluationGate,
                                     ArrayList<ReadinessCheck> checks) {
        if ("not_checked".equals(evaluationGate.status())) {
            checks.add(warn("evaluation_gate", "未指定目标版本，暂未检查评测门禁。"));
            return;
        }
        if ("not_configured".equals(evaluationGate.status())) {
            checks.add(warn("evaluation_gate", "尚未配置 Agent 评测集；生产验收前应至少配置 P0 或 safety 用例。"));
            return;
        }
        if (evaluationGate.blocked()) {
            checks.add(blocker("evaluation_gate", "评测门禁未通过：缺少当前版本评测运行或存在 P0/safety/阈值失败。"));
            return;
        }
        if ("warning".equals(evaluationGate.status())) {
            checks.add(warn("evaluation_gate", "评测门禁存在警告：部分评测集为空、warn-only 失败或缺少运行。"));
            return;
        }
        checks.add(pass("evaluation_gate", "当前版本评测门禁已通过。"));
    }

    private long activeApiKeyCount(String orgId, String agentId) {
        Instant now = Instant.now();
        return apiCredentialRepository.findByOrgIdAndAgentIdOrderByCreatedAtDesc(orgId, agentId).stream()
                .filter(item -> AgentApiCredentialEntity.STATUS_ACTIVE.equals(item.getStatus()))
                .filter(item -> item.getExpiresAt() == null || item.getExpiresAt().isAfter(now))
                .count();
    }

    private ReadinessResult result(String agentId,
                                   Integer versionNo,
                                   List<ReadinessCheck> checks,
                                   Map<String, Object> summary) {
        long blockerCount = checks.stream()
                .filter(item -> "blocker".equals(item.severity()) && !"passed".equals(item.status()))
                .count();
        long warningCount = checks.stream()
                .filter(item -> "warning".equals(item.severity()) && !"passed".equals(item.status()))
                .count();
        String status = blockerCount > 0 ? "blocked" : warningCount > 0 ? "warning" : "ready";
        return new ReadinessResult(agentId, versionNo, status, blockerCount > 0, checks, summary);
    }

    private ReadinessCheck pass(String code, String message) {
        return new ReadinessCheck(code, "passed", "info", message);
    }

    private ReadinessCheck warn(String code, String message) {
        return new ReadinessCheck(code, "warning", "warning", message);
    }

    private ReadinessCheck blocker(String code, String message) {
        return new ReadinessCheck(code, "failed", "blocker", message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ReadinessResult(
            String agentId,
            Integer versionNo,
            String status,
            boolean blocked,
            List<ReadinessCheck> checks,
            Map<String, Object> summary
    ) {
    }

    public record ReadinessCheck(
            String code,
            String status,
            String severity,
            String message
    ) {
    }
}
