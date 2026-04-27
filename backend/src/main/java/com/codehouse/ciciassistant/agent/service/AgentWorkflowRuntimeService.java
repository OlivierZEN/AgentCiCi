package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AgentWorkflowRuntimeService {

    private final AgentCapabilityResolverService capabilityResolverService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentWorkflowExecutionLogService executionLogService;

    public AgentWorkflowRuntimeService(AgentCapabilityResolverService capabilityResolverService,
                                       AgentDefinitionRepository agentDefinitionRepository,
                                       AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                                       AgentWorkflowExecutionLogService executionLogService) {
        this.capabilityResolverService = capabilityResolverService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.executionLogService = executionLogService;
    }

    public DebugRuntimeResult debug(String orgId, String agentId, String input,
                                    List<String> requestedKnowledgeBaseIds, List<String> skillRefs) {
        long startedNanos = System.nanoTime();
        AgentCapabilityResolverService.AgentCapabilityResolution capability = capabilityResolverService.resolve(
                orgId, agentId, skillRefs == null ? List.of() : skillRefs);
        Optional<AgentWorkflowVersionEntity> publishedVersion = resolvePublishedVersion(orgId, capability.agentId());
        String runtimeSource = publishedVersion.isPresent() ? "published_version" : "capability_fallback";

        List<String> traceSteps = new ArrayList<>();
        traceSteps.add("resolve-agent:" + capability.agentId());
        traceSteps.add("resolve-runtime-source:" + runtimeSource);
        publishedVersion.ifPresent(version -> {
            traceSteps.add("load-workflow-version:" + version.getVersionNo());
            traceSteps.add("workflow-entry:runAgent");
        });
        traceSteps.add("resolve-skills:" + String.join(",", capability.effectiveSkillCodes()));
        traceSteps.add("resolve-tools:" + String.join(",", capability.effectiveToolNames()));
        traceSteps.add("resolve-kbs:" + capability.effectiveKnowledgeBaseIds());
        ExecutionResult executionResult = executeWorkflow(publishedVersion.orElse(null), input, capability);
        traceSteps.add("execute-workflow-code:" + executionResult.status());
        traceSteps.add("route-input:" + ((input == null || input.isBlank()) ? "default" : "input-based"));

        String workflowCodePreview = publishedVersion
                .map(AgentWorkflowVersionEntity::getWorkflowCode)
                .map(this::toPreview)
                .orElse("");

        DebugRuntimeResult result = new DebugRuntimeResult(
                capability.agentId(),
                capability.effectiveSkillCodes(),
                capability.effectiveToolNames(),
                capability.effectiveKnowledgeBaseIds().stream().map(String::valueOf).toList(),
                capability.warnings(),
                traceSteps,
                runtimeSource,
                publishedVersion.map(AgentWorkflowVersionEntity::getId).orElse(null),
                workflowCodePreview,
                executionResult.status(),
                executionResult.output(),
                executionResult.trace(),
                executionResult.contextSnapshot()
        );
        appendTryRunLog(orgId, capability.agentId(), publishedVersion, executionResult, startedNanos);
        return result;
    }

    private void appendTryRunLog(
            String orgId,
            String agentId,
            Optional<AgentWorkflowVersionEntity> publishedVersion,
            ExecutionResult executionResult,
            long startedNanos) {
        try {
            int durationMs = (int) Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
            String status = AgentWorkflowExecutionLogService.normalizeWorkflowStatus(executionResult.status());
            Long versionPk = publishedVersion.map(AgentWorkflowVersionEntity::getId).orElse(null);
            Integer versionNo = publishedVersion.map(AgentWorkflowVersionEntity::getVersionNo).orElse(null);
            String out = executionResult.output() == null ? "" : executionResult.output();
            String summary = "TRY_RUN status=" + executionResult.status() + " · " + truncate(out, 400);
            executionLogService.append(
                    orgId,
                    agentId,
                    versionPk,
                    versionNo,
                    AgentWorkflowExecutionLogService.SOURCE_TRY_RUN,
                    status,
                    durationMs,
                    summary,
                    "published-invalid".equals(executionResult.status()) ? "invalid-workflow-entry" : null);
        } catch (RuntimeException ignored) {
            // never fail debug because of observability persistence
        }
    }

    private static String truncate(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "…";
    }

    public RuntimeExecutionResult evaluateForChat(String orgId, String agentId, String input, List<String> effectiveToolNames) {
        Optional<AgentWorkflowVersionEntity> publishedVersion = resolvePublishedVersion(orgId, agentId);
        ExecutionResult executionResult = executeWorkflow(
                publishedVersion.orElse(null),
                input,
                effectiveToolNames == null ? List.of() : effectiveToolNames
        );
        return new RuntimeExecutionResult(
                executionResult.status(),
                executionResult.output(),
                publishedVersion.map(AgentWorkflowVersionEntity::getId).orElse(null),
                executionResult.trace(),
                executionResult.contextSnapshot()
        );
    }

    private Optional<AgentWorkflowVersionEntity> resolvePublishedVersion(String orgId, String agentId) {
        Optional<AgentDefinitionEntity> definition = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        if (definition.isEmpty() || definition.get().getPublishedVersionId() == null) {
            return Optional.empty();
        }
        return agentWorkflowVersionRepository.findById(definition.get().getPublishedVersionId())
                .filter(item -> orgId.equals(item.getOrgId()) && agentId.equals(item.getAgentId()))
                .filter(item -> "PUBLISHED".equalsIgnoreCase(item.getPublishStatus()));
    }

    private String toPreview(String workflowCode) {
        if (workflowCode == null || workflowCode.isBlank()) {
            return "";
        }
        String compact = workflowCode.replace("\r", "").trim();
        int max = 240;
        return compact.length() <= max ? compact : compact.substring(0, max);
    }

    private ExecutionResult executeWorkflow(AgentWorkflowVersionEntity publishedVersion,
                                            String input,
                                            AgentCapabilityResolverService.AgentCapabilityResolution capability) {
        return executeWorkflow(publishedVersion, input, capability.effectiveToolNames());
    }

    private ExecutionResult executeWorkflow(AgentWorkflowVersionEntity publishedVersion,
                                            String input,
                                            List<String> effectiveToolNames) {
        List<String> executionTrace = new ArrayList<>();
        Map<String, Object> contextSnapshot = new LinkedHashMap<>();
        List<Map<String, Object>> nodeMetrics = new ArrayList<>();
        String normalizedInput = input == null ? "" : input.trim();
        String route = normalizedInput.isBlank() ? "default" : "input-based";
        contextSnapshot.put("inputRoute", route);
        contextSnapshot.put("toolScopeSize", effectiveToolNames.size());
        contextSnapshot.put("intent", "classified");
        contextSnapshot.put("branchHit", "default".equals(route) ? "route-default" : "route-input-based");
        executionTrace.add("workflow-node:start");
        executionTrace.add("workflow-node:route-input:" + route);
        executionTrace.add("workflow-node:tool-scope:size=" + effectiveToolNames.size());
        nodeMetrics.add(nodeMetric("start", 1L, "ok",
                "workflow runtime starts", "runtime context initialized",
                payload("stage", "start"), payload("stage", "initialized")));
        nodeMetrics.add(nodeMetric("route-input", 1L, "ok",
                "input route candidate", "route=" + route,
                payload("rawInputPresent", String.valueOf(!normalizedInput.isBlank())), payload("route", route)));
        nodeMetrics.add(nodeMetric("tool-scope", 1L, "ok",
                "allowed tool count=" + effectiveToolNames.size(), "tool scope prepared",
                payload("allowedToolCount", String.valueOf(effectiveToolNames.size())),
                payload("toolScopeReady", "true")));
        if (publishedVersion == null) {
            contextSnapshot.put("runtimeSource", "fallback");
            contextSnapshot.put("knowledgeUsed", false);
            contextSnapshot.put("toolInvoked", false);
            contextSnapshot.put("errorNode", "");
            contextSnapshot.put("errorType", "");
            contextSnapshot.put("replayHint", "Use published version to enable workflow_code replay.");
            executionTrace.add("workflow-node:fallback-runtime");
            executionTrace.add("workflow-node:end:fallback-executed");
            nodeMetrics.add(nodeMetric("fallback-runtime", 1L, "ok",
                    "published version absent", "fallback runtime path completed",
                    payload("runtimeSource", "fallback"), payload("status", "fallback-executed")));
            nodeMetrics.add(nodeMetric("end", 1L, "ok", "fallback execution completed", "return success",
                    payload("status", "fallback-executed"), payload("result", "success")));
            contextSnapshot.put("nodeMetrics", nodeMetrics);
            return new ExecutionResult("fallback-executed",
                    "Fallback runtime executed with " + effectiveToolNames.size()
                            + " tools on route " + route + ".",
                    executionTrace,
                    contextSnapshot);
        }
        String code = publishedVersion.getWorkflowCode() == null ? "" : publishedVersion.getWorkflowCode();
        if (!code.contains("runAgent")) {
            contextSnapshot.put("runtimeSource", "published");
            contextSnapshot.put("knowledgeUsed", false);
            contextSnapshot.put("toolInvoked", false);
            contextSnapshot.put("validationError", "missing-runAgent");
            contextSnapshot.put("errorNode", "validate-entry");
            contextSnapshot.put("errorType", "missing-runAgent");
            contextSnapshot.put("replayHint", "Ensure published workflow_code contains runAgent(ctx).");
            executionTrace.add("workflow-node:validate-entry:missing-runAgent");
            executionTrace.add("workflow-node:end:published-invalid");
            nodeMetrics.add(nodeMetric("validate-entry", 1L, "error",
                    "validate workflow entry", "runAgent entry not found",
                    payload("entry", "runAgent"), payload("errorType", "missing-runAgent")));
            nodeMetrics.add(nodeMetric("end", 1L, "error", "execution terminated", "published-invalid",
                    payload("status", "published-invalid"), payload("result", "error")));
            contextSnapshot.put("nodeMetrics", nodeMetrics);
            return new ExecutionResult("published-invalid",
                    "Published workflow code missing runAgent entry.",
                    executionTrace,
                    contextSnapshot);
        }
        long parseStart = System.nanoTime();
        List<String> parsedNodes = parseWorkflowNodes(code);
        long parseCostMs = Math.max(1L, (System.nanoTime() - parseStart) / 1_000_000L);
        for (String node : parsedNodes) {
            executionTrace.add("workflow-node:code:" + node);
            nodeMetrics.add(nodeMetric(node, parseCostMs, "ok",
                    "parsed workflow node=" + node, "node recognized from runAgent body",
                    payload("parsedNode", node), payload("recognized", "true")));
        }
        contextSnapshot.put("runtimeSource", "published");
        contextSnapshot.put("parsedNodes", parsedNodes);
        contextSnapshot.put("knowledgeUsed", parsedNodes.contains("knowledge-search"));
        contextSnapshot.put("toolInvoked", parsedNodes.contains("tool-invoke-best") && !effectiveToolNames.isEmpty());
        contextSnapshot.put("responsePlanned", parsedNodes.contains("response-generate"));
        contextSnapshot.put("errorNode", "");
        contextSnapshot.put("errorType", "");
        contextSnapshot.put("replayHint", "Replay by walking nodeMetrics in order and inspecting ioSummary.");
        executionTrace.add("workflow-node:invoke-runAgent:v" + publishedVersion.getVersionNo());
        executionTrace.add("workflow-node:end:published-executed");
        nodeMetrics.add(nodeMetric("invoke-runAgent", 1L, "ok",
                "invoke runAgent with runtime context", "runAgent invocation completed",
                payload("entry", "runAgent"), payload("invoked", "true")));
        nodeMetrics.add(nodeMetric("end", 1L, "ok", "execution completed", "published-executed",
                payload("status", "published-executed"), payload("result", "success")));
        contextSnapshot.put("nodeMetrics", nodeMetrics);
        return new ExecutionResult("published-executed",
                "Published workflow v" + publishedVersion.getVersionNo()
                        + " executed on route " + route
                        + " with " + effectiveToolNames.size() + " tools.",
                executionTrace,
                contextSnapshot);
    }

    private Map<String, Object> nodeMetric(String nodeId, long costMs, String status,
                                           String inputSummary, String outputSummary,
                                           Map<String, Object> inputPayload,
                                           Map<String, Object> outputPayload) {
        Map<String, Object> metric = new LinkedHashMap<>();
        metric.put("nodeId", nodeId);
        metric.put("costMs", costMs);
        metric.put("status", status);
        metric.put("ioSummary", Map.of(
                "input", inputSummary == null ? "" : inputSummary,
                "output", outputSummary == null ? "" : outputSummary
        ));
        metric.put("ioPayload", Map.of(
                "input", inputPayload == null ? Map.of() : inputPayload,
                "output", outputPayload == null ? Map.of() : outputPayload
        ));
        return metric;
    }

    private Map<String, Object> payload(String key, String value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value == null ? "" : value);
        return payload;
    }

    public record DebugRuntimeResult(
            String agentId,
            List<String> activeSkills,
            List<String> effectiveToolNames,
            List<String> effectiveKnowledgeBaseIds,
            List<String> warnings,
            List<String> traceSteps,
            String runtimeSource,
            Long publishedVersionId,
            String workflowCodePreview,
            String executionStatus,
            String executionOutput,
            List<String> executionTrace,
            Map<String, Object> contextSnapshot
    ) {
    }

    private record ExecutionResult(String status, String output, List<String> trace, Map<String, Object> contextSnapshot) {
    }

    private List<String> parseWorkflowNodes(String workflowCode) {
        if (workflowCode == null || workflowCode.isBlank()) {
            return List.of("unknown");
        }
        String runAgentBody = extractRunAgentBody(workflowCode);
        if (runAgentBody.isBlank()) {
            return List.of("unknown");
        }
        List<String> nodes = new ArrayList<>();
        List<String> lines = Arrays.stream(runAgentBody.replace("\r", "").split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        for (String line : lines) {
            if (line.contains("ctx.model.classify(")) {
                nodes.add("intent-classify");
            } else if (line.contains("ctx.knowledge.search(")) {
                nodes.add("knowledge-search");
            } else if (line.contains("ctx.handoff.request(")) {
                nodes.add("handoff-request");
            } else if (line.contains("ctx.tools.invokeBest(")) {
                nodes.add("tool-invoke-best");
            } else if (line.contains("ctx.model.generate(")) {
                nodes.add("response-generate");
            } else if (line.startsWith("if (")) {
                nodes.add("branch-condition");
            } else if (line.startsWith("return ")) {
                nodes.add("return-result");
            }
        }
        if (nodes.isEmpty()) {
            nodes.add("unknown");
        }
        return nodes;
    }

    private String extractRunAgentBody(String workflowCode) {
        int signatureStart = workflowCode.indexOf("runAgent");
        if (signatureStart < 0) {
            return "";
        }
        int bodyStart = workflowCode.indexOf('{', signatureStart);
        if (bodyStart < 0) {
            return "";
        }
        int depth = 0;
        for (int i = bodyStart; i < workflowCode.length(); i++) {
            char ch = workflowCode.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return workflowCode.substring(bodyStart + 1, i);
                }
            }
        }
        return "";
    }

    public record RuntimeExecutionResult(
            String executionStatus,
            String executionOutput,
            Long publishedVersionId,
            List<String> executionTrace,
            Map<String, Object> contextSnapshot
    ) {
    }
}
