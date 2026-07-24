package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Read-only aggregation of runtime triggers (channels + schedule placeholders) for Agent Builder (FEAT-004).
 */
@Service
public class AgentRuntimeCatalogService {

    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowVersionRepository workflowVersionRepository;
    private final AgentRuntimeScheduleSyncService runtimeScheduleSyncService;

    public AgentRuntimeCatalogService(AgentDefinitionService agentDefinitionService,
                                      AgentWorkflowVersionRepository workflowVersionRepository,
                                      AgentRuntimeScheduleSyncService runtimeScheduleSyncService) {
        this.agentDefinitionService = agentDefinitionService;
        this.workflowVersionRepository = workflowVersionRepository;
        this.runtimeScheduleSyncService = runtimeScheduleSyncService;
    }

    public Map<String, Object> buildTriggers(String companyId, String agentId) {
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.get(companyId, agentId);
        Optional<AgentWorkflowVersionEntity> latestVersion =
                workflowVersionRepository.findTopByCompanyIdAndAgentIdOrderByVersionNoDesc(companyId, agentId);
        boolean hasCompiledVersions = latestVersion.isPresent();
        Long publishedVersionId = detail.definition().getPublishedVersionId();
        boolean published = publishedVersionId != null && publishedVersionId > 0;
        String lifecycle;
        if (!hasCompiledVersions) {
            lifecycle = "NO_COMPILE";
        } else if (published) {
            lifecycle = "PUBLISHED";
        } else {
            lifecycle = "COMPILED_DRAFT";
        }

        List<Map<String, Object>> channelTriggers = new ArrayList<>();
        for (String channelId : detail.channels()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "CHANNEL");
            row.put("channelId", channelId);
            row.put("label", humanizeChannel(channelId));
            row.put("detail", "会话、IM 或门户消息进入时触发本 Agent。");
            channelTriggers.add(row);
        }

        List<Map<String, Object>> scheduleTriggers = new ArrayList<>(
                runtimeScheduleSyncService.listActiveRows(companyId, agentId));
        String scheduleSource = scheduleTriggers.isEmpty() ? "none" : "persisted";
        if (scheduleTriggers.isEmpty()) {
            Optional<AgentWorkflowVersionEntity> publishedVersion = (published && publishedVersionId != null)
                    ? workflowVersionRepository.findById(publishedVersionId)
                    : Optional.empty();
            Optional<AgentWorkflowVersionEntity> inferredSourceVersion = latestVersion;
            if (publishedVersion.isPresent() && latestVersion.isPresent()) {
                Integer latestNo = latestVersion.get().getVersionNo();
                Integer publishedNo = publishedVersion.get().getVersionNo();
                if (latestNo != null && publishedNo != null && latestNo <= publishedNo) {
                    inferredSourceVersion = publishedVersion;
                }
            } else if (publishedVersion.isPresent()) {
                inferredSourceVersion = publishedVersion;
            }
            Optional<String> manifestSource = inferredSourceVersion.map(AgentWorkflowVersionEntity::getWorkflowManifest);
            List<Map<String, Object>> inferred = manifestSource
                    .map(runtimeScheduleSyncService::inferFromManifestJson)
                    .orElse(List.of());
            if (!inferred.isEmpty()) {
                scheduleTriggers.addAll(inferred);
                scheduleSource = "inferred";
            }
        }

        if (published) {
            if (scheduleTriggers.isEmpty()) {
                Map<String, Object> stub = new LinkedHashMap<>();
                stub.put("kind", "SCHEDULE");
                stub.put("id", "routine-daily-0900");
                stub.put("title", "例行巡检 · 默认 routine");
                stub.put("cadence", "每日 09:00");
                stub.put("stub", true);
                stub.put("detail", "发布后由平台从 routine 同步；当前为占位说明。");
                scheduleTriggers.add(stub);
                scheduleSource = "placeholder";
            } else {
                Map<String, Object> note = new LinkedHashMap<>();
                note.put("kind", "SCHEDULE");
                note.put("id", "platform-routine-note");
                note.put("title", "平台 routine 与 cron");
                note.put("cadence", "发布后同步");
                note.put("stub", true);
                note.put("detail", "以上条目来自 Spec 自然语言描述；正式可执行调度以发布后 routine / cron 同步为准。");
                scheduleTriggers.add(note);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", agentId);
        payload.put("lifecycle", lifecycle);
        payload.put("channelTriggers", channelTriggers);
        payload.put("scheduleTriggers", scheduleTriggers);
        payload.put("scheduleSource", scheduleSource);
        payload.put("scheduleSyncHint", "inferred".equals(scheduleSource)
                ? "当前为 Spec 推导触发器，点击「同步到调度」可固化为运行时配置。"
                : "persisted".equals(scheduleSource)
                ? "已同步为运行时调度配置。"
                : "");
        return payload;
    }

    public Long publishedVersionId(String companyId, String agentId) {
        return agentDefinitionService.get(companyId, agentId).definition().getPublishedVersionId();
    }

    private static String humanizeChannel(String channelId) {
        if (channelId == null) {
            return "";
        }
        return switch (channelId.toLowerCase(Locale.ROOT)) {
            case "wechat" -> "企微";
            case "dingtalk" -> "钉钉";
            case "feishu" -> "飞书";
            case "web" -> "Web 浮窗";
            default -> channelId;
        };
    }
}
