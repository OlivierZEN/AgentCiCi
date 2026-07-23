package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.config.AgentRuntimeReflectProperties;
import com.codehouse.ciciassistant.agent.domain.AgentTaskEventEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskEventRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskReviewRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskRunRepository;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepEntity;
import com.codehouse.ciciassistant.agent.domain.AgentTaskStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** P4 deterministic gate. It deliberately has no model, tool, credential, or write-operation dependency. */
@Service
public class AgentTaskReflectService {
    private final AgentRuntimeReflectProperties properties;
    private final AgentTaskRunRepository runRepository;
    private final AgentTaskStepRepository stepRepository;
    private final AgentTaskReviewRepository reviewRepository;
    private final AgentTaskEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskReflectService(AgentRuntimeReflectProperties properties, AgentTaskRunRepository runRepository,
                                   AgentTaskStepRepository stepRepository, AgentTaskReviewRepository reviewRepository,
                                   AgentTaskEventRepository eventRepository, ObjectMapper objectMapper) {
        this.properties = properties; this.runRepository = runRepository; this.stepRepository = stepRepository;
        this.reviewRepository = reviewRepository; this.eventRepository = eventRepository; this.objectMapper = objectMapper;
    }

    @Transactional
    public ReflectResult reflect(ReflectCommand command) {
        if (command == null || !command.reflectRequired() || !properties.isEnabledFor(command == null ? "" : command.agentId())) {
            return ReflectResult.skipped();
        }
        AgentTaskRunEntity run = runRepository.findByIdAndOrgId(command.runId(), command.orgId())
                .orElseThrow(() -> new IllegalArgumentException("Task run not found"));
        List<AgentTaskStepEntity> steps = stepRepository.findByOrgIdAndRunIdOrderByStepOrderAsc(command.orgId(), command.runId());
        List<AgentTaskReviewEntity> previousReviews = reviewRepository.findByOrgIdAndRunIdOrderByReviewRoundAsc(
                command.orgId(), command.runId());
        List<String> issues = gateIssues(run, steps, command, previousReviews.size());
        String gateStatus = issues.isEmpty() ? "PASS" : "BLOCKED";
        String reviewerStatus = issues.isEmpty() ? "PASS" : "HANDOFF";
        int round = previousReviews.size() + 1;
        Instant now = Instant.now();
        AgentTaskReviewEntity saved = reviewRepository.saveAndFlush(new AgentTaskReviewEntity(
                command.orgId(), command.runId(), round, gateStatus, reviewerStatus, json(issues),
                summarize(command.output(), reviewerStatus), now));
        eventRepository.save(new AgentTaskEventEntity(command.orgId(), command.runId(), null, "REFLECT_GATE", json(Map.of(
                "reviewId", saved.getId(), "gateStatus", gateStatus, "reviewerStatus", reviewerStatus,
                "issueCodes", issues)), now));
        return new ReflectResult(true, saved.getId(), gateStatus, reviewerStatus, List.copyOf(issues));
    }

    private List<String> gateIssues(AgentTaskRunEntity run, List<AgentTaskStepEntity> steps, ReflectCommand command,
                                    int previousReviewCount) {
        if (!run.getAgentId().equals(command.agentId())) return List.of("AGENT_MISMATCH");
        if (!"PLAN_EXEC".equals(run.getMode())) return List.of("UNSUPPORTED_MODE");
        if (!AgentTaskRunEntity.STATUS_SUCCEEDED.equals(run.getStatus())) return List.of("RUN_NOT_SUCCEEDED");
        if (steps.isEmpty() || steps.size() > run.getMaxSteps()) return List.of("STEP_BUDGET_VIOLATION");
        if (steps.stream().anyMatch(step -> !AgentTaskStepEntity.STATUS_SUCCEEDED.equals(step.getStatus()))) return List.of("STEP_NOT_SUCCEEDED");
        if (previousReviewCount >= properties.getMaxRounds()) return List.of("REFLECT_BUDGET_EXHAUSTED");
        if (command.requiresConfirmation()) return List.of("CONFIRMATION_REQUIRED");
        if (command.output() == null || command.output().isBlank()) return List.of("OUTPUT_EMPTY");
        return List.of();
    }

    private String summarize(String output, String reviewerStatus) {
        String normalized = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        return ("PASS".equals(reviewerStatus) ? "deterministic-review-pass: " : "deterministic-review-handoff: ")
                + normalized.substring(0, Math.min(normalized.length(), 900));
    }
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Cannot serialize reflect evidence", ex); }
    }
    public record ReflectCommand(String orgId, long runId, String agentId, boolean reflectRequired,
                                 boolean requiresConfirmation, String output) { }
    public record ReflectResult(boolean selected, Long reviewId, String gateStatus, String reviewerStatus,
                                List<String> issueCodes) {
        static ReflectResult skipped() { return new ReflectResult(false, null, "SKIPPED", "NOT_REQUESTED", List.of()); }
    }
}
