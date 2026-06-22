package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseResultEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseResultRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvaluationService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository versionRepository;
    private final AgentEvalSuiteRepository suiteRepository;
    private final AgentEvalCaseRepository caseRepository;
    private final AgentEvalRunRepository runRepository;
    private final AgentEvalCaseResultRepository resultRepository;
    private final ObjectProvider<AgentWorkflowRuntimeService> runtimeServiceProvider;
    private final ObjectMapper objectMapper;

    public AgentEvaluationService(AgentDefinitionRepository agentDefinitionRepository,
                                  AgentWorkflowVersionRepository versionRepository,
                                  AgentEvalSuiteRepository suiteRepository,
                                  AgentEvalCaseRepository caseRepository,
                                  AgentEvalRunRepository runRepository,
                                  AgentEvalCaseResultRepository resultRepository,
                                  ObjectProvider<AgentWorkflowRuntimeService> runtimeServiceProvider,
                                  ObjectMapper objectMapper) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.versionRepository = versionRepository;
        this.suiteRepository = suiteRepository;
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createSuite(String orgId, String agentId, SuiteCommand command) {
        requireAgent(orgId, agentId);
        AgentEvalSuiteEntity created = suiteRepository.save(new AgentEvalSuiteEntity(
                orgId,
                agentId,
                requireText(command.name(), "Evaluation suite name is required"),
                trimToNull(command.description()),
                normalizeGateMode(command.gateMode()),
                normalizePassRate(command.minPassRate())));
        return suitePayload(created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSuites(String orgId, String agentId) {
        requireAgent(orgId, agentId);
        return suiteRepository.findByOrgIdAndAgentIdAndStatusOrderByIdAsc(
                        orgId,
                        agentId,
                        AgentEvalSuiteEntity.STATUS_ACTIVE)
                .stream()
                .map(this::suitePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> addCase(String orgId, String agentId, Long suiteId, CaseCommand command) {
        AgentEvalSuiteEntity suite = requireSuite(orgId, agentId, suiteId);
        AgentEvalCaseEntity created = caseRepository.save(new AgentEvalCaseEntity(
                orgId,
                agentId,
                suite.getId(),
                requireText(command.name(), "Evaluation case name is required"),
                requireText(command.inputText(), "Evaluation case inputText is required"),
                normalizeAssertionType(command.assertionType()),
                trimToNull(command.expectedText()),
                trimToNull(command.forbiddenText()),
                trimToNull(command.expectedStatus()),
                trimToNull(command.requiredToolName()),
                trimToNull(command.forbiddenToolName()),
                normalizePriority(command.priority())));
        return casePayload(created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCases(String orgId, String agentId, Long suiteId) {
        AgentEvalSuiteEntity suite = requireSuite(orgId, agentId, suiteId);
        return caseRepository.findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(
                        orgId,
                        suite.getId(),
                        AgentEvalCaseEntity.STATUS_ACTIVE)
                .stream()
                .map(this::casePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> runSuite(String orgId, String agentId, Long suiteId, Integer versionNo) {
        AgentEvalSuiteEntity suite = requireSuite(orgId, agentId, suiteId);
        Integer targetVersionNo = requireVersionNo(orgId, agentId, versionNo);
        List<AgentEvalCaseEntity> cases = caseRepository.findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(
                orgId,
                suite.getId(),
                AgentEvalCaseEntity.STATUS_ACTIVE);
        Instant started = Instant.now();
        ArrayList<EvalOutcome> outcomes = new ArrayList<>();
        for (AgentEvalCaseEntity evalCase : cases) {
            outcomes.add(evaluateCase(orgId, agentId, targetVersionNo, evalCase));
        }
        int caseCount = outcomes.size();
        int passed = (int) outcomes.stream().filter(EvalOutcome::passed).count();
        int failed = caseCount - passed;
        int p0Failed = (int) outcomes.stream()
                .filter(item -> !item.passed() && AgentEvalCaseEntity.PRIORITY_P0.equals(item.priority()))
                .count();
        int safetyFailed = (int) outcomes.stream()
                .filter(item -> !item.passed() && AgentEvalCaseEntity.PRIORITY_SAFETY.equals(item.priority()))
                .count();
        double passRate = caseCount == 0 ? 0.0d : (double) passed / caseCount;
        String status = caseCount == 0
                ? AgentEvalRunEntity.STATUS_EMPTY
                : failed == 0 && passRate >= suite.getMinPassRate()
                ? AgentEvalRunEntity.STATUS_PASSED
                : AgentEvalRunEntity.STATUS_FAILED;
        Instant finished = Instant.now();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suiteId", suite.getId());
        summary.put("suiteName", suite.getName());
        summary.put("gateMode", suite.getGateMode());
        summary.put("minPassRate", suite.getMinPassRate());
        summary.put("caseCount", caseCount);
        summary.put("passedCount", passed);
        summary.put("failedCount", failed);
        summary.put("p0FailedCount", p0Failed);
        summary.put("safetyFailedCount", safetyFailed);
        summary.put("passRate", passRate);
        AgentEvalRunEntity run = runRepository.save(new AgentEvalRunEntity(
                orgId,
                agentId,
                suite.getId(),
                targetVersionNo,
                status,
                caseCount,
                passed,
                failed,
                p0Failed,
                safetyFailed,
                passRate,
                writeJson(summary),
                started,
                finished));
        for (EvalOutcome outcome : outcomes) {
            resultRepository.save(new AgentEvalCaseResultEntity(
                    orgId,
                    agentId,
                    run.getId(),
                    outcome.caseId(),
                    targetVersionNo,
                    outcome.passed() ? AgentEvalCaseResultEntity.STATUS_PASSED : AgentEvalCaseResultEntity.STATUS_FAILED,
                    outcome.assertionType(),
                    outcome.actualStatus(),
                    truncate(outcome.output(), 1200),
                    writeJson(outcome.summary())));
        }
        return runPayload(run);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(String orgId, String agentId, Long suiteId) {
        AgentEvalSuiteEntity suite = requireSuite(orgId, agentId, suiteId);
        return runRepository.findByOrgIdAndSuiteIdOrderByCreatedAtDesc(orgId, suite.getId())
                .stream()
                .map(this::runPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResults(String orgId, String agentId, Long runId) {
        AgentEvalRunEntity run = runRepository.findById(runId)
                .filter(item -> orgId.equals(item.getOrgId()) && agentId.equals(item.getAgentId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run not found"));
        return resultRepository.findByOrgIdAndRunIdOrderByIdAsc(orgId, run.getId())
                .stream()
                .map(this::resultPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationGateSummary latestGateSummary(String orgId, String agentId, Integer versionNo) {
        if (versionNo == null) {
            return new EvaluationGateSummary("not_checked", false, 0, 0, 0, 0, List.of());
        }
        List<AgentEvalSuiteEntity> suites = suiteRepository.findByOrgIdAndAgentIdAndStatusOrderByIdAsc(
                orgId,
                agentId,
                AgentEvalSuiteEntity.STATUS_ACTIVE);
        if (suites.isEmpty()) {
            return new EvaluationGateSummary("not_configured", false, 0, 0, 0, 0, List.of());
        }
        ArrayList<Map<String, Object>> suiteSummaries = new ArrayList<>();
        int totalCases = 0;
        int missingRunCount = 0;
        int failedRunCount = 0;
        boolean blocked = false;
        boolean warning = false;
        for (AgentEvalSuiteEntity suite : suites) {
            long caseCount = caseRepository.countByOrgIdAndSuiteIdAndStatus(
                    orgId,
                    suite.getId(),
                    AgentEvalCaseEntity.STATUS_ACTIVE);
            totalCases += (int) caseCount;
            AgentEvalRunEntity latest = runRepository.findByOrgIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
                            orgId,
                            agentId,
                            versionNo)
                    .stream()
                    .filter(item -> Objects.equals(item.getSuiteId(), suite.getId()))
                    .findFirst()
                    .orElse(null);
            boolean blocking = AgentEvalSuiteEntity.GATE_MODE_BLOCKING.equals(suite.getGateMode()) && caseCount > 0;
            boolean missingRun = latest == null && caseCount > 0;
            boolean failedRun = latest != null && runFailsGate(suite, latest);
            if (missingRun) {
                missingRunCount++;
            }
            if (failedRun) {
                failedRunCount++;
            }
            if (blocking && (missingRun || failedRun)) {
                blocked = true;
            } else if (missingRun || failedRun || caseCount == 0) {
                warning = true;
            }
            suiteSummaries.add(suiteGatePayload(suite, caseCount, latest, missingRun, failedRun));
        }
        String status = blocked ? "blocked" : warning ? "warning" : "passed";
        return new EvaluationGateSummary(status, blocked, suites.size(), totalCases, missingRunCount, failedRunCount, suiteSummaries);
    }

    private EvalOutcome evaluateCase(String orgId, String agentId, Integer versionNo, AgentEvalCaseEntity evalCase) {
        AgentWorkflowRuntimeService.RuntimeExecutionResult result =
                runtimeServiceProvider.getObject()
                        .evaluateVersionForEvaluation(orgId, agentId, versionNo, evalCase.getInputText());
        String output = result.executionOutput() == null ? "" : result.executionOutput();
        String actualStatus = result.executionStatus() == null ? "" : result.executionStatus();
        List<String> trace = result.executionTrace() == null ? List.of() : result.executionTrace();
        Map<String, Object> context = result.contextSnapshot() == null ? Map.of() : result.contextSnapshot();
        String assertion = evalCase.getAssertionType();
        boolean passed = switch (assertion) {
            case AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS -> containsIgnoreCase(output, evalCase.getExpectedText());
            case AgentEvalCaseEntity.ASSERT_OUTPUT_NOT_CONTAINS -> !containsIgnoreCase(output, evalCase.getForbiddenText());
            case AgentEvalCaseEntity.ASSERT_STATUS_EQUALS -> equalsIgnoreCase(actualStatus, evalCase.getExpectedStatus());
            case AgentEvalCaseEntity.ASSERT_TOOL_CALLED -> traceContains(trace, "tool-invoke-best")
                    && toolScopeContains(context, evalCase.getRequiredToolName());
            case AgentEvalCaseEntity.ASSERT_TOOL_NOT_CALLED -> !toolScopeContains(context, evalCase.getForbiddenToolName());
            case AgentEvalCaseEntity.ASSERT_RAG_USED -> Boolean.TRUE.equals(context.get("knowledgeUsed"))
                    || traceContains(trace, "knowledge-search");
            case AgentEvalCaseEntity.ASSERT_HANDOFF_REQUESTED -> traceContains(trace, "handoff-request");
            case AgentEvalCaseEntity.ASSERT_SAFETY_REFUSAL -> containsIgnoreCase(output, evalCase.getExpectedText())
                    || equalsIgnoreCase(actualStatus, evalCase.getExpectedStatus());
            default -> false;
        };
        HashMap<String, Object> summary = new HashMap<>();
        summary.put("name", evalCase.getName());
        summary.put("priority", evalCase.getPriority());
        summary.put("assertionType", assertion);
        summary.put("passed", passed);
        summary.put("actualStatus", actualStatus);
        summary.put("expectedText", nullToEmpty(evalCase.getExpectedText()));
        summary.put("forbiddenText", nullToEmpty(evalCase.getForbiddenText()));
        summary.put("expectedStatus", nullToEmpty(evalCase.getExpectedStatus()));
        summary.put("requiredToolName", nullToEmpty(evalCase.getRequiredToolName()));
        summary.put("forbiddenToolName", nullToEmpty(evalCase.getForbiddenToolName()));
        summary.put("runMode", context.getOrDefault("runMode", ""));
        summary.put("executionTrace", trace);
        summary.put("contextSnapshot", context);
        return new EvalOutcome(
                evalCase.getId(),
                evalCase.getPriority(),
                assertion,
                passed,
                actualStatus,
                output,
                summary);
    }

    private boolean runFailsGate(AgentEvalSuiteEntity suite, AgentEvalRunEntity run) {
        return !AgentEvalRunEntity.STATUS_PASSED.equals(run.getStatus())
                || run.getP0FailedCount() > 0
                || run.getSafetyFailedCount() > 0
                || run.getPassRate() < suite.getMinPassRate();
    }

    private void requireAgent(String orgId, String agentId) {
        agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
    }

    private AgentEvalSuiteEntity requireSuite(String orgId, String agentId, Long suiteId) {
        return suiteRepository.findByIdAndOrgId(suiteId, orgId)
                .filter(item -> agentId.equals(item.getAgentId()))
                .filter(item -> AgentEvalSuiteEntity.STATUS_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation suite not found"));
    }

    private Integer requireVersionNo(String orgId, String agentId, Integer versionNo) {
        if (versionNo == null) {
            throw new IllegalArgumentException("versionNo is required");
        }
        versionRepository.findByOrgIdAndAgentIdAndVersionNo(orgId, agentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Agent workflow version not found"));
        return versionNo;
    }

    private Map<String, Object> suitePayload(AgentEvalSuiteEntity suite) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", suite.getId());
        row.put("agentId", suite.getAgentId());
        row.put("name", suite.getName());
        row.put("description", nullToEmpty(suite.getDescription()));
        row.put("status", suite.getStatus());
        row.put("gateMode", suite.getGateMode());
        row.put("minPassRate", suite.getMinPassRate());
        row.put("createdAt", suite.getCreatedAt().toString());
        row.put("updatedAt", suite.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> casePayload(AgentEvalCaseEntity evalCase) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", evalCase.getId());
        row.put("agentId", evalCase.getAgentId());
        row.put("suiteId", evalCase.getSuiteId());
        row.put("name", evalCase.getName());
        row.put("inputText", evalCase.getInputText());
        row.put("assertionType", evalCase.getAssertionType());
        row.put("expectedText", nullToEmpty(evalCase.getExpectedText()));
        row.put("forbiddenText", nullToEmpty(evalCase.getForbiddenText()));
        row.put("expectedStatus", nullToEmpty(evalCase.getExpectedStatus()));
        row.put("requiredToolName", nullToEmpty(evalCase.getRequiredToolName()));
        row.put("forbiddenToolName", nullToEmpty(evalCase.getForbiddenToolName()));
        row.put("priority", evalCase.getPriority());
        row.put("status", evalCase.getStatus());
        row.put("createdAt", evalCase.getCreatedAt().toString());
        row.put("updatedAt", evalCase.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> runPayload(AgentEvalRunEntity run) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", run.getId());
        row.put("agentId", run.getAgentId());
        row.put("suiteId", run.getSuiteId());
        row.put("versionNo", run.getVersionNo());
        row.put("status", run.getStatus());
        row.put("caseCount", run.getCaseCount());
        row.put("passedCount", run.getPassedCount());
        row.put("failedCount", run.getFailedCount());
        row.put("p0FailedCount", run.getP0FailedCount());
        row.put("safetyFailedCount", run.getSafetyFailedCount());
        row.put("passRate", run.getPassRate());
        row.put("summaryJson", nullToEmpty(run.getSummaryJson()));
        row.put("startedAt", run.getStartedAt().toString());
        row.put("finishedAt", run.getFinishedAt() == null ? "" : run.getFinishedAt().toString());
        return row;
    }

    private Map<String, Object> resultPayload(AgentEvalCaseResultEntity result) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", result.getId());
        row.put("agentId", result.getAgentId());
        row.put("runId", result.getRunId());
        row.put("caseId", result.getCaseId());
        row.put("versionNo", result.getVersionNo());
        row.put("status", result.getStatus());
        row.put("assertionType", result.getAssertionType());
        row.put("actualStatus", nullToEmpty(result.getActualStatus()));
        row.put("outputPreview", nullToEmpty(result.getOutputPreview()));
        row.put("resultSummaryJson", nullToEmpty(result.getResultSummaryJson()));
        row.put("createdAt", result.getCreatedAt().toString());
        return row;
    }

    private Map<String, Object> suiteGatePayload(AgentEvalSuiteEntity suite,
                                                 long caseCount,
                                                 AgentEvalRunEntity latest,
                                                 boolean missingRun,
                                                 boolean failedRun) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", suite.getId());
        row.put("name", suite.getName());
        row.put("gateMode", suite.getGateMode());
        row.put("minPassRate", suite.getMinPassRate());
        row.put("caseCount", caseCount);
        row.put("missingRun", missingRun);
        row.put("failedRun", failedRun);
        row.put("latestRun", latest == null ? Map.of() : runPayload(latest));
        return row;
    }

    private String normalizeAssertionType(String value) {
        String normalized = normalizeUpper(value, AgentEvalCaseEntity.ASSERT_STATUS_EQUALS);
        if (List.of(
                AgentEvalCaseEntity.ASSERT_OUTPUT_CONTAINS,
                AgentEvalCaseEntity.ASSERT_OUTPUT_NOT_CONTAINS,
                AgentEvalCaseEntity.ASSERT_STATUS_EQUALS,
                AgentEvalCaseEntity.ASSERT_TOOL_CALLED,
                AgentEvalCaseEntity.ASSERT_TOOL_NOT_CALLED,
                AgentEvalCaseEntity.ASSERT_RAG_USED,
                AgentEvalCaseEntity.ASSERT_HANDOFF_REQUESTED,
                AgentEvalCaseEntity.ASSERT_SAFETY_REFUSAL).contains(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("Unsupported assertionType: " + value);
    }

    private String normalizePriority(String value) {
        String normalized = normalizeUpper(value, "P1");
        if (List.of("P0", "P1", "P2", AgentEvalCaseEntity.PRIORITY_SAFETY).contains(normalized)) {
            return normalized;
        }
        return "P1";
    }

    private String normalizeGateMode(String value) {
        String normalized = normalizeUpper(value, AgentEvalSuiteEntity.GATE_MODE_BLOCKING);
        if (AgentEvalSuiteEntity.GATE_MODE_WARN_ONLY.equals(normalized)) {
            return normalized;
        }
        return AgentEvalSuiteEntity.GATE_MODE_BLOCKING;
    }

    private String normalizeUpper(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Double normalizePassRate(Double value) {
        if (value == null) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        if (expected == null || expected.isBlank()) {
            return value == null || value.isBlank();
        }
        return expected.equalsIgnoreCase(value == null ? "" : value);
    }

    private boolean traceContains(List<String> trace, String token) {
        return trace.stream().anyMatch(item -> item != null && item.contains(token));
    }

    private boolean toolScopeContains(Map<String, Object> context, String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Boolean.TRUE.equals(context.get("toolInvoked"));
        }
        Object value = context.get("allowedToolNames");
        if (value instanceof List<?> list) {
            return list.stream().anyMatch(item -> toolName.equalsIgnoreCase(String.valueOf(item)));
        }
        return false;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    public record SuiteCommand(
            String name,
            String description,
            String gateMode,
            Double minPassRate
    ) {
    }

    public record CaseCommand(
            String name,
            String inputText,
            String assertionType,
            String expectedText,
            String forbiddenText,
            String expectedStatus,
            String requiredToolName,
            String forbiddenToolName,
            String priority
    ) {
    }

    public record EvaluationGateSummary(
            String status,
            boolean blocked,
            int suiteCount,
            int caseCount,
            int missingRunCount,
            int failedRunCount,
            List<Map<String, Object>> suites
    ) {
    }

    private record EvalOutcome(
            Long caseId,
            String priority,
            String assertionType,
            boolean passed,
            String actualStatus,
            String output,
            Map<String, Object> summary
    ) {
    }
}
