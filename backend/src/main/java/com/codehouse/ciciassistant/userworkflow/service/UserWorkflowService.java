package com.codehouse.ciciassistant.userworkflow.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.service.AgentDefinitionService;
import com.codehouse.ciciassistant.ai.service.ToolOrchestratorService;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import com.codehouse.ciciassistant.feishu.service.FeishuBotConfigService;
import com.codehouse.ciciassistant.feishu.service.FeishuBotMessenger;
import com.codehouse.ciciassistant.feishu.service.FeishuBotPairingService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.userworkflow.domain.UserAgentProfileEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserAgentProfileRepository;
import com.codehouse.ciciassistant.userworkflow.domain.UserQuickCommandEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserQuickCommandRepository;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowExecutionEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowExecutionRepository;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowSpecEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowSpecRepository;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowTriggerEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowTriggerRepository;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowVersionEntity;
import com.codehouse.ciciassistant.userworkflow.domain.UserWorkflowVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserWorkflowService {

    private static final List<String> EXTRA_CICI_TOOLS = List.of(
            "email_list_inbox",
            "email_search",
            "email_get_message",
            "get_pending_approvals");

    private static final Pattern INTERVAL_PATTERN = Pattern.compile("每\\s*(\\d{1,3})\\s*分钟");
    private static final Pattern CLOCK_PATTERN = Pattern.compile("(上午|下午|中午|晚上)?\\s*(\\d{1,2})(?:(?::|[点时])\\s*(\\d{1,2})?\\s*分?)?");
    private static final Pattern NUMBERING_PREFIX = Pattern.compile("^[0-9]+[.)、]\\s*");

    private final AgentDefinitionService agentDefinitionService;
    private final UserAgentProfileRepository userAgentProfileRepository;
    private final UserWorkflowSpecRepository userWorkflowSpecRepository;
    private final UserWorkflowVersionRepository userWorkflowVersionRepository;
    private final UserWorkflowTriggerRepository userWorkflowTriggerRepository;
    private final UserWorkflowExecutionRepository userWorkflowExecutionRepository;
    private final UserQuickCommandRepository userQuickCommandRepository;
    private final SpecCompilerService specCompilerService;
    private final ToolOrchestratorService toolOrchestratorService;
    private final FeishuBotConfigService feishuBotConfigService;
    private final FeishuBotPairingService feishuBotPairingService;
    private final FeishuBotMessenger feishuBotMessenger;
    private final BillingUsageMeteringService billingUsageMeteringService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public UserWorkflowService(AgentDefinitionService agentDefinitionService,
                               UserAgentProfileRepository userAgentProfileRepository,
                               UserWorkflowSpecRepository userWorkflowSpecRepository,
                               UserWorkflowVersionRepository userWorkflowVersionRepository,
                               UserWorkflowTriggerRepository userWorkflowTriggerRepository,
                               UserWorkflowExecutionRepository userWorkflowExecutionRepository,
                               UserQuickCommandRepository userQuickCommandRepository,
                               SpecCompilerService specCompilerService,
                               ToolOrchestratorService toolOrchestratorService,
                               FeishuBotConfigService feishuBotConfigService,
                               FeishuBotPairingService feishuBotPairingService,
                               FeishuBotMessenger feishuBotMessenger,
                               BillingUsageMeteringService billingUsageMeteringService,
                               AuditService auditService,
                               ObjectMapper objectMapper) {
        this.agentDefinitionService = agentDefinitionService;
        this.userAgentProfileRepository = userAgentProfileRepository;
        this.userWorkflowSpecRepository = userWorkflowSpecRepository;
        this.userWorkflowVersionRepository = userWorkflowVersionRepository;
        this.userWorkflowTriggerRepository = userWorkflowTriggerRepository;
        this.userWorkflowExecutionRepository = userWorkflowExecutionRepository;
        this.userQuickCommandRepository = userQuickCommandRepository;
        this.specCompilerService = specCompilerService;
        this.toolOrchestratorService = toolOrchestratorService;
        this.feishuBotConfigService = feishuBotConfigService;
        this.feishuBotPairingService = feishuBotPairingService;
        this.feishuBotMessenger = feishuBotMessenger;
        this.billingUsageMeteringService = billingUsageMeteringService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkflowBundle getBundle(String companyId, String userId, String requestedAgentId) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserAgentProfileEntity profile = getOrCreateProfile(companyId, userId, agent.agentId());
        UserWorkflowSpecEntity spec = getOrCreateSpec(companyId, userId, agent.agentId());
        List<UserWorkflowVersionEntity> versions = userWorkflowVersionRepository
                .findByCompanyIdAndUserIdAndAgentIdOrderByVersionNoDesc(companyId, userId, agent.agentId());
        List<UserWorkflowTriggerEntity> triggers = userWorkflowTriggerRepository
                .findByCompanyIdAndUserIdAndAgentIdOrderByIdAsc(companyId, userId, agent.agentId());
        List<UserWorkflowExecutionEntity> executions = userWorkflowExecutionRepository
                .findTop20ByCompanyIdAndUserIdAndAgentIdOrderByIdDesc(companyId, userId, agent.agentId());
        UserWorkflowVersionEntity latestDraft = spec.getDraftVersionNo() == null
                ? null
                : userWorkflowVersionRepository.findByCompanyIdAndUserIdAndAgentIdAndVersionNo(
                        companyId, userId, agent.agentId(), spec.getDraftVersionNo())
                .orElse(null);
        return new WorkflowBundle(agent, profile, spec, versions, triggers, executions, latestDraft);
    }

    @Transactional
    public UserAgentProfileEntity updateProfile(String companyId, String userId, String requestedAgentId, UpdateProfileCommand command) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserAgentProfileEntity profile = getOrCreateProfile(companyId, userId, agent.agentId());
        profile.update(
                safeTimezone(command.timezone()),
                safeText(command.locale()).isBlank() ? "zh-CN" : safeText(command.locale()),
                toJson(normalizeMap(command.notificationTarget())),
                toJson(normalizeMap(command.personalContext())),
                command.enabled() == null || command.enabled()
        );
        auditService.log(companyId, userId, "user.workflow.profile.update", "agent=" + agent.agentId());
        return profile;
    }

    @Transactional
    public UserWorkflowSpecEntity updateSpec(String companyId, String userId, String requestedAgentId, String sourceText) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserWorkflowSpecEntity spec = getOrCreateSpec(companyId, userId, agent.agentId());
        spec.updateSourceText(safeText(sourceText).trim());
        auditService.log(companyId, userId, "user.workflow.spec.update", "agent=" + agent.agentId());
        return spec;
    }

    @Transactional
    public CompileResult compile(String companyId, String userId, String requestedAgentId, CompileCommand command) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserWorkflowSpecEntity spec = getOrCreateSpec(companyId, userId, agent.agentId());
        UserAgentProfileEntity profile = getOrCreateProfile(companyId, userId, agent.agentId());
        String sourceText = safeText(command.sourceText()).trim();
        if (!sourceText.isBlank()) {
            spec.updateSourceText(sourceText);
        } else {
            sourceText = safeText(spec.getSourceText()).trim();
        }
        if (sourceText.isBlank()) {
            throw new IllegalArgumentException("个人工作流 Spec 不能为空");
        }

        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "user-workflow",
                agent.definition().getName() + " / Personal Workflow",
                sourceText,
                agent.allowedToolIds(),
                List.of(),
                agent.definition().getHandoffRule(),
                agent.definition().getSafetyLevel()
        ));

        List<CompiledRoutine> routines = parseRoutines(sourceText, agent.allowedToolIds(), profile.getTimezone());
        List<String> warnings = new ArrayList<>(compiled.warnings());
        if (routines.isEmpty()) {
            warnings.add("未解析出有效 routine；请至少写一条带时间或周期描述的流程。");
        }
        if (agent.definition().getPublishedVersionId() == null) {
            warnings.add("共享助手当前还没有已发布版本，个人 workflow 只能作为草稿与调试信息存在。");
        }
        if ("feishu_dm".equals(notificationTarget(profile).type())) {
            warnings.add("飞书私信通知链路已接入；真实送达仍需已启用机器人配置和已绑定用户做一次端到端验证。");
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("type", "user_workflow_pack");
        manifest.put("agentId", agent.agentId());
        manifest.put("scope", Map.of("companyId", companyId, "userId", userId));
        manifest.put("sharedAgentPublishedVersionId", agent.definition().getPublishedVersionId());
        manifest.put("allowedTools", agent.allowedToolIds());
        manifest.put("timezone", profile.getTimezone());
        manifest.put("routines", routines.stream().map(this::toRoutinePayload).toList());

        Map<String, Object> preview = Map.of(
                "format", "routine-list",
                "nodes", routines.stream().map(routine -> Map.of(
                        "id", routine.routineKey(),
                        "label", routine.name(),
                        "type", routine.triggerType().toLowerCase(Locale.ROOT),
                        "summary", routine.rawLine()
                )).toList(),
                "edges", List.of()
        );

        List<String> summary = new ArrayList<>(compiled.compileSummary());
        summary.add("routines=" + routines.size());
        summary.add("timezone=" + profile.getTimezone());
        summary.add("sharedAgent=" + agent.agentId());
        summary.add("notificationTarget=" + notificationTarget(profile).type());

        List<String> dependencies = new ArrayList<>();
        dependencies.add("shared-agent:" + agent.agentId());
        dependencies.add("shared-published-version-id:" + (agent.definition().getPublishedVersionId() == null ? "none" : agent.definition().getPublishedVersionId()));
        agent.allowedToolIds().forEach(toolId -> dependencies.add("tool:" + toolId));

        Integer versionNo = userWorkflowVersionRepository
                .findTopByCompanyIdAndUserIdAndAgentIdOrderByVersionNoDesc(companyId, userId, agent.agentId())
                .map(item -> item.getVersionNo() + 1)
                .orElse(1);

        String workflowCode = buildWorkflowCode(agent.agentId(), routines);
        UserWorkflowVersionEntity version = new UserWorkflowVersionEntity(
                companyId,
                userId,
                agent.agentId(),
                spec.getId(),
                versionNo,
                "v" + versionNo,
                sourceText,
                workflowCode,
                toJson(manifest),
                toJson(preview),
                toJson(summary),
                toJson(warnings),
                toJson(dependencies),
                "DRAFT"
        );
        userWorkflowVersionRepository.save(version);
        spec.markCompiled(versionNo);
        auditService.log(companyId, userId, "user.workflow.compile", "agent=" + agent.agentId() + ",version=" + versionNo);
        return new CompileResult(version, manifest, preview, summary, warnings, dependencies, routines);
    }

    /**
     * Creates one additional personal scheduled routine without changing the owner, Agent or notification profile.
     * The caller supplies only human-readable schedule text and task content; this service owns the tenant scope.
     */
    @Transactional
    public AssistantScheduleResult createScheduledRoutine(String companyId,
                                                           String userId,
                                                           String requestedAgentId,
                                                           String title,
                                                           String cadence,
                                                           String task) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        String normalizedCadence = safeText(cadence).trim();
        String normalizedTask = safeText(task).trim();
        if (!looksLikeExecutableSchedule(normalizedCadence)) {
            throw new IllegalArgumentException("请提供明确周期，例如“每天 09:00”或“每周一 09:00”。");
        }
        if (normalizedTask.isBlank()) {
            throw new IllegalArgumentException("定时任务内容不能为空。");
        }
        UserWorkflowSpecEntity spec = getOrCreateSpec(companyId, userId, agent.agentId());
        String routineLine = normalizedCadence + " " + normalizedTask;
        String source = safeText(spec.getSourceText()).trim();
        if (!source.lines().map(String::trim).anyMatch(routineLine::equals)) {
            source = source.isBlank() ? routineLine : source + "\n" + routineLine;
        }
        CompileResult compiled = compile(companyId, userId, agent.agentId(), new CompileCommand(source));
        UserWorkflowVersionEntity published = publish(companyId, userId, agent.agentId(), compiled.version().getVersionNo());
        CompiledRoutine routine = parseManifestRoutines(published).stream()
                .filter(item -> routineLine.equals(item.rawLine()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("已发布版本未生成定时 routine"));
        UserWorkflowTriggerEntity trigger = userWorkflowTriggerRepository
                .findByCompanyIdAndUserIdAndAgentIdAndRoutineKey(companyId, userId, agent.agentId(), routine.routineKey())
                .orElseThrow(() -> new IllegalStateException("已发布版本未生成定时 trigger"));
        auditService.log(companyId, userId, "user.workflow.schedule.create",
                "agent=" + agent.agentId() + ",trigger=" + trigger.getId());
        return new AssistantScheduleResult(
                trigger.getId(),
                routine.routineKey(),
                title == null || title.isBlank() ? routine.name() : limitText(title.trim(), 80),
                trigger.getNextFireAt(),
                published.getVersionNo());
    }

    public List<UserWorkflowVersionEntity> listVersions(String companyId, String userId, String requestedAgentId) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        return userWorkflowVersionRepository.findByCompanyIdAndUserIdAndAgentIdOrderByVersionNoDesc(companyId, userId, agentId);
    }

    public List<UserQuickCommandEntity> listQuickCommands(String companyId, String userId, String requestedAgentId) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        return userQuickCommandRepository.findByCompanyIdAndUserIdAndAgentIdAndEnabledTrueOrderBySortOrderAscIdAsc(
                companyId,
                userId,
                agentId
        );
    }

    @Transactional
    public UserQuickCommandEntity createQuickCommand(String companyId,
                                                    String userId,
                                                    String requestedAgentId,
                                                    CreateQuickCommandCommand command) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        String promptText = limitText(safeText(command.promptText()).trim(), 2000);
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("快捷指令内容不能为空");
        }
        String title = limitText(safeText(command.title()).trim(), 80);
        if (title.isBlank()) {
            title = deriveQuickCommandTitle(promptText);
        }
        int nextSortOrder = userQuickCommandRepository.maxSortOrder(companyId, userId, agentId) + 1;
        UserQuickCommandEntity saved = userQuickCommandRepository.save(new UserQuickCommandEntity(
                companyId,
                userId,
                agentId,
                title,
                promptText,
                nextSortOrder
        ));
        auditService.log(companyId, userId, "user.quick_command.create", "agent=" + agentId + ",id=" + saved.getId());
        return saved;
    }

    @Transactional
    public UserWorkflowVersionEntity publish(String companyId, String userId, String requestedAgentId, Integer versionNo) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserWorkflowSpecEntity spec = getOrCreateSpec(companyId, userId, agent.agentId());
        UserWorkflowVersionEntity target = userWorkflowVersionRepository
                .findByCompanyIdAndUserIdAndAgentIdAndVersionNo(companyId, userId, agent.agentId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException("个人工作流版本不存在: " + versionNo));
        userWorkflowVersionRepository.findByCompanyIdAndUserIdAndAgentIdAndPublishStatus(companyId, userId, agent.agentId(), "PUBLISHED")
                .ifPresent(previous -> previous.setPublishStatus("ARCHIVED"));
        target.setPublishStatus("PUBLISHED");
        spec.markPublished(target.getId());
        materializeTriggers(companyId, userId, agent.agentId(), target, getOrCreateProfile(companyId, userId, agent.agentId()).getTimezone());
        auditService.log(companyId, userId, "user.workflow.publish", "agent=" + agent.agentId() + ",version=" + versionNo);
        return target;
    }

    @Transactional
    public UserWorkflowVersionEntity rollback(String companyId, String userId, String requestedAgentId, Integer versionNo) {
        return publish(companyId, userId, requestedAgentId, versionNo);
    }

    public List<UserWorkflowTriggerEntity> listTriggers(String companyId, String userId, String requestedAgentId) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        return userWorkflowTriggerRepository.findByCompanyIdAndUserIdAndAgentIdOrderByIdAsc(companyId, userId, agentId);
    }

    @Transactional
    public UserWorkflowTriggerEntity updateTrigger(String companyId,
                                                   String userId,
                                                   String requestedAgentId,
                                                   Long triggerId,
                                                   UpdateTriggerCommand command) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        UserWorkflowTriggerEntity trigger = userWorkflowTriggerRepository
                .findByIdAndCompanyIdAndUserIdAndAgentId(triggerId, companyId, userId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("个人 workflow trigger 不存在: " + triggerId));
        boolean enabled = command.enabled() == null || command.enabled();
        Instant nextFireAt = enabled ? computeNextFire(trigger.getTriggerType(), trigger.getCronExpr(), trigger.getTimezone(), trigger.getIntervalSeconds(), Instant.now()) : null;
        trigger.updateEnabled(enabled, nextFireAt);
        auditService.log(companyId, userId, "user.workflow.trigger.update", "agent=" + agentId + ",trigger=" + triggerId + ",enabled=" + enabled);
        return trigger;
    }

    public List<UserWorkflowExecutionEntity> listExecutions(String companyId, String userId, String requestedAgentId) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        return userWorkflowExecutionRepository.findTop20ByCompanyIdAndUserIdAndAgentIdOrderByIdDesc(companyId, userId, agentId);
    }

    public UserWorkflowExecutionEntity getExecution(String companyId, String userId, String requestedAgentId, Long executionId) {
        String agentId = loadAgentContext(companyId, requestedAgentId).agentId();
        return userWorkflowExecutionRepository.findByIdAndCompanyIdAndUserIdAndAgentId(executionId, companyId, userId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("执行记录不存在: " + executionId));
    }

    @Transactional
    public UserWorkflowExecutionEntity runNow(String companyId, String userId, String requestedAgentId, String routineKey) {
        AgentContext agent = loadAgentContext(companyId, requestedAgentId);
        UserWorkflowVersionEntity version = userWorkflowVersionRepository
                .findByCompanyIdAndUserIdAndAgentIdAndPublishStatus(companyId, userId, agent.agentId(), "PUBLISHED")
                .orElseThrow(() -> new IllegalArgumentException("请先发布个人 workflow 后再执行。"));
        CompiledRoutine routine = findRoutine(version, routineKey);
        UserWorkflowExecutionEntity execution = new UserWorkflowExecutionEntity(
                companyId,
                userId,
                agent.agentId(),
                version.getId(),
                userWorkflowTriggerRepository.findByCompanyIdAndUserIdAndAgentIdAndRoutineKey(companyId, userId, agent.agentId(), routine.routineKey())
                        .map(UserWorkflowTriggerEntity::getId)
                        .orElse(null),
                routine.routineKey(),
                "MANUAL",
                Instant.now()
        );
        userWorkflowExecutionRepository.save(execution);
        executeWorkflow(version, routine, getOrCreateProfile(companyId, userId, agent.agentId()), execution);
        auditService.log(companyId, userId, "user.workflow.run_now", "agent=" + agent.agentId() + ",routine=" + routine.routineKey());
        recordWorkflowBillingSafely(execution);
        return execution;
    }

    @Transactional
    public void triggerDueWorkflows() {
        Instant now = Instant.now();
        List<UserWorkflowTriggerEntity> dueTriggers = userWorkflowTriggerRepository
                .findTop100ByEnabledTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(now);
        for (UserWorkflowTriggerEntity trigger : dueTriggers) {
            Optional<UserWorkflowVersionEntity> version = userWorkflowVersionRepository.findById(trigger.getVersionId());
            if (version.isEmpty()) {
                continue;
            }
            Instant nextFireAt = computeNextFire(trigger.getTriggerType(), trigger.getCronExpr(), trigger.getTimezone(), trigger.getIntervalSeconds(), now);
            trigger.markTriggered(now, nextFireAt);
            UserWorkflowExecutionEntity execution = new UserWorkflowExecutionEntity(
                    trigger.getCompanyId(),
                    trigger.getUserId(),
                    trigger.getAgentId(),
                    trigger.getVersionId(),
                    trigger.getId(),
                    trigger.getRoutineKey(),
                    "SCHEDULE",
                    now
            );
            userWorkflowExecutionRepository.save(execution);
            UserAgentProfileEntity profile = getOrCreateProfile(trigger.getCompanyId(), trigger.getUserId(), trigger.getAgentId());
            try {
                executeWorkflow(version.get(), findRoutine(version.get(), trigger.getRoutineKey()), profile, execution);
                recordWorkflowBillingSafely(execution);
            } catch (Exception ex) {
                execution.markFailed(traceJson(List.of(Map.of(
                        "type", "error",
                        "message", ex.getMessage()
                ))), "EXECUTION_FAILED", ex.getMessage());
            }
        }
    }

    private void materializeTriggers(String companyId,
                                     String userId,
                                     String agentId,
                                     UserWorkflowVersionEntity version,
                                     String timezone) {
        List<CompiledRoutine> routines = parseManifestRoutines(version);
        userWorkflowTriggerRepository.deleteByCompanyIdAndUserIdAndAgentId(companyId, userId, agentId);
        for (CompiledRoutine routine : routines) {
            Instant nextFireAt = computeNextFire(
                    routine.triggerType(),
                    routine.cronExpr(),
                    routine.timezone() == null ? timezone : routine.timezone(),
                    routine.intervalSeconds(),
                    Instant.now()
            );
            userWorkflowTriggerRepository.save(new UserWorkflowTriggerEntity(
                    companyId,
                    userId,
                    agentId,
                    version.getId(),
                    routine.routineKey(),
                    routine.name(),
                    routine.triggerType(),
                    routine.cronExpr(),
                    routine.timezone() == null ? timezone : routine.timezone(),
                    routine.intervalSeconds(),
                    null,
                    "{}",
                    !"MANUAL".equals(routine.triggerType()),
                    nextFireAt
            ));
        }
    }

    private void executeWorkflow(UserWorkflowVersionEntity version,
                                 CompiledRoutine routine,
                                 UserAgentProfileEntity profile,
                                 UserWorkflowExecutionEntity execution) {
        execution.markRunning();
        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(Map.of(
                "type", "start",
                "routineKey", routine.routineKey(),
                "routineName", routine.name(),
                "source", execution.getTriggerSource()
        ));

        NotificationTarget notificationTarget = notificationTarget(profile);
        if (!profile.isEnabled()) {
            trace.add(Map.of("type", "skip", "message", "个人工作流已在 profile 中关闭"));
            execution.markFailed(traceJson(trace), "PROFILE_DISABLED", "个人工作流已关闭");
            return;
        }

        List<String> snippets = new ArrayList<>();
        for (String toolName : actualToolNames(routine)) {
            String argumentsJson = buildToolArgs(routine, toolName);
            String result = toolOrchestratorService.executeTool(version.getCompanyId(), profile.getUserId(), toolName, argumentsJson);
            trace.add(Map.of(
                    "type", "tool",
                    "toolName", toolName,
                    "arguments", parseJson(argumentsJson),
                    "resultSnippet", truncate(result, 800)
            ));
            if (toolName.startsWith("email_")) {
                snippets.add(formatEmailSnippet(toolName, result, routine.rawLine()));
            } else {
                snippets.add(toolName + " -> " + truncate(result, 300));
            }
        }

        if (routine.rawLine().contains("新闻") || routine.rawLine().contains("AI 大事")) {
            trace.add(Map.of("type", "note", "message", "新闻检索工具尚未接入，本次仅保留该 routine 计划与执行骨架。"));
            snippets.add("新闻检索工具尚未接入，当前仅保留执行骨架。");
        }
        if (routine.rawLine().contains("会议邀请")) {
            trace.add(Map.of("type", "note", "message", "会议邀请发送能力待后续接入日历/IM 主动消息接口。"));
            snippets.add("会议邀请发送能力待接入。");
        }
        if (routine.rawLine().contains("工作总结")) {
            List<UserWorkflowExecutionEntity> recent = userWorkflowExecutionRepository
                    .findTop20ByCompanyIdAndUserIdAndAgentIdOrderByIdDesc(version.getCompanyId(), profile.getUserId(), version.getAgentId());
            String digest = recent.stream()
                    .filter(item -> "SUCCESS".equals(item.getStatus()) && item.getOutputSummary() != null && !item.getOutputSummary().isBlank())
                    .limit(5)
                    .map(item -> "- " + truncate(item.getOutputSummary(), 80))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("暂无可汇总的历史执行结果。");
            trace.add(Map.of("type", "summary", "message", digest));
            snippets.add("近期执行摘要：\n" + digest);
        }

        String resultsBlock;
        if (snippets.isEmpty()) {
            resultsBlock = "当前未命中可真实执行的工具，已记录为骨架执行。\n";
        } else {
            StringBuilder rb = new StringBuilder("执行结果：\n");
            for (String snippet : snippets) {
                // Each snippet may itself be multi-line (email list); indent continuation lines
                String[] sLines = snippet.split("\\R");
                for (int i = 0; i < sLines.length; i++) {
                    if (i == 0) {
                        rb.append(sLines[i]).append("\n");
                    } else {
                        rb.append("  ").append(sLines[i]).append("\n");
                    }
                }
            }
            resultsBlock = rb.toString();
        }
        String outputSummary = "【" + routine.name() + "】\n"
                + "目标：" + routine.rawLine() + "\n"
                + resultsBlock
                + "通知目标：" + notificationTarget.type()
                + (notificationTarget.value().isBlank() ? "" : " / " + notificationTarget.value());
        NotificationDelivery delivery = deliverNotification(version.getCompanyId(), profile.getUserId(), notificationTarget, outputSummary);
        trace.add(Map.of(
                "type", "notification",
                "targetType", notificationTarget.type(),
                "targetValue", delivery.resolvedTargetValue(),
                "status", delivery.status(),
                "message", delivery.message()
        ));
        execution.markSuccess(traceJson(trace), outputSummary);
    }

    private void recordWorkflowBillingSafely(UserWorkflowExecutionEntity execution) {
        if (execution == null || !"SUCCESS".equals(execution.getStatus())) {
            return;
        }
        billingUsageMeteringService.recordWorkflowRunSafely(new BillingUsageMeteringService.WorkflowRunMeteringInput(
                execution.getCompanyId(),
                execution.getUserId(),
                execution.getAgentId(),
                "user_workflow",
                execution.getId(),
                execution.getRoutineKey(),
                execution.getTriggerSource(),
                0,
                "user-workflow-run",
                execution.getCompanyId() + ":user:" + execution.getUserId() + ":workflow-execution:" + execution.getId(),
                Instant.now()));
    }

    private List<String> actualToolNames(CompiledRoutine routine) {
        List<String> tools = new ArrayList<>();
        for (String toolId : routine.allowedTools()) {
            if ("approval-fetch".equals(toolId) || "get_pending_approvals".equals(toolId)) {
                tools.add("get_pending_approvals");
            } else if (toolId.startsWith("email_")) {
                tools.add(toolId);
            } else if ("tavily_search".equals(toolId) || "tavily_extract".equals(toolId)) {
                tools.add(toolId);
            }
        }
        return tools;
    }

    private String buildToolArgs(CompiledRoutine routine, String toolName) {
        Map<String, Object> args = new LinkedHashMap<>();
        if ("email_list_inbox".equals(toolName)) {
            args.put("limit", 10);
        } else if ("email_search".equals(toolName)) {
            args.put("limit", 5);
            List<String> keywords = extractWatchKeywords(routine.rawLine());
            if (!keywords.isEmpty()) {
                args.put("keyword", keywords.get(0));
            }
        } else if ("tavily_search".equals(toolName)) {
            args.put("query", routine.rawLine());
            args.put("max_results", 5);
        }
        return toJson(args);
    }

    /**
     * Formats the raw email tool result into a human-readable snippet suitable for Feishu
     * notification. Parses each "- [date] from · subject · id=xxx" line and re-renders as
     * a numbered list. When the routine mentions "总结" or "摘要", appends a subject-based
     * keyword summary.
     */
    private String formatEmailSnippet(String toolName, String rawResult, String rawLine) {
        if (rawResult == null || rawResult.isBlank()) {
            return toolName + " -> （无结果）";
        }
        // Pass-through for error / empty inbox messages (no bullet lines)
        boolean hasEntries = rawResult.contains("\n- [");
        if (!hasEntries) {
            return rawResult.trim();
        }

        String[] lines = rawResult.split("\\R");
        StringBuilder header = new StringBuilder();
        List<String> emailLines = new ArrayList<>();
        StringBuilder footer = new StringBuilder();
        boolean inEntries = false;
        for (String line : lines) {
            if (line.startsWith("- [")) {
                inEntries = true;
                emailLines.add(line);
            } else if (inEntries) {
                footer.append(line).append("\n");
            } else {
                header.append(line).append("\n");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header.toString().stripTrailing()).append("\n");

        int idx = 1;
        List<String> subjects = new ArrayList<>();
        for (String entry : emailLines) {
            // entry: "- [2026-04-21 14:48] Sender Name <addr> · Subject text · id=xxx"
            String body = entry.startsWith("- ") ? entry.substring(2) : entry;
            // Strip the id= suffix to reduce noise in notification
            int idPos = body.lastIndexOf(" · id=");
            if (idPos > 0) {
                body = body.substring(0, idPos);
            }
            sb.append(idx).append(". ").append(body).append("\n");
            // Extract subject (last segment after last " · ")
            int lastDot = body.lastIndexOf(" · ");
            if (lastDot > 0) {
                String subj = body.substring(lastDot + 3).trim();
                if (!subj.isBlank()) {
                    subjects.add(subj);
                }
            }
            idx++;
        }

        String footerStr = footer.toString().stripTrailing();
        if (!footerStr.isBlank()) {
            sb.append("\n").append(footerStr);
        }

        // Append a simple subject summary when the spec asks for one
        boolean wantsSummary = rawLine.contains("总结") || rawLine.contains("摘要") || rawLine.contains("汇总");
        if (wantsSummary && !subjects.isEmpty()) {
            sb.append("\n📋 邮件主题摘要：\n");
            for (int i = 0; i < subjects.size(); i++) {
                sb.append("  • ").append(truncate(subjects.get(i), 60)).append("\n");
            }
        }
        return sb.toString().stripTrailing();
    }

    private List<String> extractWatchKeywords(String rawLine) {
        String cleaned = safeText(rawLine).replace("重点关注", "").replace("关注", "");
        String[] tokens = cleaned.split("[，,、\\s]+");
        List<String> values = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() >= 2 && !token.contains("邮件") && !token.contains("检查") && !token.contains("通知")) {
                values.add(token);
            }
        }
        return values.stream().distinct().limit(3).toList();
    }

    private CompiledRoutine findRoutine(UserWorkflowVersionEntity version, String routineKey) {
        List<CompiledRoutine> routines = parseManifestRoutines(version);
        if (routines.isEmpty()) {
            throw new IllegalArgumentException("该版本未解析出任何 routine");
        }
        if (routineKey == null || routineKey.isBlank()) {
            return routines.get(0);
        }
        return routines.stream()
                .filter(item -> Objects.equals(item.routineKey(), routineKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("找不到 routine: " + routineKey));
    }

    private List<CompiledRoutine> parseManifestRoutines(UserWorkflowVersionEntity version) {
        Map<String, Object> manifest = readJsonMap(version.getWorkflowManifest());
        Object raw = manifest.get("routines");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<CompiledRoutine> routines = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> trigger = asMap(map.get("trigger"));
            Map<String, Object> toolPolicy = asMap(map.get("toolPolicy"));
            List<String> allowedTools = asStringList(toolPolicy.get("allowedTools"));
            routines.add(new CompiledRoutine(
                    safeText(map.get("routineKey")),
                    safeText(map.get("name")),
                    safeText(map.get("rawLine")),
                    safeText(trigger.get("type")).toUpperCase(Locale.ROOT),
                    blankToNull(safeText(trigger.get("cronExpr"))),
                    blankToNull(safeText(trigger.get("timezone"))),
                    asInteger(trigger.get("intervalSeconds")),
                    allowedTools
            ));
        }
        return routines;
    }

    private AgentContext loadAgentContext(String companyId, String requestedAgentId) {
        String agentId = safeText(requestedAgentId).isBlank() ? "cici-system" : safeText(requestedAgentId).trim().toLowerCase(Locale.ROOT);
        agentDefinitionService.warmupBuiltinAgents(companyId);
        AgentDefinitionService.AgentDetail detail = agentDefinitionService.get(companyId, agentId);
        LinkedHashSet<String> tools = new LinkedHashSet<>(detail.toolIds());
        if ("cici-system".equals(detail.definition().getAgentId())) {
            tools.addAll(EXTRA_CICI_TOOLS);
        }
        return new AgentContext(detail.definition(), List.copyOf(tools));
    }

    private UserAgentProfileEntity getOrCreateProfile(String companyId, String userId, String agentId) {
        return userAgentProfileRepository.findByCompanyIdAndUserIdAndAgentId(companyId, userId, agentId)
                .orElseGet(() -> userAgentProfileRepository.save(new UserAgentProfileEntity(
                        companyId,
                        userId,
                        agentId,
                        "Asia/Shanghai",
                        "zh-CN",
                        "{\"type\":\"log_only\",\"value\":\"\"}",
                        "{}",
                        true
                )));
    }

    private UserWorkflowSpecEntity getOrCreateSpec(String companyId, String userId, String agentId) {
        return userWorkflowSpecRepository.findByCompanyIdAndUserIdAndAgentId(companyId, userId, agentId)
                .orElseGet(() -> userWorkflowSpecRepository.save(new UserWorkflowSpecEntity(companyId, userId, agentId, "")));
    }

    private String deriveQuickCommandTitle(String promptText) {
        String firstLine = safeText(promptText).split("\\R", 2)[0].trim();
        if (firstLine.length() <= 24) {
            return firstLine;
        }
        return firstLine.substring(0, 24);
    }

    private String limitText(String value, int maxLength) {
        String text = safeText(value);
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private List<CompiledRoutine> parseRoutines(String sourceText, List<String> allowedTools, String timezone) {
        List<CompiledRoutine> routines = new ArrayList<>();
        String[] lines = safeText(sourceText).split("\\R");
        int index = 1;
        for (String line : lines) {
            String cleaned = NUMBERING_PREFIX.matcher(line.trim()).replaceFirst("").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            TriggerShape trigger = inferTrigger(cleaned, timezone);
            routines.add(new CompiledRoutine(
                    "routine-" + index,
                    buildRoutineName(cleaned, index),
                    cleaned,
                    trigger.type(),
                    trigger.cronExpr(),
                    trigger.timezone(),
                    trigger.intervalSeconds(),
                    inferAllowedTools(cleaned, allowedTools)
            ));
            index += 1;
        }
        return routines;
    }

    private TriggerShape inferTrigger(String line, String timezone) {
        Matcher interval = INTERVAL_PATTERN.matcher(line);
        if (interval.find()) {
            return new TriggerShape("INTERVAL", null, timezone, Integer.parseInt(interval.group(1)) * 60);
        }
        Matcher clock = CLOCK_PATTERN.matcher(line);
        if (clock.find()) {
            String period = safeText(clock.group(1));
            String matchedText = safeText(clock.group(0));
            boolean hasExplicitTimeMarker = matchedText.contains(":")
                    || matchedText.contains("点")
                    || matchedText.contains("时")
                    || !period.isBlank();
            if (!hasExplicitTimeMarker) {
                return new TriggerShape("MANUAL", null, timezone, null);
            }
            int hour = Integer.parseInt(clock.group(2));
            int minute = clock.group(3) == null || clock.group(3).isBlank() ? 0 : Integer.parseInt(clock.group(3));
            if ("下午".equals(period) || "晚上".equals(period)) {
                if (hour < 12) {
                    hour += 12;
                }
            } else if ("中午".equals(period) && hour < 11) {
                hour += 12;
            } else if ("上午".equals(period) && hour == 12) {
                hour = 0;
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return new TriggerShape("MANUAL", null, timezone, null);
            }
            return new TriggerShape("SCHEDULE", "0 " + minute + " " + hour + " * * *", timezone, null);
        }
        return new TriggerShape("MANUAL", null, timezone, null);
    }

    private List<String> inferAllowedTools(String line, List<String> allowedTools) {
        String lower = safeText(line).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> matched = new LinkedHashSet<>();
        if ((line.contains("邮箱") || line.contains("邮件") || lower.contains("mail")) && allowedTools.contains("email_list_inbox")) {
            matched.add("email_list_inbox");
            if (line.contains("搜索") || line.contains("重点关注") || line.contains("关注")) {
                matched.add("email_search");
            }
        }
        if ((line.contains("审批") || lower.contains("approval")) && (allowedTools.contains("get_pending_approvals") || allowedTools.contains("approval-fetch"))) {
            matched.add("get_pending_approvals");
        }
        return List.copyOf(matched);
    }

    private String buildRoutineName(String line, int index) {
        String name = line;
        name = name.replaceFirst("^(上午|下午|中午|晚上)?\\s*\\d{1,2}(?:[:点时]\\s*\\d{1,2})?\\s*(?:分)?[，,、]?", "").trim();
        name = name.replaceFirst("^每\\s*\\d{1,3}\\s*分钟[，,、]?", "").trim();
        return name.isBlank() ? "Routine " + index : truncate(name, 30);
    }

    private Map<String, Object> toRoutinePayload(CompiledRoutine routine) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("routineKey", routine.routineKey());
        out.put("name", routine.name());
        out.put("rawLine", routine.rawLine());
        out.put("trigger", Map.of(
                "type", routine.triggerType().toLowerCase(Locale.ROOT),
                "cronExpr", routine.cronExpr() == null ? "" : routine.cronExpr(),
                "timezone", routine.timezone() == null ? "" : routine.timezone(),
                "intervalSeconds", routine.intervalSeconds() == null ? 0 : routine.intervalSeconds()
        ));
        out.put("toolPolicy", Map.of("allowedTools", routine.allowedTools()));
        return out;
    }

    private Instant computeNextFire(String triggerType,
                                    String cronExpr,
                                    String timezone,
                                    Integer intervalSeconds,
                                    Instant from) {
        String normalizedType = safeText(triggerType).toUpperCase(Locale.ROOT);
        if ("INTERVAL".equals(normalizedType) && intervalSeconds != null && intervalSeconds > 0) {
            return from.plus(intervalSeconds, ChronoUnit.SECONDS);
        }
        if ("SCHEDULE".equals(normalizedType) && cronExpr != null && !cronExpr.isBlank()) {
            String[] parts = cronExpr.trim().split("\\s+");
            if (parts.length == 6) {
                int minute = Integer.parseInt(parts[1]);
                int hour = Integer.parseInt(parts[2]);
                if (minute < 0 || minute > 59 || hour < 0 || hour > 23) {
                    return null;
                }
                ZoneId zoneId = ZoneId.of(safeTimezone(timezone));
                ZonedDateTime now = from.atZone(zoneId);
                ZonedDateTime next = ZonedDateTime.of(LocalDateTime.of(now.getYear(), now.getMonthValue(), now.getDayOfMonth(), hour, minute), zoneId);
                if (!next.isAfter(now)) {
                    next = next.plusDays(1);
                }
                return next.toInstant();
            }
        }
        return null;
    }

    private String buildWorkflowCode(String agentId, List<CompiledRoutine> routines) {
        StringBuilder builder = new StringBuilder();
        builder.append("export async function runUserWorkflowPack(ctx) {\n");
        builder.append("  return {\n");
        builder.append("    agentId: ").append(jsonString(agentId)).append(",\n");
        builder.append("    routines: [\n");
        for (CompiledRoutine routine : routines) {
            builder.append("      { key: ").append(jsonString(routine.routineKey()))
                    .append(", name: ").append(jsonString(routine.name()))
                    .append(", trigger: ").append(jsonString(routine.triggerType()))
                    .append(" },\n");
        }
        builder.append("    ]\n");
        builder.append("  };\n");
        builder.append("}\n");
        return builder.toString();
    }

    private NotificationTarget notificationTarget(UserAgentProfileEntity profile) {
        Map<String, Object> json = readJsonMap(profile.getNotificationTargetJson());
        String type = safeText(json.get("type"));
        if (type.isBlank()) {
            type = "log_only";
        }
        return new NotificationTarget(type, safeText(json.get("value")));
    }

    private NotificationDelivery deliverNotification(String companyId, String userId, NotificationTarget target, String text) {
        if (!"feishu_dm".equals(target.type())) {
            return new NotificationDelivery("SKIPPED", target.value(), "当前通知模式为记录执行结果，不主动外发。");
        }
        String resolvedOpenId = target.value();
        if (resolvedOpenId.isBlank()) {
            resolvedOpenId = feishuBotPairingService.findActiveBindingForUser(companyId, userId)
                    .map(FeishuBotBindingEntity::getOpenId)
                    .orElse("");
        }
        if (resolvedOpenId.isBlank()) {
            return new NotificationDelivery("FAILED", "", "未找到可用的飞书 open_id；请先完成飞书配对或手动填写通知目标。");
        }
        FeishuBotConfigService.FeishuBotConfig config = feishuBotConfigService.getEnabledConfig(companyId).orElse(null);
        if (config == null) {
            return new NotificationDelivery("FAILED", resolvedOpenId, "组织尚未启用可用的飞书机器人配置。");
        }
        try {
            feishuBotMessenger.sendTextToOpenId(config, resolvedOpenId, text);
            return new NotificationDelivery("SENT", resolvedOpenId, "已通过飞书私信主动发送执行结果。");
        } catch (Exception ex) {
            return new NotificationDelivery("FAILED", resolvedOpenId, ex.getMessage());
        }
    }

    private Map<String, Object> normalizeMap(Map<String, Object> input) {
        return input == null ? Map.of() : new LinkedHashMap<>(input);
    }

    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private Map<String, Object> readJsonMap(String value) {
        return parseJson(value);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(item -> item == null ? "" : String.valueOf(item).trim()).filter(item -> !item.isBlank()).toList();
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? null : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String traceJson(List<Map<String, Object>> trace) {
        return toJson(trace);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize workflow artifact", ex);
        }
    }

    private String jsonString(String value) {
        return "\"" + safeText(value).replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String safeTimezone(String timezone) {
        String candidate = safeText(timezone).trim();
        if (candidate.isBlank()) {
            return "Asia/Shanghai";
        }
        try {
            ZoneId.of(candidate);
            return candidate;
        } catch (Exception ex) {
            return "Asia/Shanghai";
        }
    }

    private boolean looksLikeExecutableSchedule(String cadence) {
        String value = safeText(cadence).trim().toLowerCase(Locale.ROOT);
        return value.contains("每天") || value.contains("每周") || value.contains("每月")
                || value.contains("工作日") || value.contains("cron")
                || INTERVAL_PATTERN.matcher(value).find()
                || CLOCK_PATTERN.matcher(value).find();
    }

    private String truncate(String text, int maxLength) {
        String normalized = safeText(text);
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }

    public record UpdateProfileCommand(
            String timezone,
            String locale,
            Map<String, Object> notificationTarget,
            Map<String, Object> personalContext,
            Boolean enabled
    ) {
    }

    public record CompileCommand(String sourceText) {
    }

    public record CreateQuickCommandCommand(String title, String promptText) {
    }

    public record UpdateTriggerCommand(Boolean enabled) {
    }

    public record WorkflowBundle(
            AgentContext agent,
            UserAgentProfileEntity profile,
            UserWorkflowSpecEntity spec,
            List<UserWorkflowVersionEntity> versions,
            List<UserWorkflowTriggerEntity> triggers,
            List<UserWorkflowExecutionEntity> executions,
            UserWorkflowVersionEntity latestDraftVersion
    ) {
    }

    public record CompileResult(
            UserWorkflowVersionEntity version,
            Map<String, Object> workflowManifest,
            Map<String, Object> workflowPreview,
            List<String> compileSummary,
            List<String> warnings,
            List<String> dependencies,
            List<CompiledRoutine> routines
    ) {
    }

    public record AssistantScheduleResult(Long triggerId,
                                          String routineKey,
                                          String title,
                                          Instant nextFireAt,
                                          Integer versionNo) {
    }

    public record AgentContext(AgentDefinitionEntity definition, List<String> allowedToolIds) {
        public String agentId() {
            return definition.getAgentId();
        }
    }

    public record CompiledRoutine(
            String routineKey,
            String name,
            String rawLine,
            String triggerType,
            String cronExpr,
            String timezone,
            Integer intervalSeconds,
            List<String> allowedTools
    ) {
    }

    private record TriggerShape(String type, String cronExpr, String timezone, Integer intervalSeconds) {
    }

    private record NotificationTarget(String type, String value) {
    }

    private record NotificationDelivery(String status, String resolvedTargetValue, String message) {
    }
}
