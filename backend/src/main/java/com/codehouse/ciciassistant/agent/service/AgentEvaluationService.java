package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseResultEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseResultRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvaluationService {

    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository versionRepository;
    private final AgentEvalSuiteRepository suiteRepository;
    private final AgentEvalSuiteBindingRepository bindingRepository;
    private final AgentEvalCaseRepository caseRepository;
    private final AgentEvalRunRepository runRepository;
    private final AgentEvalCaseResultRepository resultRepository;
    private final ObjectProvider<AgentWorkflowRuntimeService> runtimeServiceProvider;
    private final ObjectProvider<ChatOrchestratorService> chatOrchestratorProvider;
    private final AgentEvaluationAssertionEngine assertionEngine;
    private final ObjectMapper objectMapper;

    public AgentEvaluationService(AgentDefinitionRepository agentDefinitionRepository,
                                  AgentWorkflowVersionRepository versionRepository,
                                  AgentEvalSuiteRepository suiteRepository,
                                  AgentEvalSuiteBindingRepository bindingRepository,
                                  AgentEvalCaseRepository caseRepository,
                                  AgentEvalRunRepository runRepository,
                                  AgentEvalCaseResultRepository resultRepository,
                                  ObjectProvider<AgentWorkflowRuntimeService> runtimeServiceProvider,
                                  ObjectProvider<ChatOrchestratorService> chatOrchestratorProvider,
                                  AgentEvaluationAssertionEngine assertionEngine,
                                  ObjectMapper objectMapper) {
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.versionRepository = versionRepository;
        this.suiteRepository = suiteRepository;
        this.bindingRepository = bindingRepository;
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.runtimeServiceProvider = runtimeServiceProvider;
        this.chatOrchestratorProvider = chatOrchestratorProvider;
        this.assertionEngine = assertionEngine;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createSuite(String companyId, String agentId, SuiteCommand command) {
        requireAgent(companyId, agentId);
        AgentEvalSuiteEntity created = suiteRepository.save(new AgentEvalSuiteEntity(
                companyId,
                agentId,
                requireText(command.name(), "Evaluation suite name is required"),
                trimToNull(command.description()),
                normalizeGateMode(command.gateMode()),
                normalizePassRate(command.minPassRate())));
        return suitePayload(created);
    }

    @Transactional
    public Map<String, Object> createTenantSuite(String companyId,
                                                 String agentId,
                                                 AdvancedSuiteCommand command,
                                                 String createdBy) {
        requireAgent(companyId, agentId);
        AgentEvalSuiteEntity created = suiteRepository.save(new AgentEvalSuiteEntity(
                companyId,
                agentId,
                requireText(command.name(), "Evaluation suite name is required"),
                trimToNull(command.description()),
                normalizeGateMode(command.gateMode()),
                normalizePassRate(command.minPassRate()),
                AgentEvalSuiteEntity.SCOPE_TENANT_PRIVATE,
                AgentEvalSuiteEntity.VISIBILITY_TENANT_ONLY,
                AgentEvalSuiteEntity.RELEASE_PUBLISHED,
                trimToNull(command.templateCode()),
                1,
                trimToNull(command.appCode()),
                trimToNull(command.industryCode()),
                false,
                false,
                trimToNull(createdBy)));
        return suitePayload(created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listSuites(String companyId, String agentId) {
        requireAgent(companyId, agentId);
        return applicableSuites(companyId, agentId)
                .stream()
                .map(this::suitePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> addCase(String companyId, String agentId, Long suiteId, CaseCommand command) {
        AgentEvalSuiteEntity suite = requireEditableTenantSuite(companyId, agentId, suiteId);
        AgentEvalCaseEntity created = caseRepository.save(new AgentEvalCaseEntity(
                companyId,
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

    @Transactional
    public Map<String, Object> addAdvancedCase(String companyId,
                                               String agentId,
                                               Long suiteId,
                                               AdvancedCaseCommand command) {
        AgentEvalSuiteEntity suite = requireEditableTenantSuite(companyId, agentId, suiteId);
        AgentEvalCaseEntity created = caseRepository.save(new AgentEvalCaseEntity(
                companyId,
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
                normalizePriority(command.priority()),
                trimToNull(command.caseKey()),
                normalizeUpper(command.category(), "ANSWER_QUALITY"),
                normalizeJson(command.conversationHistoryJson()),
                normalizeJson(command.fixtureJson()),
                normalizeJson(command.assertionConfigJson()),
                normalizeJson(command.judgeConfigJson()),
                normalizeJson(command.tagsJson()),
                trimToNull(command.createdFromTraceId()),
                false,
                normalizeUpper(command.reviewStatus(), "APPROVED"),
                normalizeUpper(command.redactionStatus(), "NOT_REQUIRED")));
        return casePayload(created);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCases(String companyId, String agentId, Long suiteId) {
        AgentEvalSuiteEntity suite = requireSuite(companyId, agentId, suiteId);
        return caseRepository.findBySuiteIdAndStatusOrderByIdAsc(suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE)
                .stream()
                .map(item -> casePayload(item, suite.isPlatformOwned() && item.isHiddenCase()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCasesForManagement(String companyId, String agentId, Long suiteId) {
        AgentEvalSuiteEntity suite = requireSuite(companyId, agentId, suiteId);
        return caseRepository.findBySuiteIdOrderByIdAsc(suite.getId()).stream()
                .map(item -> casePayload(item, suite.isPlatformOwned() && item.isHiddenCase()))
                .toList();
    }

    @Transactional
    public Map<String, Object> updateAdvancedCase(String companyId,
                                                  String agentId,
                                                  Long suiteId,
                                                  Long caseId,
                                                  AdvancedCaseCommand command) {
        AgentEvalSuiteEntity suite = requireEditableTenantSuite(companyId, agentId, suiteId);
        AgentEvalCaseEntity evalCase = caseRepository.findById(caseId)
                .filter(item -> suite.getId().equals(item.getSuiteId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation case not found"));
        evalCase.update(
                requireText(command.name(), "Evaluation case name is required"),
                requireText(command.inputText(), "Evaluation case inputText is required"),
                normalizeAssertionType(command.assertionType()),
                trimToNull(command.expectedText()),
                trimToNull(command.forbiddenText()),
                trimToNull(command.expectedStatus()),
                trimToNull(command.requiredToolName()),
                trimToNull(command.forbiddenToolName()),
                normalizePriority(command.priority()),
                trimToNull(command.caseKey()),
                normalizeUpper(command.category(), "ANSWER_QUALITY"),
                normalizeJson(command.conversationHistoryJson()),
                normalizeJson(command.fixtureJson()),
                normalizeJson(command.assertionConfigJson()),
                normalizeJson(command.judgeConfigJson()),
                normalizeJson(command.tagsJson()),
                false,
                normalizeUpper(command.reviewStatus(), "PENDING"),
                normalizeUpper(command.redactionStatus(), "NOT_REQUIRED"));
        return casePayload(caseRepository.save(evalCase));
    }

    public Map<String, Object> runSuite(String companyId, String agentId, Long suiteId, Integer versionNo) {
        return runSuite(companyId, agentId, suiteId, new RunCommand(
                versionNo, "CANDIDATE", null, "MANUAL", null));
    }

    public Map<String, Object> runSuite(String companyId, String agentId, Long suiteId, RunCommand command) {
        AgentEvalSuiteEntity suite = requireSuite(companyId, agentId, suiteId);
        Integer targetVersionNo = requireVersionNo(companyId, agentId, command.versionNo());
        String targetType = normalizeRunTargetType(command.targetType());
        Integer baselineVersionNo = command.baselineVersionNo();
        if ("COMPARE".equals(targetType)) {
            if (baselineVersionNo == null) {
                throw new IllegalArgumentException("baselineVersionNo is required for COMPARE runs");
            }
            requireVersionNo(companyId, agentId, baselineVersionNo);
        }
        List<AgentEvalCaseEntity> cases = caseRepository.findBySuiteIdAndStatusOrderByIdAsc(
                suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE);
        Instant started = Instant.now();
        ArrayList<EvalOutcome> outcomes = new ArrayList<>();
        for (AgentEvalCaseEntity evalCase : cases) {
            outcomes.add(evaluateCase(companyId, agentId, targetVersionNo, evalCase));
        }
        ArrayList<EvalOutcome> baselineOutcomes = new ArrayList<>();
        if ("COMPARE".equals(targetType)) {
            for (AgentEvalCaseEntity evalCase : cases) {
                baselineOutcomes.add(evaluateCase(companyId, agentId, baselineVersionNo, evalCase));
            }
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
        long totalElapsedMs = outcomes.stream().mapToLong(EvalOutcome::elapsedMs).sum();
        long avgLatencyMs = caseCount == 0 ? 0 : totalElapsedMs / caseCount;
        List<EvalOutcome> toolOutcomes = outcomes.stream()
                .filter(item -> item.assertionType().contains("TOOL"))
                .toList();
        List<EvalOutcome> ragOutcomes = outcomes.stream()
                .filter(item -> item.assertionType().contains("RAG"))
                .toList();
        double toolCallAccuracy = ratioPassed(toolOutcomes);
        double ragHitRate = ratioPassed(ragOutcomes);
        String snapshotFingerprint = snapshotFingerprint(companyId, agentId, targetVersionNo, suite, cases);
        Map<String, Object> runtimeSnapshot = runtimeSnapshot(
                companyId, agentId, targetVersionNo, suite, cases, outcomes, snapshotFingerprint);
        summary.put("targetType", targetType);
        summary.put("baselineVersionNo", baselineVersionNo);
        summary.put("triggerType", normalizeUpper(command.triggerType(), "MANUAL"));
        summary.put("snapshotFingerprint", snapshotFingerprint);
        summary.put("avgLatencyMs", avgLatencyMs);
        summary.put("totalElapsedMs", totalElapsedMs);
        summary.put("toolCallAccuracy", toolCallAccuracy);
        summary.put("ragHitRate", ragHitRate);
        if (!baselineOutcomes.isEmpty()) {
            summary.put("comparison", comparisonSummary(outcomes, baselineOutcomes, baselineVersionNo, passRate));
        }
        AgentEvalRunEntity run = runRepository.save(new AgentEvalRunEntity(
                companyId,
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
        run.attachExecutionMetadata(
                targetType,
                baselineVersionNo,
                normalizeUpper(command.triggerType(), "MANUAL"),
                writeJson(runtimeSnapshot),
                snapshotFingerprint,
                avgLatencyMs,
                totalElapsedMs,
                toolCallAccuracy,
                ragHitRate,
                trimToNull(command.createdBy()));
        runRepository.save(run);
        Map<Long, EvalOutcome> baselineByCase = baselineOutcomes.stream()
                .collect(java.util.stream.Collectors.toMap(EvalOutcome::caseId, item -> item));
        for (EvalOutcome outcome : outcomes) {
            Map<String, Object> resultSummary = new LinkedHashMap<>(outcome.summary());
            EvalOutcome baselineOutcome = baselineByCase.get(outcome.caseId());
            if (baselineOutcome != null) {
                resultSummary.put("baselineComparison", caseComparison(outcome, baselineOutcome));
            }
            AgentEvalCaseResultEntity savedResult = new AgentEvalCaseResultEntity(
                    companyId,
                    agentId,
                    run.getId(),
                    outcome.caseId(),
                    targetVersionNo,
                    outcome.passed() ? AgentEvalCaseResultEntity.STATUS_PASSED : AgentEvalCaseResultEntity.STATUS_FAILED,
                    outcome.assertionType(),
                    outcome.actualStatus(),
                    truncate(outcome.output(), 1200),
                    writeJson(resultSummary));
            savedResult.attachEvidence(
                    trimToNull(outcome.failureCategory()),
                    trimToNull(outcome.failureSummary()),
                    writeJson(outcome.assertionResults()),
                    trimToNull(outcome.traceId()),
                    outcome.score(),
                    outcome.elapsedMs(),
                    outcome.toolCallCount(),
                    outcome.ragHitCount());
            resultRepository.save(savedResult);
        }
        return runPayload(run);
    }

    private Map<String, Object> comparisonSummary(List<EvalOutcome> candidate,
                                                  List<EvalOutcome> baseline,
                                                  Integer baselineVersionNo,
                                                  double candidatePassRate) {
        Map<Long, EvalOutcome> baselineByCase = baseline.stream()
                .collect(java.util.stream.Collectors.toMap(EvalOutcome::caseId, item -> item));
        List<Map<String, Object>> caseComparisons = candidate.stream()
                .filter(item -> baselineByCase.containsKey(item.caseId()))
                .map(item -> caseComparison(item, baselineByCase.get(item.caseId())))
                .toList();
        double baselinePassRate = baseline.isEmpty() ? 0.0d
                : (double) baseline.stream().filter(EvalOutcome::passed).count() / baseline.size();
        return Map.of(
                "baselineVersionNo", baselineVersionNo,
                "baselinePassRate", baselinePassRate,
                "candidatePassRate", candidatePassRate,
                "passRateDelta", candidatePassRate - baselinePassRate,
                "regressionCount", caseComparisons.stream().filter(item -> "REGRESSED".equals(item.get("change"))).count(),
                "improvementCount", caseComparisons.stream().filter(item -> "IMPROVED".equals(item.get("change"))).count(),
                "baselineTotalElapsedMs", baseline.stream().mapToLong(EvalOutcome::elapsedMs).sum(),
                "cases", caseComparisons);
    }

    private Map<String, Object> caseComparison(EvalOutcome candidate, EvalOutcome baseline) {
        String change = candidate.passed() == baseline.passed()
                ? "UNCHANGED"
                : candidate.passed() ? "IMPROVED" : "REGRESSED";
        return Map.of(
                "caseId", candidate.caseId(),
                "change", change,
                "candidatePassed", candidate.passed(),
                "baselinePassed", baseline.passed(),
                "candidateScore", candidate.score(),
                "baselineScore", baseline.score(),
                "candidateStatus", candidate.actualStatus(),
                "baselineStatus", baseline.actualStatus());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listRuns(String companyId, String agentId, Long suiteId) {
        AgentEvalSuiteEntity suite = requireSuite(companyId, agentId, suiteId);
        return runRepository.findByCompanyIdAndSuiteIdOrderByCreatedAtDesc(companyId, suite.getId())
                .stream()
                .map(this::runPayload)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listResults(String companyId, String agentId, Long runId) {
        AgentEvalRunEntity run = runRepository.findById(runId)
                .filter(item -> companyId.equals(item.getCompanyId()) && agentId.equals(item.getAgentId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run not found"));
        AgentEvalSuiteEntity suite = suiteRepository.findById(run.getSuiteId())
                .orElseThrow(() -> new IllegalArgumentException("Evaluation suite not found"));
        Map<Long, AgentEvalCaseEntity> cases = caseRepository.findBySuiteIdOrderByIdAsc(suite.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(AgentEvalCaseEntity::getId, item -> item));
        return resultRepository.findByCompanyIdAndRunIdOrderByIdAsc(companyId, run.getId())
                .stream()
                .map(result -> {
                    AgentEvalCaseEntity evalCase = cases.get(result.getCaseId());
                    boolean redacted = suite.isPlatformOwned() && evalCase != null && evalCase.isHiddenCase();
                    Map<String, Object> payload = resultPayload(result);
                    if (redacted) {
                        payload.put("outputPreview", "");
                        payload.put("resultSummaryJson", "");
                        payload.put("assertionResultsJson", "");
                        payload.put("traceId", "");
                        payload.put("redacted", true);
                    } else {
                        payload.put("redacted", false);
                    }
                    return payload;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public EvaluationGateSummary latestGateSummary(String companyId, String agentId, Integer versionNo) {
        if (versionNo == null) {
            return new EvaluationGateSummary("not_checked", false, 0, 0, 0, 0, List.of());
        }
        List<AgentEvalSuiteEntity> suites = applicableSuites(companyId, agentId);
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
            long caseCount = caseRepository.countBySuiteIdAndStatus(suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE);
            totalCases += (int) caseCount;
            AgentEvalRunEntity latest = runRepository.findByCompanyIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
                            companyId,
                            agentId,
                            versionNo)
                    .stream()
                    .filter(item -> Objects.equals(item.getSuiteId(), suite.getId()))
                    .findFirst()
                    .orElse(null);
            boolean blocking = AgentEvalSuiteEntity.GATE_MODE_BLOCKING.equals(suite.getGateMode()) && caseCount > 0;
            boolean missingRun = latest == null && caseCount > 0;
            boolean staleRun = latest != null && !Objects.equals(
                    latest.getSnapshotFingerprint(),
                    snapshotFingerprint(companyId, agentId, versionNo, suite,
                            caseRepository.findBySuiteIdAndStatusOrderByIdAsc(suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE)));
            boolean failedRun = latest != null && (runFailsGate(suite, latest) || staleRun);
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
            suiteSummaries.add(suiteGatePayload(suite, caseCount, latest, missingRun, failedRun, staleRun));
        }
        String status = blocked ? "blocked" : warning ? "warning" : "passed";
        return new EvaluationGateSummary(status, blocked, suites.size(), totalCases, missingRunCount, failedRunCount, suiteSummaries);
    }

    private EvalOutcome evaluateCase(String companyId, String agentId, Integer versionNo, AgentEvalCaseEntity evalCase) {
        long started = System.nanoTime();
        try {
            AgentWorkflowRuntimeService.RuntimeExecutionResult result =
                    runtimeServiceProvider.getObject()
                            .evaluateVersionForEvaluation(companyId, agentId, versionNo, evalCase.getInputText());
            ChatOrchestratorService.EvaluationDryRunResult modelResult = chatOrchestratorProvider.getObject()
                    .evaluateNoSideEffects(
                            companyId,
                            "evaluation-system",
                            agentId,
                            versionNo,
                            evalCase.getInputText(),
                            evalCase.getConversationHistoryJson(),
                            evalCase.getFixtureJson());
            long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            String output = modelResult.output() == null ? "" : modelResult.output();
            String actualStatus = result.executionStatus() == null ? "" : result.executionStatus();
            List<String> trace = new ArrayList<>();
            if (result.executionTrace() != null) trace.addAll(result.executionTrace());
            if (modelResult.trace() != null) trace.addAll(modelResult.trace());
            Map<String, Object> context = new LinkedHashMap<>();
            if (result.contextSnapshot() != null) context.putAll(result.contextSnapshot());
            if (modelResult.context() != null) context.putAll(modelResult.context());
            AgentEvaluationAssertionEngine.AssertionOutcome assertions = assertionEngine.evaluate(
                    evalCase, output, actualStatus, trace, context, elapsedMs);
            HashMap<String, Object> summary = baseCaseSummary(evalCase, actualStatus, context, trace);
            summary.put("passed", assertions.passed());
            summary.put("score", assertions.score());
            summary.put("failureCategory", assertions.failureCategory());
            summary.put("failureSummary", assertions.failureSummary());
            summary.put("assertionResults", assertions.assertionResults());
            summary.put("elapsedMs", elapsedMs);
            return new EvalOutcome(
                    evalCase.getId(),
                    evalCase.getPriority(),
                    evalCase.getAssertionType(),
                    assertions.passed(),
                    actualStatus,
                    output,
                    assertions.score(),
                    assertions.failureCategory(),
                    assertions.failureSummary(),
                    assertions.assertionResults(),
                    elapsedMs,
                    assertions.toolCallCount(),
                    assertions.ragHitCount(),
                    stringValue(context.get("traceId")),
                    summary);
        } catch (RuntimeException ex) {
            long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            HashMap<String, Object> summary = baseCaseSummary(evalCase, "ERROR", Map.of(), List.of());
            summary.put("passed", false);
            summary.put("score", 0.0d);
            summary.put("failureCategory", "RUNTIME_ERROR");
            summary.put("failureSummary", safeError(ex));
            summary.put("elapsedMs", elapsedMs);
            return new EvalOutcome(
                    evalCase.getId(), evalCase.getPriority(), evalCase.getAssertionType(), false,
                    "ERROR", "", 0.0d, "RUNTIME_ERROR", safeError(ex), List.of(), elapsedMs,
                    0, 0, "", summary);
        }
    }

    private HashMap<String, Object> baseCaseSummary(AgentEvalCaseEntity evalCase,
                                                     String actualStatus,
                                                     Map<String, Object> context,
                                                     List<String> trace) {
        HashMap<String, Object> summary = new HashMap<>();
        summary.put("name", evalCase.getName());
        summary.put("caseKey", nullToEmpty(evalCase.getCaseKey()));
        summary.put("category", nullToEmpty(evalCase.getCategory()));
        summary.put("priority", evalCase.getPriority());
        summary.put("assertionType", evalCase.getAssertionType());
        summary.put("actualStatus", actualStatus);
        summary.put("expectedText", nullToEmpty(evalCase.getExpectedText()));
        summary.put("forbiddenText", nullToEmpty(evalCase.getForbiddenText()));
        summary.put("expectedStatus", nullToEmpty(evalCase.getExpectedStatus()));
        summary.put("requiredToolName", nullToEmpty(evalCase.getRequiredToolName()));
        summary.put("forbiddenToolName", nullToEmpty(evalCase.getForbiddenToolName()));
        summary.put("runMode", context.getOrDefault("runMode", ""));
        summary.put("executionTrace", trace);
        summary.put("contextSnapshot", context);
        return summary;
    }

    private boolean runFailsGate(AgentEvalSuiteEntity suite, AgentEvalRunEntity run) {
        return !AgentEvalRunEntity.STATUS_PASSED.equals(run.getStatus())
                || run.getP0FailedCount() > 0
                || run.getSafetyFailedCount() > 0
                || run.getPassRate() < suite.getMinPassRate();
    }

    private Map<String, Object> runtimeSnapshot(String companyId,
                                                String agentId,
                                                Integer versionNo,
                                                AgentEvalSuiteEntity suite,
                                                List<AgentEvalCaseEntity> cases,
                                                List<EvalOutcome> outcomes,
                                                String fingerprint) {
        AgentDefinitionEntity agent = agentDefinitionRepository.findByCompanyIdAndAgentId(companyId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
        AgentWorkflowVersionEntity version = versionRepository.findByCompanyIdAndAgentIdAndVersionNo(companyId, agentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Agent workflow version not found"));
        Map<String, Object> runtimeEvidence = outcomes.isEmpty()
                ? Map.of()
                : mapValue(outcomes.get(0).summary().get("contextSnapshot"));
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fingerprint", fingerprint);
        snapshot.put("capturedAt", Instant.now().toString());
        snapshot.put("runMode", "EVALUATION");
        snapshot.put("agent", Map.of(
                "agentId", agentId,
                "name", agent.getName(),
                "model", nullToEmpty(agent.getModel()),
                "safetyLevel", nullToEmpty(agent.getSafetyLevel()),
                "executionMode", nullToEmpty(agent.getExecutionMode()),
                "updatedAt", agent.getUpdatedAt().toString()));
        snapshot.put("workflowVersion", Map.of(
                "id", version.getId(),
                "versionNo", version.getVersionNo(),
                "versionLabel", nullToEmpty(version.getVersionLabel()),
                "compileFingerprint", nullToEmpty(version.getCompileFingerprint()),
                "publishStatus", nullToEmpty(version.getPublishStatus()),
                "createdAt", version.getCreatedAt().toString()));
        snapshot.put("suite", Map.of(
                "id", suite.getId(),
                "templateCode", nullToEmpty(suite.getTemplateCode()),
                "versionNo", suite.getVersionNo(),
                "scopeType", suite.getScopeType(),
                "updatedAt", suite.getUpdatedAt().toString(),
                "caseCount", cases.size()));
        snapshot.put("resolvedSkillVersions", runtimeEvidence.getOrDefault("resolvedSkillVersions", List.of()));
        snapshot.put("policyBundleCode", runtimeEvidence.getOrDefault("policyBundleCode", ""));
        snapshot.put("policyBundleVersionNo", runtimeEvidence.getOrDefault("policyBundleVersionNo", ""));
        snapshot.put("allowedToolNames", runtimeEvidence.getOrDefault("allowedToolNames", List.of()));
        snapshot.put("evaluationVersionNo", runtimeEvidence.getOrDefault("evaluationVersionNo", versionNo));
        return snapshot;
    }

    private String snapshotFingerprint(String companyId,
                                       String agentId,
                                       Integer versionNo,
                                       AgentEvalSuiteEntity suite,
                                       List<AgentEvalCaseEntity> cases) {
        AgentWorkflowVersionEntity version = versionRepository.findByCompanyIdAndAgentIdAndVersionNo(companyId, agentId, versionNo)
                .orElseThrow(() -> new IllegalArgumentException("Agent workflow version not found"));
        StringBuilder source = new StringBuilder();
        source.append(companyId).append('|').append(agentId).append('|').append(versionNo).append('|')
                .append(nullToEmpty(version.getCompileFingerprint())).append('|')
                .append(suite.getId()).append('|').append(suite.getVersionNo()).append('|')
                .append(suite.getUpdatedAt()).append('|');
        cases.stream().sorted(Comparator.comparing(AgentEvalCaseEntity::getId)).forEach(item -> source
                .append(item.getId()).append(':')
                .append(item.getUpdatedAt()).append(':')
                .append(nullToEmpty(item.getAssertionConfigJson())).append('|'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot fingerprint evaluation snapshot", ex);
        }
    }

    private double ratioPassed(List<EvalOutcome> outcomes) {
        if (outcomes.isEmpty()) return 1.0d;
        return (double) outcomes.stream().filter(EvalOutcome::passed).count() / outcomes.size();
    }

    private void requireAgent(String companyId, String agentId) {
        agentDefinitionRepository.findByCompanyIdAndAgentId(companyId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found"));
    }

    private AgentEvalSuiteEntity requireSuite(String companyId, String agentId, Long suiteId) {
        return suiteRepository.findById(suiteId)
                .filter(item -> isApplicable(companyId, agentId, item))
                .filter(item -> AgentEvalSuiteEntity.STATUS_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation suite not found"));
    }

    private AgentEvalSuiteEntity requireEditableTenantSuite(String companyId, String agentId, Long suiteId) {
        return suiteRepository.findByIdAndCompanyId(suiteId, companyId)
                .filter(item -> agentId.equals(item.getAgentId()))
                .filter(item -> AgentEvalSuiteEntity.SCOPE_TENANT_PRIVATE.equals(item.getScopeType()))
                .filter(item -> AgentEvalSuiteEntity.STATUS_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new IllegalArgumentException("Editable tenant evaluation suite not found"));
    }

    private List<AgentEvalSuiteEntity> applicableSuites(String companyId, String agentId) {
        LinkedHashMap<Long, AgentEvalSuiteEntity> suites = new LinkedHashMap<>();
        suiteRepository.findByCompanyIdAndAgentIdAndStatusOrderByIdAsc(
                        companyId, agentId, AgentEvalSuiteEntity.STATUS_ACTIVE)
                .forEach(item -> suites.put(item.getId(), item));
        suiteRepository.findByScopeTypeNotAndReleaseStatusAndStatusOrderByIdAsc(
                        AgentEvalSuiteEntity.SCOPE_TENANT_PRIVATE,
                        AgentEvalSuiteEntity.RELEASE_PUBLISHED,
                        AgentEvalSuiteEntity.STATUS_ACTIVE)
                .stream()
                .filter(item -> isApplicable(companyId, agentId, item))
                .forEach(item -> suites.put(item.getId(), item));
        return suites.values().stream()
                .sorted(Comparator.comparing(AgentEvalSuiteEntity::getId))
                .toList();
    }

    private boolean isApplicable(String companyId, String agentId, AgentEvalSuiteEntity suite) {
        if (suite == null || !AgentEvalSuiteEntity.STATUS_ACTIVE.equals(suite.getStatus())) return false;
        if (AgentEvalSuiteEntity.SCOPE_TENANT_PRIVATE.equals(suite.getScopeType())) {
            return companyId.equals(suite.getCompanyId()) && agentId.equals(suite.getAgentId());
        }
        if (!AgentEvalSuiteEntity.RELEASE_PUBLISHED.equals(suite.getReleaseStatus())) return false;
        if (AgentEvalSuiteEntity.SCOPE_PLATFORM_CORE.equals(suite.getScopeType()) && suite.isMandatory()) return true;
        if (AgentEvalSuiteEntity.SCOPE_APP_STANDARD.equals(suite.getScopeType())
                && (agentId.equals(suite.getAppCode()) || agentId.equals(suite.getAgentId()))) return true;
        return bindingRepository.findBySuiteIdOrderByIdAsc(suite.getId()).stream()
                .filter(AgentEvalSuiteBindingEntity::isEnabled)
                .anyMatch(binding -> (binding.getCompanyId() == null || companyId.equals(binding.getCompanyId()))
                        && (binding.getAgentId() == null || agentId.equals(binding.getAgentId())));
    }

    private Integer requireVersionNo(String companyId, String agentId, Integer versionNo) {
        if (versionNo == null) {
            throw new IllegalArgumentException("versionNo is required");
        }
        versionRepository.findByCompanyIdAndAgentIdAndVersionNo(companyId, agentId, versionNo)
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
        row.put("scopeType", suite.getScopeType());
        row.put("visibility", suite.getVisibility());
        row.put("releaseStatus", suite.getReleaseStatus());
        row.put("templateCode", nullToEmpty(suite.getTemplateCode()));
        row.put("suiteVersionNo", suite.getVersionNo());
        row.put("appCode", nullToEmpty(suite.getAppCode()));
        row.put("industryCode", nullToEmpty(suite.getIndustryCode()));
        row.put("hiddenResults", suite.isHiddenResults());
        row.put("mandatory", suite.isMandatory());
        row.put("platformOwned", suite.isPlatformOwned());
        row.put("caseCount", caseRepository.countBySuiteIdAndStatus(suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE));
        row.put("createdAt", suite.getCreatedAt().toString());
        row.put("updatedAt", suite.getUpdatedAt().toString());
        row.put("publishedAt", suite.getPublishedAt() == null ? "" : suite.getPublishedAt().toString());
        return row;
    }

    private Map<String, Object> casePayload(AgentEvalCaseEntity evalCase) {
        return casePayload(evalCase, false);
    }

    private Map<String, Object> casePayload(AgentEvalCaseEntity evalCase, boolean redacted) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", evalCase.getId());
        row.put("agentId", evalCase.getAgentId());
        row.put("suiteId", evalCase.getSuiteId());
        row.put("name", redacted ? "平台隐藏挑战用例" : evalCase.getName());
        row.put("inputText", redacted ? "" : evalCase.getInputText());
        row.put("assertionType", evalCase.getAssertionType());
        row.put("expectedText", redacted ? "" : nullToEmpty(evalCase.getExpectedText()));
        row.put("forbiddenText", redacted ? "" : nullToEmpty(evalCase.getForbiddenText()));
        row.put("expectedStatus", redacted ? "" : nullToEmpty(evalCase.getExpectedStatus()));
        row.put("requiredToolName", redacted ? "" : nullToEmpty(evalCase.getRequiredToolName()));
        row.put("forbiddenToolName", redacted ? "" : nullToEmpty(evalCase.getForbiddenToolName()));
        row.put("priority", evalCase.getPriority());
        row.put("status", evalCase.getStatus());
        row.put("caseKey", redacted ? "" : nullToEmpty(evalCase.getCaseKey()));
        row.put("category", evalCase.getCategory());
        row.put("conversationHistoryJson", redacted ? "" : nullToEmpty(evalCase.getConversationHistoryJson()));
        row.put("fixtureJson", redacted ? "" : nullToEmpty(evalCase.getFixtureJson()));
        row.put("assertionConfigJson", redacted ? "" : nullToEmpty(evalCase.getAssertionConfigJson()));
        row.put("judgeConfigJson", redacted ? "" : nullToEmpty(evalCase.getJudgeConfigJson()));
        row.put("tagsJson", redacted ? "" : nullToEmpty(evalCase.getTagsJson()));
        row.put("createdFromTraceId", redacted ? "" : nullToEmpty(evalCase.getCreatedFromTraceId()));
        row.put("hiddenCase", evalCase.isHiddenCase());
        row.put("redacted", redacted);
        row.put("reviewStatus", evalCase.getReviewStatus());
        row.put("redactionStatus", evalCase.getRedactionStatus());
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
        row.put("targetType", run.getTargetType());
        row.put("baselineVersionNo", run.getBaselineVersionNo());
        row.put("triggerType", run.getTriggerType());
        row.put("runtimeSnapshotJson", nullToEmpty(run.getRuntimeSnapshotJson()));
        row.put("snapshotFingerprint", nullToEmpty(run.getSnapshotFingerprint()));
        row.put("avgLatencyMs", run.getAvgLatencyMs());
        row.put("totalElapsedMs", run.getTotalElapsedMs());
        row.put("toolCallAccuracy", run.getToolCallAccuracy());
        row.put("ragHitRate", run.getRagHitRate());
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
        row.put("failureCategory", nullToEmpty(result.getFailureCategory()));
        row.put("failureSummary", nullToEmpty(result.getFailureSummary()));
        row.put("assertionResultsJson", nullToEmpty(result.getAssertionResultsJson()));
        row.put("traceId", nullToEmpty(result.getTraceId()));
        row.put("score", result.getScore());
        row.put("elapsedMs", result.getElapsedMs());
        row.put("toolCallCount", result.getToolCallCount());
        row.put("ragHitCount", result.getRagHitCount());
        row.put("createdAt", result.getCreatedAt().toString());
        return row;
    }

    private Map<String, Object> suiteGatePayload(AgentEvalSuiteEntity suite,
                                                 long caseCount,
                                                 AgentEvalRunEntity latest,
                                                 boolean missingRun,
                                                 boolean failedRun,
                                                 boolean staleRun) {
        HashMap<String, Object> row = new HashMap<>();
        row.put("id", suite.getId());
        row.put("name", suite.getName());
        row.put("gateMode", suite.getGateMode());
        row.put("minPassRate", suite.getMinPassRate());
        row.put("caseCount", caseCount);
        row.put("missingRun", missingRun);
        row.put("failedRun", failedRun);
        row.put("staleRun", staleRun);
        row.put("scopeType", suite.getScopeType());
        row.put("mandatory", suite.isMandatory());
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

    private String normalizeRunTargetType(String value) {
        String normalized = normalizeUpper(value, "CANDIDATE");
        if (!Set.of("CANDIDATE", "PUBLISHED", "COMPARE", "TRACE_REPLAY").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported evaluation targetType: " + value);
        }
        return normalized;
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

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON configuration");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        return truncate(message == null || message.isBlank() ? ex.getClass().getSimpleName() : message, 500);
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

    public record AdvancedSuiteCommand(
            String name,
            String description,
            String gateMode,
            Double minPassRate,
            String templateCode,
            String appCode,
            String industryCode
    ) {}

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

    public record AdvancedCaseCommand(
            String name,
            String inputText,
            String assertionType,
            String expectedText,
            String forbiddenText,
            String expectedStatus,
            String requiredToolName,
            String forbiddenToolName,
            String priority,
            String caseKey,
            String category,
            String conversationHistoryJson,
            String fixtureJson,
            String assertionConfigJson,
            String judgeConfigJson,
            String tagsJson,
            String createdFromTraceId,
            String reviewStatus,
            String redactionStatus
    ) {}

    public record RunCommand(
            Integer versionNo,
            String targetType,
            Integer baselineVersionNo,
            String triggerType,
            String createdBy
    ) {}

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
            double score,
            String failureCategory,
            String failureSummary,
            List<Map<String, Object>> assertionResults,
            long elapsedMs,
            int toolCallCount,
            int ragHitCount,
            String traceId,
            Map<String, Object> summary
    ) {
    }
}
