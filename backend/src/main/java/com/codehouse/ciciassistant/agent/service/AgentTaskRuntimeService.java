package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentTaskEventEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskEventRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskPlanEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskPlanRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P1 durable task-state foundation. This service stores and advances plan state only;
 * it does not select a mode, call a model, retrieve knowledge, or execute tools.
 */
@Service
public class AgentTaskRuntimeService {

    private static final Set<String> STEP_KINDS = Set.of(
            "RETRIEVE", "TOOL", "SYNTHESIZE", "VERIFY", "REQUEST_CONFIRMATION", "HANDOFF");
    private static final Set<String> TERMINAL_STEP_STATUSES = Set.of(
            AgentTaskStepEntity.STATUS_SUCCEEDED, AgentTaskStepEntity.STATUS_FAILED);

    private final AgentTaskRunRepository runRepository;
    private final AgentTaskPlanRepository planRepository;
    private final AgentTaskStepRepository stepRepository;
    private final AgentTaskEventRepository eventRepository;
    private final AgentTaskReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskRuntimeService(AgentTaskRunRepository runRepository,
                                   AgentTaskPlanRepository planRepository,
                                   AgentTaskStepRepository stepRepository,
                                   AgentTaskEventRepository eventRepository,
                                   AgentTaskReviewRepository reviewRepository,
                                   ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.eventRepository = eventRepository;
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RunView createRun(CreateRunCommand command) {
        String orgId = required(command.orgId(), "orgId");
        String agentId = required(command.agentId(), "agentId");
        String mode = required(command.mode(), "mode").toUpperCase(Locale.ROOT);
        if (!Set.of("DIRECT", "REACT", "PLAN_EXEC").contains(mode)) {
            throw new IllegalArgumentException("Unsupported task runtime mode");
        }
        int maxSteps = command.maxSteps();
        if (maxSteps < 1 || maxSteps > 12) {
            throw new IllegalArgumentException("maxSteps must be between 1 and 12");
        }
        Instant now = Instant.now();
        AgentTaskRunEntity run = runRepository.saveAndFlush(new AgentTaskRunEntity(
                orgId, emptyToNull(command.sessionId()), agentId, defaulted(command.channel(), "web"),
                mode, bounded(command.goalSummary(), 512, "goalSummary"), maxSteps, now));
        event(run, null, "RUN_CREATED", Map.of("mode", mode, "status", run.getStatus()), now);
        return toRunView(run);
    }

    @Transactional
    public PlanView attachInitialPlan(String orgId, long runId, String planJson) {
        AgentTaskRunEntity run = requireRun(orgId, runId);
        if (!AgentTaskRunEntity.STATUS_CREATED.equals(run.getStatus())) {
            throw new IllegalStateException("A plan can only be attached to a newly created run");
        }
        ValidatedPlan validated = validatePlan(planJson, run.getMaxSteps());
        Instant now = Instant.now();
        AgentTaskPlanEntity plan = planRepository.saveAndFlush(new AgentTaskPlanEntity(
                run.getOrgId(), run.getId(), 1, validated.goalSummary(), validated.canonicalJson(), sha256(validated.canonicalJson()), now));
        List<AgentTaskStepEntity> steps = new ArrayList<>();
        for (int index = 0; index < validated.steps().size(); index++) {
            PlanStep step = validated.steps().get(index);
            steps.add(new AgentTaskStepEntity(run.getOrgId(), run.getId(), plan.getId(), step.key(), index,
                    step.kind(), json(step.dependsOn()), json(step.allowedToolNames()), json(step.expectedEvidence()),
                    step.dependsOn().isEmpty(), now));
        }
        stepRepository.saveAllAndFlush(steps);
        run.attachPlan(plan.getId(), now);
        runRepository.saveAndFlush(run);
        event(run, null, "PLAN_VALIDATED", Map.of("planId", plan.getId(), "stepCount", steps.size()), now);
        for (AgentTaskStepEntity step : steps) {
            if (AgentTaskStepEntity.STATUS_READY.equals(step.getStatus())) {
                event(run, step, "STEP_READY", Map.of("stepKey", step.getStepKey()), now);
            }
        }
        return toPlanView(plan, steps);
    }

    @Transactional
    public Optional<ClaimedStep> claimNextReadyStep(String orgId, long runId, String leaseOwner, int leaseSeconds) {
        if (leaseSeconds < 1 || leaseSeconds > 300) {
            throw new IllegalArgumentException("leaseSeconds must be between 1 and 300");
        }
        AgentTaskRunEntity run = requireRun(orgId, runId);
        Instant now = Instant.now();
        if (!run.leaseAvailableTo(required(leaseOwner, "leaseOwner"), now)) {
            throw new IllegalStateException("Task run is leased by another executor");
        }
        if (!Set.of(AgentTaskRunEntity.STATUS_READY, AgentTaskRunEntity.STATUS_RUNNING).contains(run.getStatus())) {
            throw new IllegalStateException("Task run is not ready to execute");
        }
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(orgId, runId);
        AgentTaskStepEntity next = steps.stream()
                .filter(item -> AgentTaskStepEntity.STATUS_READY.equals(item.getStatus()))
                .findFirst().orElse(null);
        if (next == null) {
            finalizeRunWhenComplete(run, steps, now);
            return Optional.empty();
        }
        Instant expiresAt = now.plusSeconds(leaseSeconds);
        run.claimLease(leaseOwner, expiresAt, now);
        next.claim(leaseOwner, expiresAt, now);
        runRepository.saveAndFlush(run);
        stepRepository.saveAndFlush(next);
        event(run, next, "STEP_CLAIMED", Map.of("stepKey", next.getStepKey(), "attemptNo", next.getAttemptNo()), now);
        return Optional.of(toClaimedStep(next, run));
    }

    @Transactional
    public StepView completeStep(String orgId, long runId, long stepId, String leaseOwner,
                                 long expectedStepVersion, String resultSummary) {
        AgentTaskRunEntity run = requireRun(orgId, runId);
        AgentTaskStepEntity step = requireStep(orgId, runId, stepId);
        Instant now = Instant.now();
        requireClaim(run, step, leaseOwner, now);
        requireVersion(step.getVersion(), expectedStepVersion);
        step.succeed(bounded(resultSummary == null ? "" : resultSummary, 1024, "resultSummary"), now);
        stepRepository.saveAndFlush(step);
        event(run, step, "STEP_SUCCEEDED", Map.of("stepKey", step.getStepKey()), now);
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(orgId, runId);
        promoteSatisfiedSteps(run, steps, now);
        finalizeRunWhenComplete(run, steps, now);
        runRepository.saveAndFlush(run);
        return toStepView(step);
    }

    @Transactional
    public StepView failStep(String orgId, long runId, long stepId, String leaseOwner,
                             long expectedStepVersion, String errorCode) {
        AgentTaskRunEntity run = requireRun(orgId, runId);
        AgentTaskStepEntity step = requireStep(orgId, runId, stepId);
        Instant now = Instant.now();
        requireClaim(run, step, leaseOwner, now);
        requireVersion(step.getVersion(), expectedStepVersion);
        step.fail(bounded(required(errorCode, "errorCode"), 64, "errorCode"), now);
        stepRepository.saveAndFlush(step);
        run.fail(now);
        runRepository.saveAndFlush(run);
        event(run, step, "STEP_FAILED", Map.of("stepKey", step.getStepKey(), "errorCode", step.getErrorCode()), now);
        event(run, null, "RUN_FAILED", Map.of("stepKey", step.getStepKey()), now);
        return toStepView(step);
    }

    @Transactional
    public boolean recoverExpiredLease(String orgId, long runId, Instant now) {
        AgentTaskRunEntity run = requireRun(orgId, runId);
        if (run.getLeaseExpiresAt() == null || run.getLeaseExpiresAt().isAfter(now)) {
            return false;
        }
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(orgId, runId);
        boolean recovered = false;
        for (AgentTaskStepEntity step : steps) {
            if (AgentTaskStepEntity.STATUS_RUNNING.equals(step.getStatus())
                    && step.getLeaseExpiresAt() != null && !step.getLeaseExpiresAt().isAfter(now)) {
                step.recover(now);
                stepRepository.save(step);
                event(run, step, "STEP_LEASE_EXPIRED", Map.of("stepKey", step.getStepKey()), now);
                recovered = true;
            }
        }
        if (recovered) {
            run.releaseLease(now);
            runRepository.saveAndFlush(run);
            event(run, null, "RUN_RECOVERED", Map.of("runId", run.getId()), now);
        }
        return recovered;
    }

    @Transactional(readOnly = true)
    public RunSnapshot snapshot(String orgId, long runId) {
        AgentTaskRunEntity run = requireRun(orgId, runId);
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(orgId, runId);
        return new RunSnapshot(toRunView(run), steps.stream().map(this::toStepView).toList(),
                eventRepository.findByOrgIdAndRunIdOrderByOccurredAtAscIdAsc(orgId, runId).stream()
                        .map(item -> new EventView(item.getEventType(), item.getStepId(), item.getPayloadRedactedJson(), item.getOccurredAt())).toList());
    }

    /**
     * Returns only stable, administrator-safe execution facts for a Trace that already holds an exact run id.
     * Every repository access includes orgId, so a trace association never becomes a cross-tenant lookup key.
     */
    @Transactional(readOnly = true)
    public Optional<TraceExecutionView> traceExecution(String orgId, long runId) {
        AgentTaskRunEntity run = runRepository.findByIdAndOrgId(runId, required(orgId, "orgId")).orElse(null);
        if (run == null) return Optional.empty();
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(orgId, runId);
        int planRevision = planRepository.findTopByOrgIdAndRunIdOrderByRevisionNoDesc(orgId, runId)
                .map(AgentTaskPlanEntity::getRevisionNo).orElse(0);
        List<AgentTaskReviewEntity> reviews = reviewRepository.findByOrgIdAndRunIdOrderByReviewRoundAsc(orgId, runId);
        AgentTaskReviewEntity latestReview = reviews.isEmpty() ? null : reviews.get(reviews.size() - 1);
        List<TraceEventView> events = eventRepository.findByOrgIdAndRunIdOrderByOccurredAtAscIdAsc(orgId, runId).stream()
                .map(event -> new TraceEventView(event.getEventType(), event.getOccurredAt())).toList();
        String partialReason = steps.stream()
                .filter(step -> AgentTaskStepEntity.STATUS_FAILED.equals(step.getStatus()))
                .map(AgentTaskStepEntity::getErrorCode)
                .filter(code -> code != null && !code.isBlank())
                .findFirst().orElse("");
        return Optional.of(new TraceExecutionView(
                run.getId(), run.getMode(), run.getStatus(), planRevision,
                latestReview == null ? "NOT_REQUESTED" : latestReview.getReviewerStatus(),
                latestReview == null ? "SKIPPED" : latestReview.getGateStatus(),
                partialReason,
                steps.stream().map(step -> new TraceStepView(
                        step.getStepKey(), step.getStepKind(), step.getStatus(), step.getAttemptNo(),
                        step.getStartedAt(), step.getCompletedAt(),
                        safeEvidence(step.getResultSummary(), step.getErrorCode()))).toList(),
                events));
    }

    private void promoteSatisfiedSteps(AgentTaskRunEntity run, List<AgentTaskStepEntity> steps, Instant now) {
        Map<String, AgentTaskStepEntity> byKey = new HashMap<>();
        for (AgentTaskStepEntity item : steps) byKey.put(item.getStepKey(), item);
        for (AgentTaskStepEntity item : steps) {
            if (!AgentTaskStepEntity.STATUS_PENDING.equals(item.getStatus())) continue;
            List<String> dependencies = readStringArray(item.getDependsOnJson());
            if (dependencies.stream().allMatch(key -> AgentTaskStepEntity.STATUS_SUCCEEDED.equals(byKey.get(key).getStatus()))) {
                item.markReady(now);
                stepRepository.save(item);
                event(run, item, "STEP_READY", Map.of("stepKey", item.getStepKey()), now);
            }
        }
    }

    private void finalizeRunWhenComplete(AgentTaskRunEntity run, List<AgentTaskStepEntity> steps, Instant now) {
        if (steps.isEmpty()) return;
        if (steps.stream().anyMatch(item -> AgentTaskStepEntity.STATUS_FAILED.equals(item.getStatus()))) {
            run.fail(now);
            event(run, null, "RUN_FAILED", Map.of("reason", "step_failed"), now);
        } else if (steps.stream().allMatch(item -> AgentTaskStepEntity.STATUS_SUCCEEDED.equals(item.getStatus()))) {
            run.succeed(now);
            event(run, null, "RUN_SUCCEEDED", Map.of("stepCount", steps.size()), now);
        }
    }

    private ValidatedPlan validatePlan(String rawPlan, int maxSteps) {
        try {
            JsonNode root = objectMapper.readTree(required(rawPlan, "planJson"));
            if (root == null || !root.isObject()) throw new IllegalArgumentException("Plan must be a JSON object");
            String goal = bounded(root.path("goal").asText(), 512, "plan.goal");
            JsonNode rawSteps = root.path("steps");
            if (!rawSteps.isArray() || rawSteps.isEmpty() || rawSteps.size() > maxSteps) {
                throw new IllegalArgumentException("Plan steps must contain between 1 and maxSteps items");
            }
            List<PlanStep> steps = new ArrayList<>();
            Set<String> keys = new LinkedHashSet<>();
            for (JsonNode rawStep : rawSteps) {
                String key = required(rawStep.path("key").asText(), "step.key");
                if (!key.matches("[a-z0-9][a-z0-9-]{0,63}") || !keys.add(key)) {
                    throw new IllegalArgumentException("Plan step keys must be unique lowercase identifiers");
                }
                String kind = required(rawStep.path("kind").asText(), "step.kind").toUpperCase(Locale.ROOT);
                if (!STEP_KINDS.contains(kind)) throw new IllegalArgumentException("Unsupported plan step kind");
                steps.add(new PlanStep(key, kind, readArray(rawStep.path("dependsOn")),
                        readArray(rawStep.path("allowedToolNames")), readArray(rawStep.path("expectedEvidence"))));
            }
            Set<String> keySet = Set.copyOf(keys);
            for (PlanStep step : steps) {
                if (step.dependsOn().contains(step.key()) || !keySet.containsAll(step.dependsOn())) {
                    throw new IllegalArgumentException("Plan step dependencies must refer to other plan steps");
                }
            }
            assertAcyclic(steps);
            return new ValidatedPlan(goal, List.copyOf(steps), objectMapper.writeValueAsString(root));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Plan JSON is invalid", ex);
        }
    }

    private static void assertAcyclic(List<PlanStep> steps) {
        Map<String, PlanStep> byKey = new LinkedHashMap<>();
        for (PlanStep step : steps) byKey.put(step.key(), step);
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PlanStep step : steps) visit(step.key(), byKey, visiting, visited);
    }

    private static void visit(String key, Map<String, PlanStep> byKey, Set<String> visiting, Set<String> visited) {
        if (visited.contains(key)) return;
        if (!visiting.add(key)) throw new IllegalArgumentException("Plan dependencies must be acyclic");
        for (String dependency : byKey.get(key).dependsOn()) visit(dependency, byKey, visiting, visited);
        visiting.remove(key);
        visited.add(key);
    }

    private void requireClaim(AgentTaskRunEntity run, AgentTaskStepEntity step, String leaseOwner, Instant now) {
        String owner = required(leaseOwner, "leaseOwner");
        if (!AgentTaskStepEntity.STATUS_RUNNING.equals(step.getStatus()) || !owner.equals(step.getLeaseOwner())
                || step.getLeaseExpiresAt() == null || !step.getLeaseExpiresAt().isAfter(now)
                || !owner.equals(run.getLeaseOwner()) || run.getLeaseExpiresAt() == null || !run.getLeaseExpiresAt().isAfter(now)) {
            throw new IllegalStateException("Step lease is not active for this executor");
        }
    }

    private void requireVersion(Long actual, long expected) {
        if (actual == null || actual.longValue() != expected) throw new IllegalStateException("Step version is stale");
    }
    private AgentTaskRunEntity requireRun(String orgId, long runId) {
        return runRepository.findByIdAndOrgId(runId, required(orgId, "orgId"))
                .orElseThrow(() -> new IllegalArgumentException("Task run not found"));
    }
    private AgentTaskStepEntity requireStep(String orgId, long runId, long stepId) {
        return stepRepository.findByIdAndOrgIdAndRunId(stepId, orgId, runId)
                .orElseThrow(() -> new IllegalArgumentException("Task step not found"));
    }
    private void event(AgentTaskRunEntity run, AgentTaskStepEntity step, String type, Map<String, Object> payload, Instant now) {
        try { eventRepository.save(new AgentTaskEventEntity(run.getOrgId(), run.getId(), step == null ? null : step.getId(), type, json(payload), now)); }
        catch (RuntimeException ex) { throw ex; }
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException("Cannot serialize runtime event", ex); } }
    private List<String> readStringArray(String raw) { try { return readArray(objectMapper.readTree(raw)); } catch (Exception ex) { throw new IllegalStateException("Stored step dependencies are invalid", ex); } }
    private static List<String> readArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) throw new IllegalArgumentException("Plan step list fields must be arrays");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) { if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Plan step list values must be non-empty strings"); values.add(value.asText()); }
        return List.copyOf(values);
    }
    private static String required(String value, String field) { if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " is required"); return value.trim(); }
    private static String bounded(String value, int max, String field) { String safe = required(value, field); if (safe.length() > max) throw new IllegalArgumentException(field + " exceeds " + max + " characters"); return safe; }
    private static String defaulted(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String safeEvidence(String resultSummary, String errorCode) {
        String source = resultSummary == null || resultSummary.isBlank() ? errorCode : resultSummary;
        if (source == null || source.isBlank()) return "";
        String compact = source.replaceAll("\\s+", " ").trim();
        return compact.length() > 220 ? compact.substring(0, 219) + "…" : compact;
    }
    private static String sha256(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(64); for (byte item : bytes) out.append(String.format("%02x", item)); return out.toString(); } catch (Exception ex) { throw new IllegalStateException("SHA-256 unavailable", ex); } }

    private RunView toRunView(AgentTaskRunEntity item) { return new RunView(item.getId(), item.getOrgId(), item.getAgentId(), item.getMode(), item.getStatus(), item.getGoalSummary(), item.getCurrentPlanId(), item.getVersion()); }
    private PlanView toPlanView(AgentTaskPlanEntity plan, List<AgentTaskStepEntity> steps) { return new PlanView(plan.getId(), plan.getRevisionNo(), plan.getGoalSummary(), plan.getPlanHash(), steps.stream().map(this::toStepView).toList()); }
    private StepView toStepView(AgentTaskStepEntity item) { return new StepView(item.getId(), item.getStepKey(), item.getStepKind(), item.getStatus(), item.getAttemptNo(), item.getVersion(), item.getResultSummary(), item.getErrorCode()); }
    private ClaimedStep toClaimedStep(AgentTaskStepEntity step, AgentTaskRunEntity run) { return new ClaimedStep(toStepView(step), run.getLeaseExpiresAt()); }

    public record CreateRunCommand(String orgId, String sessionId, String agentId, String channel, String mode, String goalSummary, int maxSteps) { }
    public record RunView(Long id, String orgId, String agentId, String mode, String status, String goalSummary, Long currentPlanId, Long version) { }
    public record PlanView(Long id, int revisionNo, String goalSummary, String planHash, List<StepView> steps) { }
    public record StepView(Long id, String key, String kind, String status, int attemptNo, Long version, String resultSummary, String errorCode) { }
    public record ClaimedStep(StepView step, Instant leaseExpiresAt) { }
    public record EventView(String type, Long stepId, String payloadRedactedJson, Instant occurredAt) { }
    public record RunSnapshot(RunView run, List<StepView> steps, List<EventView> events) { }
    public record TraceExecutionView(Long runId, String mode, String terminalStatus, int planRevision,
                                     String reviewStatus, String reviewGateStatus, String partialReason,
                                     List<TraceStepView> steps, List<TraceEventView> events) { }
    public record TraceStepView(String key, String kind, String status, int attemptNo,
                                Instant startedAt, Instant completedAt, String evidenceSummary) { }
    public record TraceEventView(String type, Instant occurredAt) { }
    private record PlanStep(String key, String kind, List<String> dependsOn, List<String> allowedToolNames, List<String> expectedEvidence) { }
    private record ValidatedPlan(String goalSummary, List<PlanStep> steps, String canonicalJson) { }
}
