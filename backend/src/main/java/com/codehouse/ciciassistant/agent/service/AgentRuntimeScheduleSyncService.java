package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentRuntimeScheduleTriggerEntity;
import com.codehouse.ciciassistant.agent.domain.AgentRuntimeScheduleTriggerRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentRuntimeScheduleSyncService {

    private final AgentRuntimeScheduleTriggerRepository scheduleRepository;
    private final AgentWorkflowVersionRepository workflowVersionRepository;
    private final ObjectMapper objectMapper;
    private final AgentWorkflowExecutionLogService executionLogService;

    public AgentRuntimeScheduleSyncService(AgentRuntimeScheduleTriggerRepository scheduleRepository,
                                           AgentWorkflowVersionRepository workflowVersionRepository,
                                           ObjectMapper objectMapper,
                                           AgentWorkflowExecutionLogService executionLogService) {
        this.scheduleRepository = scheduleRepository;
        this.workflowVersionRepository = workflowVersionRepository;
        this.objectMapper = objectMapper;
        this.executionLogService = executionLogService;
    }

    public List<Map<String, Object>> listActiveRows(String companyId, String agentId) {
        List<AgentRuntimeScheduleTriggerEntity> rows =
                scheduleRepository.findByCompanyIdAndAgentIdAndActiveTrueOrderByIdAsc(companyId, agentId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentRuntimeScheduleTriggerEntity row : rows) {
            out.add(toPayload(row));
        }
        return out;
    }

    public List<Map<String, Object>> inferFromManifestJson(String manifestJson) {
        return inferFromManifestJson(manifestJson, 8);
    }

    public List<Map<String, Object>> inferFromManifestJson(String manifestJson, int maxRows) {
        if (manifestJson == null || manifestJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(manifestJson);
            JsonNode steps = root.path("generatedFrom").path("specIr").path("steps");
            if (!steps.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                if (out.size() >= Math.max(1, maxRows)) {
                    break;
                }
                String step = steps.get(i).asText("").trim();
                if (!looksLikeScheduleLine(step)) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "SCHEDULE");
                row.put("id", "spec-inferred-" + i);
                row.put("title", titleHint(step));
                row.put("cadence", "Spec 描述");
                row.put("stub", true);
                row.put("detail", step);
                out.add(row);
            }
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Separate transaction so failures during publish do not mark the caller's outer transaction rollback-only
     * when {@link AgentDefinitionService#publishVersion} catches exceptions best-effort.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> syncFromCompiledVersion(String companyId, String agentId, Long publishedVersionId) {
        // Replace inferred-from-spec rows wholesale; deactivating would accumulate inactive duplicates and violate
        // uq_agent_runtime_sched_org_agent_key_active (company_id, agent_id, trigger_key, active).
        scheduleRepository.deleteByCompanyIdAndAgentIdAndSource(companyId, agentId, "SPEC_SYNC");
        scheduleRepository.flush();

        Optional<AgentWorkflowVersionEntity> source = resolveSourceVersion(companyId, agentId, publishedVersionId);

        if (source.isEmpty()) {
            return Map.of(
                    "agentId", agentId,
                    "synced", 0,
                    "sourceVersionNo", 0,
                    "sourceVersionId", 0L,
                    "rows", List.of(),
                    "message", "当前无可用编译版本，未产生可同步调度。"
            );
        }

        AgentWorkflowVersionEntity version = source.get();
        List<Map<String, Object>> inferred = inferFromManifestJson(version.getWorkflowManifest(), 12);
        List<AgentRuntimeScheduleTriggerEntity> toSave = new ArrayList<>();
        for (int i = 0; i < inferred.size(); i++) {
            Map<String, Object> item = inferred.get(i);
            toSave.add(new AgentRuntimeScheduleTriggerEntity(
                    companyId,
                    agentId,
                    version.getId(),
                    version.getVersionNo(),
                    String.valueOf(item.getOrDefault("id", "spec-sync-" + i)),
                    String.valueOf(item.getOrDefault("title", "未命名调度")),
                    String.valueOf(item.getOrDefault("cadence", "Spec 描述")),
                    String.valueOf(item.getOrDefault("detail", "")),
                    "SPEC_SYNC",
                    true
            ));
        }
        if (!toSave.isEmpty()) {
            scheduleRepository.saveAll(toSave);
        }

        return Map.of(
                "agentId", agentId,
                "synced", toSave.size(),
                "sourceVersionNo", version.getVersionNo() == null ? 0 : version.getVersionNo(),
                "sourceVersionId", version.getId() == null ? 0L : version.getId(),
                "rows", toSave.stream().map(this::toPayload).toList(),
                "message", toSave.isEmpty()
                        ? "已清空旧调度；当前 Spec 未识别到时间语义，未同步新调度。"
                        : "已同步 Spec 推导调度到运行时列表。"
        );
    }

    private Optional<AgentWorkflowVersionEntity> resolveSourceVersion(String companyId, String agentId, Long publishedVersionId) {
        if (publishedVersionId != null && publishedVersionId > 0) {
            Optional<AgentWorkflowVersionEntity> published = workflowVersionRepository.findById(publishedVersionId);
            if (published.isPresent()) {
                return published;
            }
        }
        return workflowVersionRepository.findTopByCompanyIdAndAgentIdOrderByVersionNoDesc(companyId, agentId);
    }

    private Map<String, Object> toPayload(AgentRuntimeScheduleTriggerEntity row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "SCHEDULE");
        out.put("id", row.getTriggerKey());
        out.put("title", row.getTitle());
        out.put("cadence", row.getCadence());
        out.put("detail", row.getDetail());
        out.put("stub", row.isStub());
        out.put("source", row.getSource());
        out.put("versionNo", row.getVersionNo());
        out.put("enabled", row.isEnabled());
        return out;
    }

    @Transactional
    public Map<String, Object> updateEnabled(String companyId, String agentId, String triggerKey, boolean enabled) {
        AgentRuntimeScheduleTriggerEntity row = scheduleRepository
                .findByCompanyIdAndAgentIdAndTriggerKeyAndActiveTrue(companyId, agentId, triggerKey)
                .orElseThrow(() -> new IllegalArgumentException("Schedule trigger not found: " + triggerKey));
        row.updateEnabled(enabled);
        scheduleRepository.save(row);
        return toPayload(row);
    }

    @Transactional
    public Map<String, Object> runNow(String companyId, String agentId, String triggerKey) {
        AgentRuntimeScheduleTriggerEntity row = scheduleRepository
                .findByCompanyIdAndAgentIdAndTriggerKeyAndActiveTrue(companyId, agentId, triggerKey)
                .orElseThrow(() -> new IllegalArgumentException("Schedule trigger not found: " + triggerKey));
        String summary = "RUN_NOW schedule trigger: " + row.getTitle();
        try {
            executionLogService.append(
                    companyId,
                    agentId,
                    row.getWorkflowVersionId(),
                    row.getVersionNo(),
                    AgentWorkflowExecutionLogService.SOURCE_SCHEDULE_STUB,
                    AgentWorkflowExecutionLogService.STATUS_SUCCESS,
                    0,
                    summary,
                    null);
        } catch (RuntimeException ignored) {
            // run-now action should remain best-effort if execution log fails
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("triggerKey", triggerKey);
        payload.put("status", "QUEUED");
        payload.put("message", "已触发立即执行（本期为占位执行记录）。");
        return payload;
    }

    private static boolean looksLikeScheduleLine(String step) {
        if (step == null || step.isBlank() || step.length() < 4) {
            return false;
        }
        String lower = step.toLowerCase(Locale.ROOT);
        if (step.contains("每天") || step.contains("每周") || step.contains("每月") || step.contains("每年")) {
            return true;
        }
        if (step.contains("定时") || step.contains("周期") || step.contains("工作日") || step.contains("周末")) {
            return true;
        }
        if (step.contains("上午") || step.contains("下午") || step.contains("晚上") || step.contains("凌晨") || step.contains("整点")) {
            return true;
        }
        if (lower.contains("cron")) {
            return true;
        }
        return step.matches(".*\\d{1,2}\\s*[点:：]\\s*\\d{0,2}.*") || step.matches(".*\\d{1,2}点.*");
    }

    private static String titleHint(String step) {
        String compact = step.replaceAll("\\s+", " ").trim();
        return compact.length() > 56 ? compact.substring(0, 53) + "…" : compact;
    }
}
