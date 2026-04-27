package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowExecutionLogEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowExecutionLogRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentWorkflowExecutionLogService {

    public static final String SOURCE_TRY_RUN = "TRY_RUN";
    public static final String SOURCE_MANUAL_PUBLISH = "MANUAL_PUBLISH";
    public static final String SOURCE_CHANNEL = "CHANNEL";
    public static final String SOURCE_SCHEDULE_STUB = "SCHEDULE_STUB";

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final AgentWorkflowExecutionLogRepository repository;
    private final AgentWorkflowVersionRepository workflowVersionRepository;

    public AgentWorkflowExecutionLogService(AgentWorkflowExecutionLogRepository repository,
                                            AgentWorkflowVersionRepository workflowVersionRepository) {
        this.repository = repository;
        this.workflowVersionRepository = workflowVersionRepository;
    }

    @Transactional
    public void append(
            String orgId,
            String agentId,
            Long workflowVersionId,
            Integer versionNo,
            String source,
            String status,
            int durationMs,
            String summary,
            String errorHint) {
        String safeSummary = truncate(summary == null ? "" : summary, 1024);
        String safeHint = errorHint == null || errorHint.isBlank() ? null : truncate(errorHint, 512);
        repository.save(new AgentWorkflowExecutionLogEntity(
                orgId,
                agentId,
                workflowVersionId,
                versionNo,
                source,
                status,
                Math.max(0, durationMs),
                safeSummary,
                safeHint,
                Instant.now()));
    }

    @Transactional
    public void appendFromChat(
            String orgId,
            String agentId,
            Long workflowVersionId,
            String executionStatus,
            int durationMs,
            String output) {
        Integer versionNo = null;
        if (workflowVersionId != null) {
            versionNo = workflowVersionRepository.findById(workflowVersionId)
                    .filter(v -> orgId.equals(v.getOrgId()) && agentId.equals(v.getAgentId()))
                    .map(v -> v.getVersionNo())
                    .orElse(null);
        }
        String summary = "CHANNEL chat · status=" + executionStatus + " · " + truncate(output == null ? "" : output, 400);
        append(
                orgId,
                agentId,
                workflowVersionId,
                versionNo,
                SOURCE_CHANNEL,
                normalizeWorkflowStatus(executionStatus),
                durationMs,
                summary,
                null);
    }

    public List<Map<String, Object>> list(String orgId, String agentId, Integer versionNo, int limit) {
        int cap = Math.min(Math.max(limit, 1), 100);
        var page = PageRequest.of(0, cap);
        List<AgentWorkflowExecutionLogEntity> rows = versionNo == null
                ? repository.findByOrgIdAndAgentIdOrderByCreatedAtDesc(orgId, agentId, page)
                : repository.findByOrgIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(orgId, agentId, versionNo, page);
        return rows.stream().map(this::toRow).toList();
    }

    public static String normalizeWorkflowStatus(String executionStatus) {
        if (executionStatus == null || executionStatus.isBlank()) {
            return STATUS_FAILED;
        }
        String s = executionStatus.toLowerCase();
        if (s.contains("invalid") || s.contains("error") || s.contains("failed")) {
            return STATUS_FAILED;
        }
        return STATUS_SUCCESS;
    }

    private Map<String, Object> toRow(AgentWorkflowExecutionLogEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("agentId", e.getAgentId());
        m.put("workflowVersionId", e.getWorkflowVersionId());
        m.put("versionNo", e.getVersionNo());
        m.put("source", e.getSource());
        m.put("status", e.getStatus());
        m.put("durationMs", e.getDurationMs());
        m.put("summary", e.getSummary());
        m.put("errorHint", e.getErrorHint());
        m.put("createdAt", e.getCreatedAt().toString());
        return m;
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }
}
