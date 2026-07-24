package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalCaseRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalIssueEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalIssueRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalPublishReferenceEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalPublishReferenceRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteEntity;
import com.codehouse.ciciassistant.agent.domain.AgentEvalSuiteRepository;
import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.platform.service.PlatformAuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentEvaluationControlPlaneService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?i)([a-z0-9._%+-])[a-z0-9._%+-]*(@[a-z0-9.-]+\\.[a-z]{2,})");
    private static final Pattern MOBILE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{3})\\d{11}(\\d{3}[\\dXx])(?!\\d)");
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+|(?:api[_-]?key|access[_-]?token|password)\\s*[:=]\\s*)\\S+");
    private static final Set<String> PLATFORM_SCOPES = Set.of(
            AgentEvalSuiteEntity.SCOPE_PLATFORM_CORE,
            AgentEvalSuiteEntity.SCOPE_APP_STANDARD,
            AgentEvalSuiteEntity.SCOPE_INDUSTRY_PACK);

    private final AgentDefinitionRepository agentRepository;
    private final AgentEvalSuiteRepository suiteRepository;
    private final AgentEvalSuiteBindingRepository bindingRepository;
    private final AgentEvalCaseRepository caseRepository;
    private final AgentEvalRunRepository runRepository;
    private final AgentEvalIssueRepository issueRepository;
    private final AgentEvalPublishReferenceRepository publishReferenceRepository;
    private final AgentEvaluationService evaluationService;
    private final AgentRunTraceService traceService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public AgentEvaluationControlPlaneService(AgentDefinitionRepository agentRepository,
                                              AgentEvalSuiteRepository suiteRepository,
                                              AgentEvalSuiteBindingRepository bindingRepository,
                                              AgentEvalCaseRepository caseRepository,
                                              AgentEvalRunRepository runRepository,
                                              AgentEvalIssueRepository issueRepository,
                                              AgentEvalPublishReferenceRepository publishReferenceRepository,
                                              AgentEvaluationService evaluationService,
                                              AgentRunTraceService traceService,
                                              PlatformAuditService platformAuditService,
                                              ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.suiteRepository = suiteRepository;
        this.bindingRepository = bindingRepository;
        this.caseRepository = caseRepository;
        this.runRepository = runRepository;
        this.issueRepository = issueRepository;
        this.publishReferenceRepository = publishReferenceRepository;
        this.evaluationService = evaluationService;
        this.traceService = traceService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tenantOverview(String companyId) {
        List<AgentDefinitionEntity> agents = agentRepository.findByCompanyIdAndEnabledTrueOrderByBuiltinDescUpdatedAtDesc(companyId);
        List<AgentEvalRunEntity> runs = runRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        Map<String, AgentEvalRunEntity> latestByAgent = new LinkedHashMap<>();
        runs.forEach(run -> latestByAgent.putIfAbsent(run.getAgentId(), run));
        long ready = latestByAgent.values().stream().filter(run -> AgentEvalRunEntity.STATUS_PASSED.equals(run.getStatus())).count();
        long blocked = latestByAgent.values().stream().filter(run -> AgentEvalRunEntity.STATUS_FAILED.equals(run.getStatus())).count();
        double averagePassRate = latestByAgent.isEmpty()
                ? 0.0d
                : latestByAgent.values().stream().mapToDouble(AgentEvalRunEntity::getPassRate).average().orElse(0.0d);
        List<Map<String, Object>> agentRows = agents.stream().map(agent -> {
            AgentEvalRunEntity run = latestByAgent.get(agent.getAgentId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agentId", agent.getAgentId());
            row.put("name", agent.getName());
            row.put("builtin", agent.isBuiltin());
            row.put("latestRun", run == null ? Map.of() : runPayload(run, false));
            row.put("qualityStatus", run == null ? "NOT_RUN" : run.getP0FailedCount() > 0 || run.getSafetyFailedCount() > 0
                    ? "BLOCKED" : AgentEvalRunEntity.STATUS_PASSED.equals(run.getStatus()) ? "READY" : "WARNING");
            return row;
        }).toList();
        return Map.of(
                "summary", Map.of(
                        "agentCount", agents.size(),
                        "readyCount", ready,
                        "blockedCount", blocked,
                        "notRunCount", Math.max(0, agents.size() - latestByAgent.size()),
                        "averagePassRate", averagePassRate,
                        "openIssueCount", issueRepository.countByCompanyIdAndStatus(companyId, "OPEN")),
                "agents", agentRows,
                "recentRuns", runs.stream().limit(20).map(run -> runPayload(run, false)).toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tenantSuites(String companyId, String agentId) {
        if (agentId != null && !agentId.isBlank()) return evaluationService.listSuites(companyId, agentId);
        return suiteRepository.findByCompanyIdAndStatusOrderByUpdatedAtDesc(
                        companyId, AgentEvalSuiteEntity.STATUS_ACTIVE).stream()
                .map(item -> suitePayload(item, true))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tenantRuns(String companyId, String agentId) {
        return runRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(run -> agentId == null || agentId.isBlank() || agentId.equals(run.getAgentId()))
                .limit(200)
                .map(run -> runPayload(run, false))
                .toList();
    }

    public Map<String, Object> runTenantSuite(String companyId,
                                              String agentId,
                                              Long suiteId,
                                              RunSuiteCommand command,
                                              String actorId) {
        requireAgent(companyId, agentId);
        return evaluationService.runSuite(companyId, agentId, suiteId, new AgentEvaluationService.RunCommand(
                command.versionNo(), command.targetType(), command.baselineVersionNo(),
                command.triggerType(), actorId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tenantCases(String companyId, String agentId, Long suiteId) {
        return evaluationService.listCasesForManagement(companyId, agentId, suiteId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> tenantRunDetail(String companyId, Long runId) {
        AgentEvalRunEntity run = runRepository.findById(runId)
                .filter(item -> companyId.equals(item.getCompanyId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run not found"));
        return Map.of(
                "run", runPayload(run, false),
                "results", evaluationService.listResults(companyId, run.getAgentId(), run.getId()));
    }

    @Transactional
    public Map<String, Object> createTenantSuite(String companyId,
                                                 String agentId,
                                                 TenantSuiteCommand command,
                                                 String actorId) {
        return evaluationService.createTenantSuite(companyId, agentId,
                new AgentEvaluationService.AdvancedSuiteCommand(
                        command.name(), command.description(), command.gateMode(), command.minPassRate(),
                        command.templateCode(), command.appCode(), command.industryCode()), actorId);
    }

    @Transactional
    public Map<String, Object> addTenantCase(String companyId,
                                            String agentId,
                                            Long suiteId,
                                            CaseMutationCommand command) {
        return evaluationService.addAdvancedCase(companyId, agentId, suiteId, advancedCase(command, null));
    }

    @Transactional
    public Map<String, Object> updateTenantCase(String companyId,
                                               String agentId,
                                               Long suiteId,
                                               Long caseId,
                                               CaseMutationCommand command) {
        return evaluationService.updateAdvancedCase(
                companyId, agentId, suiteId, caseId, advancedCase(command, null));
    }

    @Transactional
    public Map<String, Object> createCaseFromTrace(String companyId,
                                                  String actorId,
                                                  TraceCaseCommand command) {
        Map<String, Object> trace = traceService.orgTraceDetail(companyId, requireText(command.traceId(), "traceId is required"));
        String traceAgentId = string(trace.get("agentId"));
        String agentId = blankToDefault(command.agentId(), traceAgentId);
        if (!agentRepository.existsByCompanyIdAndAgentId(companyId, agentId)) {
            throw new IllegalArgumentException("Trace agent not found in current company");
        }
        Map<String, Object> detail = map(trace.get("detail"));
        Map<String, Object> request = map(detail.get("request"));
        String input = firstNonBlank(
                string(request.get("question")),
                string(request.get("inputSummary")),
                string(trace.get("summary")));
        String redactedInput = redact(input);
        CaseMutationCommand mutation = new CaseMutationCommand(
                blankToDefault(command.name(), "来自生产 Trace 的回归用例"),
                redactedInput,
                "STATUS_EQUALS",
                "", "", "published-executed", "", "",
                blankToDefault(command.priority(), "P1"),
                "trace-" + command.traceId(),
                blankToDefault(command.category(), "ANSWER_QUALITY"),
                "[]",
                json(Map.of("source", "production-trace", "traceId", command.traceId())),
                json(Map.of("assertions", List.of(Map.of("type", "STATUS_EQUALS", "expected", "published-executed")))),
                null,
                json(List.of("trace-regression")),
                "PENDING",
                "REDACTED",
                false);
        Map<String, Object> created = evaluationService.addAdvancedCase(
                companyId, agentId, command.suiteId(), advancedCase(mutation, command.traceId()));
        created.put("sourceTraceId", command.traceId());
        created.put("reviewRequired", true);
        created.put("createdBy", actorId);
        return created;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listIssues(String companyId) {
        return issueRepository.findByCompanyIdOrderByUpdatedAtDesc(companyId).stream().map(this::issuePayload).toList();
    }

    @Transactional
    public Map<String, Object> createIssue(String companyId, IssueCommand command, String actorId) {
        requireAgent(companyId, command.agentId());
        validateIssueReferences(companyId, command.agentId(), command.runId(), command.caseId());
        AgentEvalIssueEntity issue = issueRepository.save(new AgentEvalIssueEntity(
                companyId,
                command.agentId(),
                command.runId(),
                command.caseId(),
                requireText(command.title(), "Issue title is required"),
                normalizeRootCause(command.rootCauseType()),
                normalizeSeverity(command.severity()),
                trimToNull(command.description()),
                actorId));
        return issuePayload(issue);
    }

    @Transactional
    public Map<String, Object> updateIssue(String companyId, Long issueId, IssueUpdateCommand command) {
        AgentEvalIssueEntity issue = issueRepository.findById(issueId)
                .filter(item -> companyId.equals(item.getCompanyId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation issue not found"));
        if (command.verificationRunId() != null) {
            requireRun(companyId, issue.getAgentId(), command.verificationRunId());
        }
        issue.update(
                normalizeIssueStatus(command.status()),
                normalizeRootCause(command.rootCauseType()),
                normalizeSeverity(command.severity()),
                trimToNull(command.ownerUserId()),
                command.fixVersionNo(),
                command.verificationRunId(),
                trimToNull(command.description()),
                trimToNull(command.resolution()));
        return issuePayload(issueRepository.save(issue));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> platformOverview() {
        List<AgentEvalSuiteEntity> suites = suiteRepository.findByCompanyIdOrderByUpdatedAtDesc(
                AgentEvalSuiteEntity.PLATFORM_ORG_ID);
        List<AgentEvalRunEntity> runs = runRepository.findTop200ByOrderByCreatedAtDesc();
        return Map.of(
                "summary", Map.of(
                        "suiteCount", suites.size(),
                        "publishedSuiteCount", suites.stream().filter(item -> AgentEvalSuiteEntity.RELEASE_PUBLISHED.equals(item.getReleaseStatus())).count(),
                        "draftSuiteCount", suites.stream().filter(item -> AgentEvalSuiteEntity.RELEASE_DRAFT.equals(item.getReleaseStatus())).count(),
                        "hiddenCaseCount", suites.stream().mapToLong(item -> caseRepository.findBySuiteIdOrderByIdAsc(item.getId()).stream().filter(AgentEvalCaseEntity::isHiddenCase).count()).sum(),
                        "recentRunCount", runs.size(),
                        "recentP0FailureCount", runs.stream().mapToInt(AgentEvalRunEntity::getP0FailedCount).sum(),
                        "recentSafetyFailureCount", runs.stream().mapToInt(AgentEvalRunEntity::getSafetyFailedCount).sum()),
                "suites", suites.stream().map(item -> suitePayload(item, false)).toList(),
                "recentRuns", runs.stream().limit(30).map(run -> runPayload(run, true)).toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> platformSuites() {
        return suiteRepository.findByCompanyIdOrderByUpdatedAtDesc(AgentEvalSuiteEntity.PLATFORM_ORG_ID).stream()
                .map(item -> suitePayload(item, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> platformRuns() {
        return runRepository.findTop200ByOrderByCreatedAtDesc().stream()
                .map(run -> runPayload(run, true))
                .toList();
    }

    @Transactional
    public Map<String, Object> createPlatformSuite(PlatformSuiteCommand command,
                                                   String actorId,
                                                   String roleCode) {
        String scopeType = normalizePlatformScope(command.scopeType());
        String templateCode = requireText(command.templateCode(), "templateCode is required");
        int versionNo = suiteRepository.findByTemplateCodeOrderByVersionNoDesc(templateCode).stream()
                .map(AgentEvalSuiteEntity::getVersionNo)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
        AgentEvalSuiteEntity suite = suiteRepository.save(new AgentEvalSuiteEntity(
                AgentEvalSuiteEntity.PLATFORM_ORG_ID,
                blankToDefault(command.agentId(), "*"),
                requireText(command.name(), "Evaluation suite name is required"),
                trimToNull(command.description()),
                normalizeGateMode(command.gateMode()),
                normalizePassRate(command.minPassRate()),
                scopeType,
                normalizeVisibility(command.visibility()),
                AgentEvalSuiteEntity.RELEASE_DRAFT,
                templateCode,
                versionNo,
                trimToNull(command.appCode()),
                trimToNull(command.industryCode()),
                command.hiddenResults(),
                command.mandatory(),
                actorId));
        audit(actorId, roleCode, "platform.evaluation.suite.create", suite, "scope=" + scopeType + ",version=" + versionNo);
        return suitePayload(suite, false);
    }

    @Transactional
    public Map<String, Object> updatePlatformSuite(Long suiteId,
                                                   PlatformSuiteCommand command,
                                                   String actorId,
                                                   String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformSuite(suiteId);
        suite.updateDraft(
                requireText(command.name(), "Evaluation suite name is required"),
                trimToNull(command.description()),
                normalizeGateMode(command.gateMode()),
                normalizePassRate(command.minPassRate()),
                normalizeVisibility(command.visibility()),
                trimToNull(command.appCode()),
                trimToNull(command.industryCode()),
                command.hiddenResults(),
                command.mandatory());
        audit(actorId, roleCode, "platform.evaluation.suite.update", suite, "draft updated");
        return suitePayload(suiteRepository.save(suite), false);
    }

    @Transactional
    public Map<String, Object> publishPlatformSuite(Long suiteId, String actorId, String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformSuite(suiteId);
        long activeCases = caseRepository.countBySuiteIdAndStatus(suiteId, AgentEvalCaseEntity.STATUS_ACTIVE);
        if (activeCases == 0) throw new IllegalStateException("Evaluation suite must contain at least one active case");
        suite.publish();
        audit(actorId, roleCode, "platform.evaluation.suite.publish", suite, "activeCases=" + activeCases);
        return suitePayload(suiteRepository.save(suite), false);
    }

    @Transactional
    public Map<String, Object> archivePlatformSuite(Long suiteId, String actorId, String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformSuite(suiteId);
        suite.archive();
        audit(actorId, roleCode, "platform.evaluation.suite.archive", suite, "archived");
        return suitePayload(suiteRepository.save(suite), false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> platformCases(Long suiteId) {
        requirePlatformSuite(suiteId);
        return caseRepository.findBySuiteIdOrderByIdAsc(suiteId).stream().map(item -> casePayload(item, false)).toList();
    }

    @Transactional
    public Map<String, Object> addPlatformCase(Long suiteId,
                                              CaseMutationCommand command,
                                              String actorId,
                                              String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformDraftSuite(suiteId);
        AgentEvalCaseEntity evalCase = newCase(
                AgentEvalSuiteEntity.PLATFORM_ORG_ID, suite.getAgentId(), suiteId, command, null, command.hiddenCase());
        AgentEvalCaseEntity saved = caseRepository.save(evalCase);
        audit(actorId, roleCode, "platform.evaluation.case.create", suite, "caseId=" + saved.getId());
        return casePayload(saved, false);
    }

    @Transactional
    public Map<String, Object> updatePlatformCase(Long suiteId,
                                                 Long caseId,
                                                 CaseMutationCommand command,
                                                 String actorId,
                                                 String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformDraftSuite(suiteId);
        AgentEvalCaseEntity evalCase = caseRepository.findById(caseId)
                .filter(item -> suiteId.equals(item.getSuiteId()))
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
                normalizeCategory(command.category()),
                normalizeJson(command.conversationHistoryJson()),
                normalizeJson(command.fixtureJson()),
                normalizeJson(command.assertionConfigJson()),
                normalizeJson(command.judgeConfigJson()),
                normalizeJson(command.tagsJson()),
                command.hiddenCase(),
                normalizeReviewStatus(command.reviewStatus()),
                normalizeRedactionStatus(command.redactionStatus()));
        AgentEvalCaseEntity saved = caseRepository.save(evalCase);
        audit(actorId, roleCode, "platform.evaluation.case.update", suite, "caseId=" + saved.getId());
        return casePayload(saved, false);
    }

    @Transactional
    public Map<String, Object> bindPlatformSuite(Long suiteId,
                                                BindingCommand command,
                                                String actorId,
                                                String roleCode) {
        AgentEvalSuiteEntity suite = requirePlatformSuite(suiteId);
        AgentEvalSuiteBindingEntity binding = bindingRepository.save(new AgentEvalSuiteBindingEntity(
                suiteId,
                trimToNull(command.companyId()),
                trimToNull(command.agentId()),
                trimToNull(command.appCode()),
                trimToNull(command.industryCode()),
                actorId));
        audit(actorId, roleCode, "platform.evaluation.suite.bind", suite, "bindingId=" + binding.getId());
        return bindingPayload(binding);
    }

    @Transactional
    public void recordPublishReference(String companyId, String agentId, Integer versionNo, String actorId) {
        List<AgentEvalRunEntity> latest = runRepository.findByCompanyIdAndAgentIdAndVersionNoOrderByCreatedAtDesc(
                companyId, agentId, versionNo);
        LinkedHashMap<Long, AgentEvalRunEntity> bySuite = new LinkedHashMap<>();
        latest.forEach(run -> bySuite.putIfAbsent(run.getSuiteId(), run));
        List<Long> runIds = bySuite.values().stream().map(AgentEvalRunEntity::getId).toList();
        String fingerprint = bySuite.values().stream()
                .map(AgentEvalRunEntity::getSnapshotFingerprint)
                .filter(Objects::nonNull)
                .sorted()
                .reduce((left, right) -> left + ":" + right)
                .orElse("");
        publishReferenceRepository.save(new AgentEvalPublishReferenceEntity(
                companyId, agentId, versionNo, json(runIds), fingerprint, actorId));
    }

    private AgentEvaluationService.AdvancedCaseCommand advancedCase(CaseMutationCommand command, String traceId) {
        return new AgentEvaluationService.AdvancedCaseCommand(
                command.name(), command.inputText(), command.assertionType(), command.expectedText(),
                command.forbiddenText(), command.expectedStatus(), command.requiredToolName(),
                command.forbiddenToolName(), command.priority(), command.caseKey(), command.category(),
                command.conversationHistoryJson(), command.fixtureJson(), command.assertionConfigJson(),
                command.judgeConfigJson(), command.tagsJson(), traceId, command.reviewStatus(),
                command.redactionStatus());
    }

    private AgentEvalCaseEntity newCase(String companyId,
                                        String agentId,
                                        Long suiteId,
                                        CaseMutationCommand command,
                                        String traceId,
                                        boolean hiddenCase) {
        return new AgentEvalCaseEntity(
                companyId, agentId, suiteId,
                requireText(command.name(), "Evaluation case name is required"),
                requireText(command.inputText(), "Evaluation case input is required"),
                normalizeAssertionType(command.assertionType()),
                trimToNull(command.expectedText()), trimToNull(command.forbiddenText()),
                trimToNull(command.expectedStatus()), trimToNull(command.requiredToolName()),
                trimToNull(command.forbiddenToolName()), normalizePriority(command.priority()),
                trimToNull(command.caseKey()), normalizeCategory(command.category()),
                normalizeJson(command.conversationHistoryJson()), normalizeJson(command.fixtureJson()),
                normalizeJson(command.assertionConfigJson()), normalizeJson(command.judgeConfigJson()),
                normalizeJson(command.tagsJson()), trimToNull(traceId), hiddenCase,
                normalizeReviewStatus(command.reviewStatus()), normalizeRedactionStatus(command.redactionStatus()));
    }

    private AgentEvalSuiteEntity requirePlatformSuite(Long suiteId) {
        return suiteRepository.findByIdAndCompanyId(suiteId, AgentEvalSuiteEntity.PLATFORM_ORG_ID)
                .orElseThrow(() -> new IllegalArgumentException("Platform evaluation suite not found"));
    }

    private AgentEvalSuiteEntity requirePlatformDraftSuite(Long suiteId) {
        AgentEvalSuiteEntity suite = requirePlatformSuite(suiteId);
        if (!AgentEvalSuiteEntity.RELEASE_DRAFT.equals(suite.getReleaseStatus())) {
            throw new IllegalStateException("Published platform evaluation suites are immutable");
        }
        return suite;
    }

    private void requireAgent(String companyId, String agentId) {
        if (!agentRepository.existsByCompanyIdAndAgentId(companyId, agentId)) {
            throw new IllegalArgumentException("Agent not found");
        }
    }

    private void validateIssueReferences(String companyId, String agentId, Long runId, Long caseId) {
        AgentEvalRunEntity run = runId == null ? null : requireRun(companyId, agentId, runId);
        if (caseId == null) return;
        AgentEvalCaseEntity evalCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation case not found"));
        boolean valid = run == null
                ? companyId.equals(evalCase.getCompanyId()) && agentId.equals(evalCase.getAgentId())
                : Objects.equals(run.getSuiteId(), evalCase.getSuiteId());
        if (!valid) throw new IllegalArgumentException("Evaluation case does not belong to the referenced run");
    }

    private AgentEvalRunEntity requireRun(String companyId, String agentId, Long runId) {
        return runRepository.findById(runId)
                .filter(item -> companyId.equals(item.getCompanyId()) && agentId.equals(item.getAgentId()))
                .orElseThrow(() -> new IllegalArgumentException("Evaluation run not found"));
    }

    private Map<String, Object> suitePayload(AgentEvalSuiteEntity suite, boolean tenantView) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", suite.getId());
        row.put("agentId", suite.getAgentId());
        row.put("name", suite.getName());
        row.put("description", blankToDefault(suite.getDescription(), ""));
        row.put("status", suite.getStatus());
        row.put("gateMode", suite.getGateMode());
        row.put("minPassRate", suite.getMinPassRate());
        row.put("scopeType", suite.getScopeType());
        row.put("visibility", suite.getVisibility());
        row.put("releaseStatus", suite.getReleaseStatus());
        row.put("templateCode", blankToDefault(suite.getTemplateCode(), ""));
        row.put("versionNo", suite.getVersionNo());
        row.put("appCode", blankToDefault(suite.getAppCode(), ""));
        row.put("industryCode", blankToDefault(suite.getIndustryCode(), ""));
        row.put("hiddenResults", suite.isHiddenResults());
        row.put("mandatory", suite.isMandatory());
        row.put("caseCount", caseRepository.countBySuiteIdAndStatus(suite.getId(), AgentEvalCaseEntity.STATUS_ACTIVE));
        row.put("createdAt", suite.getCreatedAt().toString());
        row.put("updatedAt", suite.getUpdatedAt().toString());
        row.put("publishedAt", suite.getPublishedAt() == null ? "" : suite.getPublishedAt().toString());
        if (tenantView && AgentEvalSuiteEntity.VISIBILITY_SEALED.equals(suite.getVisibility())) {
            row.put("description", "平台强制质量基线");
            row.put("templateCode", "");
        }
        return row;
    }

    private Map<String, Object> casePayload(AgentEvalCaseEntity evalCase, boolean redacted) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", evalCase.getId());
        row.put("suiteId", evalCase.getSuiteId());
        row.put("name", redacted ? "平台隐藏挑战用例" : evalCase.getName());
        row.put("inputText", redacted ? "" : evalCase.getInputText());
        row.put("caseKey", redacted ? "" : blankToDefault(evalCase.getCaseKey(), ""));
        row.put("category", evalCase.getCategory());
        row.put("priority", evalCase.getPriority());
        row.put("assertionType", evalCase.getAssertionType());
        row.put("assertionConfigJson", redacted ? "" : blankToDefault(evalCase.getAssertionConfigJson(), ""));
        row.put("expectedText", redacted ? "" : blankToDefault(evalCase.getExpectedText(), ""));
        row.put("status", evalCase.getStatus());
        row.put("hiddenCase", evalCase.isHiddenCase());
        row.put("reviewStatus", evalCase.getReviewStatus());
        row.put("redactionStatus", evalCase.getRedactionStatus());
        row.put("createdFromTraceId", redacted ? "" : blankToDefault(evalCase.getCreatedFromTraceId(), ""));
        row.put("updatedAt", evalCase.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> runPayload(AgentEvalRunEntity run, boolean platformView) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", run.getId());
        row.put("companyId", platformView ? run.getCompanyId() : "");
        row.put("agentId", run.getAgentId());
        row.put("suiteId", run.getSuiteId());
        row.put("versionNo", run.getVersionNo());
        row.put("baselineVersionNo", run.getBaselineVersionNo());
        row.put("status", run.getStatus());
        row.put("caseCount", run.getCaseCount());
        row.put("passedCount", run.getPassedCount());
        row.put("failedCount", run.getFailedCount());
        row.put("p0FailedCount", run.getP0FailedCount());
        row.put("safetyFailedCount", run.getSafetyFailedCount());
        row.put("passRate", run.getPassRate());
        row.put("avgLatencyMs", run.getAvgLatencyMs());
        row.put("totalElapsedMs", run.getTotalElapsedMs());
        row.put("toolCallAccuracy", run.getToolCallAccuracy());
        row.put("ragHitRate", run.getRagHitRate());
        row.put("targetType", run.getTargetType());
        row.put("triggerType", run.getTriggerType());
        row.put("startedAt", run.getStartedAt().toString());
        row.put("finishedAt", run.getFinishedAt() == null ? "" : run.getFinishedAt().toString());
        return row;
    }

    private Map<String, Object> issuePayload(AgentEvalIssueEntity issue) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", issue.getId());
        row.put("agentId", issue.getAgentId());
        row.put("runId", issue.getRunId());
        row.put("caseId", issue.getCaseId());
        row.put("title", issue.getTitle());
        row.put("status", issue.getStatus());
        row.put("rootCauseType", issue.getRootCauseType());
        row.put("severity", issue.getSeverity());
        row.put("ownerUserId", blankToDefault(issue.getOwnerUserId(), ""));
        row.put("fixVersionNo", issue.getFixVersionNo());
        row.put("verificationRunId", issue.getVerificationRunId());
        row.put("description", blankToDefault(issue.getDescription(), ""));
        row.put("resolution", blankToDefault(issue.getResolution(), ""));
        row.put("createdAt", issue.getCreatedAt().toString());
        row.put("updatedAt", issue.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> bindingPayload(AgentEvalSuiteBindingEntity binding) {
        return Map.of(
                "id", binding.getId(),
                "suiteId", binding.getSuiteId(),
                "companyId", blankToDefault(binding.getCompanyId(), ""),
                "agentId", blankToDefault(binding.getAgentId(), ""),
                "appCode", blankToDefault(binding.getAppCode(), ""),
                "industryCode", blankToDefault(binding.getIndustryCode(), ""),
                "enabled", binding.isEnabled(),
                "createdAt", binding.getCreatedAt().toString());
    }

    private void audit(String actorId,
                       String roleCode,
                       String eventType,
                       AgentEvalSuiteEntity suite,
                       String detail) {
        platformAuditService.log(
                AgentEvalSuiteEntity.PLATFORM_ORG_ID,
                actorId,
                roleCode,
                eventType,
                "AGENT_EVALUATION_SUITE",
                String.valueOf(suite.getId()),
                detail);
    }

    private String normalizePlatformScope(String value) {
        String normalized = normalizeUpper(value, AgentEvalSuiteEntity.SCOPE_APP_STANDARD);
        if (!PLATFORM_SCOPES.contains(normalized)) throw new IllegalArgumentException("Invalid platform suite scope");
        return normalized;
    }

    private String normalizeVisibility(String value) {
        String normalized = normalizeUpper(value, AgentEvalSuiteEntity.VISIBILITY_AUTHORIZED);
        if (!Set.of(AgentEvalSuiteEntity.VISIBILITY_SEALED,
                AgentEvalSuiteEntity.VISIBILITY_AUTHORIZED).contains(normalized)) {
            throw new IllegalArgumentException("Invalid platform suite visibility");
        }
        return normalized;
    }

    private String normalizeGateMode(String value) {
        return "WARN_ONLY".equals(normalizeUpper(value, "BLOCKING")) ? "WARN_ONLY" : "BLOCKING";
    }

    private double normalizePassRate(Double value) {
        return value == null ? 1.0d : Math.max(0.0d, Math.min(1.0d, value));
    }

    private String normalizeAssertionType(String value) {
        return normalizeUpper(value, "STATUS_EQUALS");
    }

    private String normalizePriority(String value) {
        String normalized = normalizeUpper(value, "P1");
        return Set.of("P0", "P1", "P2", "SAFETY").contains(normalized) ? normalized : "P1";
    }

    private String normalizeCategory(String value) {
        return normalizeUpper(value, "ANSWER_QUALITY");
    }

    private String normalizeReviewStatus(String value) {
        String normalized = normalizeUpper(value, "APPROVED");
        return Set.of("PENDING", "APPROVED", "REJECTED").contains(normalized) ? normalized : "PENDING";
    }

    private String normalizeRedactionStatus(String value) {
        String normalized = normalizeUpper(value, "NOT_REQUIRED");
        return Set.of("NOT_REQUIRED", "REDACTED", "REVIEW_REQUIRED").contains(normalized) ? normalized : "REVIEW_REQUIRED";
    }

    private String normalizeRootCause(String value) {
        return normalizeUpper(value, "UNCLASSIFIED");
    }

    private String normalizeSeverity(String value) {
        String normalized = normalizeUpper(value, "P1");
        return Set.of("P0", "P1", "P2").contains(normalized) ? normalized : "P1";
    }

    private String normalizeIssueStatus(String value) {
        String normalized = normalizeUpper(value, "OPEN");
        return Set.of("OPEN", "IN_PROGRESS", "VERIFYING", "RESOLVED", "CLOSED").contains(normalized)
                ? normalized : "OPEN";
    }

    private String normalizeUpper(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(value));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid JSON configuration");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private String redact(String value) {
        if (value == null || value.isBlank()) return "";
        String redacted = EMAIL_PATTERN.matcher(value).replaceAll("$1***$2");
        redacted = ID_CARD_PATTERN.matcher(redacted).replaceAll("$1***********$2");
        redacted = MOBILE_PATTERN.matcher(redacted).replaceAll("$1****$2");
        return SECRET_PATTERN.matcher(redacted).replaceAll("$1[redacted]");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "";
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record TenantSuiteCommand(String name, String description, String gateMode, Double minPassRate,
                                     String templateCode, String appCode, String industryCode) {}

    public record PlatformSuiteCommand(String name, String description, String gateMode, Double minPassRate,
                                       String scopeType, String visibility, String templateCode, String agentId,
                                       String appCode, String industryCode, boolean hiddenResults, boolean mandatory) {}

    public record CaseMutationCommand(String name, String inputText, String assertionType, String expectedText,
                                      String forbiddenText, String expectedStatus, String requiredToolName,
                                      String forbiddenToolName, String priority, String caseKey, String category,
                                      String conversationHistoryJson, String fixtureJson, String assertionConfigJson,
                                      String judgeConfigJson, String tagsJson, String reviewStatus,
                                      String redactionStatus, boolean hiddenCase) {}

    public record TraceCaseCommand(Long suiteId, String traceId, String agentId, String name,
                                   String priority, String category) {}

    public record IssueCommand(String agentId, Long runId, Long caseId, String title,
                               String rootCauseType, String severity, String description) {}

    public record IssueUpdateCommand(String status, String rootCauseType, String severity, String ownerUserId,
                                     Integer fixVersionNo, Long verificationRunId, String description,
                                     String resolution) {}

    public record BindingCommand(String companyId, String agentId, String appCode, String industryCode) {}

    public record RunSuiteCommand(Integer versionNo, String targetType, Integer baselineVersionNo,
                                  String triggerType) {}
}
