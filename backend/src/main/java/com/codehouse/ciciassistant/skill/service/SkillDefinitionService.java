package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.agent.domain.AgentWorkflowSkillRefRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.platform.domain.PlatformSkillTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillDefinitionService {

    private static final Set<String> CORE_POLICY_CODES = Set.of(
            "conversation-core",
            "knowledge-first",
            "safe-handoff"
    );

    private static final List<BuiltinSkillSpec> BUILTIN_SKILLS = List.of(
            new BuiltinSkillSpec(
                    "conversation-core",
                    "对话基础",
                    "统一回答语言、格式和基础沟通风格。",
                    "Follow the user's language. Keep the reply concise, readable, and professional. "
                            + "Use short Markdown sections when helpful. Never expose chain-of-thought.",
                    null,
                    null,
                    null,
                    "结论优先，必要时补充依据与下一步建议。",
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "knowledge-first",
                    "知识优先",
                    "优先依赖知识库与已知事实回答。",
                    "When knowledge context is available, prefer grounded answers. "
                            + "If the available knowledge is insufficient, ask a clarifying question or state the limit clearly.",
                    null,
                    null,
                    null,
                    null,
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "safe-handoff",
                    "安全兜底",
                    "高风险、权限不清或涉及承诺的场景优先转人工。",
                    "If the request involves pricing commitments, approvals, sensitive operations, or unclear permissions, "
                            + "do not guess. Ask for confirmation or hand off to a human operator.",
                    null,
                    null,
                    "涉及价格承诺、审批决策、权限不清或高风险动作时，必须转人工或请求确认。",
                    null,
                    "HIGH"
            ),
            new BuiltinSkillSpec(
                    "general-assistant",
                    "通用助手",
                    "默认通用问答与协作分流能力。",
                    "Act as the default enterprise assistant. Handle general Q&A, route business requests, "
                            + "and keep the response practical and easy to act on.",
                    null,
                    null,
                    null,
                    "优先输出结论，再给出依据与下一步建议。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "sales-copilot",
                    "售前协同",
                    "面向销售和售前场景的客户查询与报价前置能力。",
                    "For sales-related requests, first determine whether the user needs product Q&A, customer lookup, or pricing support. "
                            + "Use CRM tools before giving account-specific answers. Escalate if the request implies a commitment.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及报价承诺、折扣确认或实施排期时，必须转人工确认。",
                    "输出包含客户背景、当前判断与建议动作。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-lead-intake",
                    "CRM 线索分诊",
                    "识别客户线索价值并输出后续跟进建议。",
                    "For inbound leads, classify quality into A/B/C by company profile, budget signal, urgency, "
                            + "and decision chain. Provide next best action for the owner.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及报价承诺、商务条款或跨部门资源调度时，必须转人工确认。",
                    "输出包含线索等级、判定依据、建议动作和负责人。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-opportunity-health",
                    "CRM 商机健康扫描",
                    "对商机推进进度、停留时长和风险进行健康评估。",
                    "Analyze opportunity health using stage aging, stakeholder activity, and risk signals. "
                            + "Return red/amber/green label with recovery actions.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及折扣确认、实施承诺或合同条款变更时，必须转人工处理。",
                    "输出包含健康等级、风险项、动作建议和完成时点。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-followup-orchestrator",
                    "CRM 跟进节奏编排",
                    "基于客户阶段生成多触点跟进节奏与话术重点。",
                    "Generate a 14-day follow-up cadence with channel mix and objective for each touchpoint. "
                            + "Escalate when consecutive responses are missing.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "客户提出商务谈判或价格诉求时，必须转人工客户经理。",
                    "输出包含触达日程、渠道、话术重点和升级条件。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "crm-renewal-guard",
                    "CRM 续约预警",
                    "识别续约窗口内的流失风险并给出保留动作。",
                    "Detect renewal churn risk based on contract window, usage trend, and support sentiment. "
                            + "Recommend a 72-hour retention plan with clear owner.",
                    String.join(",",
                            "cloudcc_getStandardObjects",
                            "cloudcc_getCustomObjects",
                            "cloudcc_getObjectFields",
                            "cloudcc_pageQuery"),
                    null,
                    "涉及价格让利、续费方案或合同改签时，必须转人工审批。",
                    "输出包含风险等级、关键证据、保留动作和负责人。",
                    "HIGH"
            ),
            new BuiltinSkillSpec(
                    "approval-assistant",
                    "审批推进",
                    "面向审批待办、催办与流程风险提醒。",
                    "For approval-related requests, summarize the current approval state, identify blockers, "
                            + "and recommend the next escalation or follow-up action.",
                    "get_pending_approvals",
                    null,
                    "当审批结果不明确、需要跨部门决策或存在异常风险时，必须转人工处理。",
                    "输出包含当前状态、风险判断、催办对象与下一步建议。",
                    "MEDIUM"
            ),
            new BuiltinSkillSpec(
                    "ai-meeting-notetaker",
                    "AI 听记",
                    "面向会议实时转写后的结构化纪要生成能力。",
                    "You are the AI meeting notetaker skill. When invoked with meeting transcript segments, "
                            + "produce concise, faithful, and action-oriented meeting minutes. Preserve speaker intent, "
                            + "separate facts from unresolved questions, never invent attendees, owners, deadlines, "
                            + "decisions, or timestamps, and mark missing information as 未明确.",
                    null,
                    null,
                    "会议内容缺少关键事实、负责人或截止日期时，不要补造；在开放问题中标明待确认项。",
                    "输出必须是中文 Markdown，固定包含 Meeting Summary、Date & Time、Participants、Topic、Summary、Action Items、Decisions Made、Open Questions；行动项必须用表格。",
                    "LOW"
            ),
            new BuiltinSkillSpec(
                    "web-search",
                    "Web 搜索",
                    "面向公开互联网的搜索与正文抽取能力，返回带 URL 的结构化来源。",
                    "When the user's question involves fresh public information, external facts, industry news, "
                            + "or topics unlikely to be covered by the tenant knowledge base, use the web-search skill:\n"
                            + "1) Call tavily_search first to discover candidate sources. Keep the query under 400 characters "
                            + "and use search-style phrasing (not a long prompt).\n"
                            + "   - If the question mentions '最新 / 今天 / 本周 / 今年' or other time signals, set time_range accordingly.\n"
                            + "   - For news-type questions set topic=news; for financial data set topic=finance.\n"
                            + "2) When the user needs the full body of a specific page (terms, data tables, long articles), call "
                            + "tavily_extract on the most relevant URLs with format=markdown.\n"
                            + "3) Prefer grounded answers: always cite 3~5 source links (title + URL) at the end of the reply. "
                            + "All citations must come from tavily_search / tavily_extract results.\n"
                            + "4) Do NOT send personal data, CRM customer records, or internal confidential text to Tavily — it is an external service.\n"
                            + "5) If tavily_search returns an error (e.g. TAVILY_NOT_CONFIGURED), fall back to tenant knowledge "
                            + "and tell the user that live web search is unavailable.",
                    String.join(",", "tavily_search", "tavily_extract"),
                    null,
                    null,
                    "答案末尾必须附 3~5 条可点击的来源链接（标题 + URL），来源均来自 tavily_search / tavily_extract。",
                    "LOW"
            )
    );

    private static final Map<String, List<DefaultBinding>> DEFAULT_AGENT_SKILLS = Map.of(
            "cici-system", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("general-assistant"),
                    DefaultBinding.intentRoute("ai-meeting-notetaker"),
                    DefaultBinding.intentRoute("web-search")
            ),
            "sales-agent", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("sales-copilot")
            ),
            "approval-agent", List.of(
                    DefaultBinding.alwaysOn("conversation-core"),
                    DefaultBinding.alwaysOn("knowledge-first"),
                    DefaultBinding.alwaysOn("safe-handoff"),
                    DefaultBinding.alwaysOn("approval-assistant")
            )
    );

    /** Default binding metadata for a builtin skill attached to an agent. */
    private record DefaultBinding(String skillCode, String activationMode) {
        static DefaultBinding alwaysOn(String code) {
            return new DefaultBinding(code, "always-on");
        }

        static DefaultBinding intentRoute(String code) {
            return new DefaultBinding(code, "intent-route");
        }
    }

    private final SkillDefinitionRepository skillDefinitionRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillPromptAssembler skillPromptAssembler;
    private final SkillVersionRepository skillVersionRepository;
    private final SpecCompilerService specCompilerService;
    private final ObjectMapper objectMapper;
    private final PlatformSkillTemplateRepository platformSkillTemplateRepository;
    private final AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository;
    private final SkillApiToolService skillApiToolService;
    private final FileBackedBuiltinSkillSyncService fileBackedBuiltinSkillSyncService;

    public SkillDefinitionService(SkillDefinitionRepository skillDefinitionRepository,
                                  AgentSkillBindingRepository agentSkillBindingRepository,
                                  SkillPromptAssembler skillPromptAssembler,
                                  SkillVersionRepository skillVersionRepository,
                                  SpecCompilerService specCompilerService,
                                  ObjectMapper objectMapper,
                                  PlatformSkillTemplateRepository platformSkillTemplateRepository,
                                  AgentWorkflowSkillRefRepository agentWorkflowSkillRefRepository,
                                  SkillApiToolService skillApiToolService,
                                  FileBackedBuiltinSkillSyncService fileBackedBuiltinSkillSyncService) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillPromptAssembler = skillPromptAssembler;
        this.skillVersionRepository = skillVersionRepository;
        this.specCompilerService = specCompilerService;
        this.objectMapper = objectMapper;
        this.platformSkillTemplateRepository = platformSkillTemplateRepository;
        this.agentWorkflowSkillRefRepository = agentWorkflowSkillRefRepository;
        this.skillApiToolService = skillApiToolService;
        this.fileBackedBuiltinSkillSyncService = fileBackedBuiltinSkillSyncService;
    }

    @Transactional
    public void ensurePhaseOneDefaults(String orgId) {
        ensureBuiltinSkills(orgId);
        fileBackedBuiltinSkillSyncService.syncOrg(orgId);
        ensureDefaultBindings(orgId);
    }

    public List<SkillDefinitionEntity> listSkills(String orgId) {
        ensurePhaseOneDefaults(orgId);
        return skillDefinitionRepository.findByOrgIdOrderByBuiltinDescNameAsc(orgId).stream()
                .filter(SkillDefinitionEntity::isVisibleToTenant)
                .toList();
    }

    public SkillDefinitionEntity getSkill(String orgId, Long id) {
        ensurePhaseOneDefaults(orgId);
        return skillDefinitionRepository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found"));
    }

    public List<SkillDefinitionEntity> listSkillsForAgent(String orgId, String agentId) {
        ensurePhaseOneDefaults(orgId);
        List<AgentSkillBindingEntity> bindings = listBindingsInternal(orgId, agentId);
        List<Long> skillIds = bindings.stream().map(AgentSkillBindingEntity::getSkillId).toList();
        if (skillIds.isEmpty()) {
            return List.of();
        }
        Map<Long, SkillDefinitionEntity> byId = skillDefinitionRepository.findByOrgIdAndIdInAndEnabledTrue(orgId, skillIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        return skillIds.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .filter(SkillDefinitionEntity::isVisibleToTenant)
                .toList();
    }

    @Transactional
    public SkillDefinitionEntity createSkill(String orgId, UpsertCommand command) {
        ensurePhaseOneDefaults(orgId);
        String skillCode = normalizeSkillCode(command.skillCode());
        Optional<SkillDefinitionEntity> existing = skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, skillCode);
        if (existing.isPresent() && "DELETED".equals(existing.get().getLifecycleStatus())) {
            existing.get().archiveDeletedSkillCode();
            skillDefinitionRepository.saveAndFlush(existing.get());
        } else if (existing.isPresent()) {
            throw new IllegalArgumentException("Skill code already exists: " + skillCode);
        }
        SkillDefinitionEntity created = new SkillDefinitionEntity(
                orgId,
                skillCode,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                false,
                command.enabled() == null || command.enabled(),
                trimToNull(command.promptFragment()),
                resolveDraftSpecText(command.draftSpecText(), command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                normalizeRiskLevel(command.riskLevel()),
                SkillSourceType.TENANT_CUSTOM,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                null,
                null
        );
        created.setRuntimeApiDraftJson(skillApiToolService.serializeDraftApis(command.runtimeApis()));
        SkillDefinitionEntity saved = skillDefinitionRepository.save(created);
        SkillVersionEntity draft = createDraftVersion(orgId, saved, command, "CREATE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
    }

    @Transactional
    public SkillDefinitionEntity updateSkill(String orgId, Long id, UpsertCommand command) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        if (!entity.isVisibleToTenant()) {
            throw new IllegalArgumentException("Skill not found");
        }
        if (entity.isTenantConfigurable()) {
            entity.setEnabled(command.enabled() == null || command.enabled());
            return skillDefinitionRepository.save(entity);
        }
        if (!entity.isTenantEditable()) {
            throw new IllegalArgumentException("Skill is platform managed and cannot be edited");
        }

        String requestedCode = normalizeSkillCode(command.skillCode());
        if (!requestedCode.equals(entity.getSkillCode())
                && skillDefinitionRepository.existsByOrgIdAndSkillCodeAndIdNot(orgId, requestedCode, id)) {
            throw new IllegalArgumentException("Skill code already exists: " + requestedCode);
        }

        entity.update(
                requestedCode,
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                command.enabled() == null || command.enabled(),
                trimToNull(command.promptFragment()),
                resolveDraftSpecText(command.draftSpecText(), command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                skillApiToolService.serializeDraftApis(command.runtimeApis()),
                normalizeRiskLevel(command.riskLevel())
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(entity);
        SkillVersionEntity draft = createDraftVersion(orgId, saved, command, "SAVE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
    }

    @Transactional
    public DeleteImpact deleteImpact(String orgId, Long id) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        boolean bound = agentSkillBindingRepository.findByOrgIdAndSkillIdInAndEnabledTrue(orgId, List.of(id)).stream()
                .anyMatch(AgentSkillBindingEntity::isEnabled);
        boolean pinned = agentWorkflowSkillRefRepository.countActivePublishedRuntimeByOrgIdAndSkillId(orgId, id) > 0;
        List<String> blockers = new ArrayList<>();
        if (!entity.isTenantDeletable()) {
            blockers.add("仅租户自定义技能可以删除");
        }
        if (bound) {
            blockers.add("仍有 Agent 绑定该技能");
        }
        if (pinned) {
            blockers.add("仍有已发布运行时版本引用该技能");
        }
        return new DeleteImpact(entity.getId(), entity.getSkillCode(), entity.getName(), entity.getSourceType().name(),
                entity.getEditPolicy().name(), entity.isTenantDeletable() && blockers.isEmpty(), bound, pinned, blockers);
    }

    @Transactional
    public void deleteSkill(String orgId, Long id, String deletedBy, String reason) {
        DeleteImpact impact = deleteImpact(orgId, id);
        if (!impact.canDelete()) {
            throw new IllegalArgumentException("Skill cannot be deleted: " + String.join("; ", impact.blockers()));
        }
        SkillDefinitionEntity entity = getSkill(orgId, id);
        entity.markDeleted(fallback(trimToNull(deletedBy), "system"), trimToNull(reason));
        skillDefinitionRepository.save(entity);
    }

    @Transactional
    public SkillDefinitionEntity publishSkill(String orgId, Long id, PublishCommand command) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        if (!entity.isVisibleToTenant() || entity.getSourceType() != SkillSourceType.TENANT_CUSTOM || !entity.isTenantEditable()) {
            throw new IllegalArgumentException("Only tenant custom editable skills can be published");
        }
        PreviewResult preview = previewCompile(orgId, new PreviewCommand(
                entity.getSkillCode(),
                entity.getName(),
                entity.getDraftSpecText(),
                entity.getPromptFragment(),
                splitCsv(entity.getToolWhitelist()),
                splitCsv(entity.getKbWhitelist()),
                entity.getHandoffRule(),
                entity.getOutputContract(),
                skillApiToolService.readDraftApis(entity.getRuntimeApiDraftJson()),
                entity.getRiskLevel()
        ));
        if (preview.warnings().stream().anyMatch(item -> item.toLowerCase().contains("阻断"))) {
            throw new IllegalArgumentException("Skill has blocking compile warnings");
        }
        UpsertCommand snapshot = UpsertCommand.fromEntity(entity, command == null ? null : command.changeLog(),
                command == null ? null : command.actorUserId());
        SkillVersionEntity published = createDraftVersion(orgId, entity, snapshot, "PUBLISH", null);
        published.markPublished();
        skillVersionRepository.save(published);
        skillApiToolService.publishApisForVersion(orgId, entity, published, published.getRuntimeApiSnapshotJson());
        entity.markPublished(published.getId(), command == null ? "system" : fallback(trimToNull(command.actorUserId()), "system"));
        return skillDefinitionRepository.save(entity);
    }

    @Transactional
    public SkillDefinitionEntity restoreVersion(String orgId, Long id, Long versionId, RestoreCommand command) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        if (entity.getSourceType() != SkillSourceType.TENANT_CUSTOM || !entity.isTenantEditable()) {
            throw new IllegalArgumentException("Only tenant custom editable skills can restore versions");
        }
        SkillVersionEntity source = skillVersionRepository.findById(versionId)
                .filter(item -> orgId.equals(item.getOrgId()) && id.equals(item.getSkillId()))
                .filter(item -> Boolean.TRUE.equals(item.getRestoreVisible()))
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));
        entity.update(
                entity.getSkillCode(),
                entity.getName(),
                entity.getDescription(),
                entity.isEnabled(),
                source.getCompiledPromptFragment(),
                source.getSpecText(),
                source.getEffectiveToolWhitelist(),
                source.getEffectiveKbWhitelist(),
                entity.getHandoffRule(),
                entity.getOutputContract(),
                source.getRuntimeApiSnapshotJson(),
                source.getRiskLevel()
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(entity);
        UpsertCommand snapshot = UpsertCommand.fromEntity(saved,
                command == null ? "恢复自 v" + source.getVersionNo() : command.changeLog(),
                command == null ? null : command.actorUserId());
        SkillVersionEntity restored = createDraftVersion(orgId, saved, snapshot, "RESTORE", source.getId());
        saved.markDraft(restored.getId());
        return skillDefinitionRepository.save(saved);
    }

    public List<SkillVersionEntity> listRestoreVersions(String orgId, Long skillId, int limit) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity skill = getSkill(orgId, skillId);
        if (!skill.isVisibleToTenant()) {
            throw new IllegalArgumentException("Skill not found");
        }
        return skillVersionRepository.findByOrgIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(orgId, skillId).stream()
                .limit(Math.max(1, Math.min(limit, 20)))
                .toList();
    }

    @Transactional
    public SkillDefinitionEntity deriveSkill(String orgId, Long sourceSkillId, DeriveCommand command) {
        ensurePhaseOneDefaults(orgId);
        throw new IllegalArgumentException("Skill derivation is hidden in this release");
        /*
        SkillDefinitionEntity source = getSkill(orgId, sourceSkillId);
        if (!source.isVisibleToTenant() || source.getSourceType() != SkillSourceType.PLATFORM_STANDARD) {
            throw new IllegalArgumentException("Only platform standard skills can be derived");
        }
        String skillCode = normalizeSkillCode(command.skillCode());
        if (skillDefinitionRepository.existsByOrgIdAndSkillCode(orgId, skillCode)) {
            throw new IllegalArgumentException("Skill code already exists: " + skillCode);
        }
        Integer baseTemplateVersion = platformSkillTemplateRepository.findByOrgIdAndTemplateCode(
                        orgId,
                        fallback(source.getTemplateCode(), source.getSkillCode())
                )
                .map(template -> template.getCurrentVersionNo() == null ? 1 : template.getCurrentVersionNo())
                .or(() -> Optional.ofNullable(source.getCurrentPublishedVersionId())
                        .flatMap(skillVersionRepository::findById)
                        .map(SkillVersionEntity::getVersionNo))
                .or(() -> skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, source.getId())
                        .map(SkillVersionEntity::getVersionNo))
                .orElse(1);
        SkillDefinitionEntity derived = new SkillDefinitionEntity(
                orgId,
                skillCode,
                requireText(command.name(), "name"),
                trimToNull(fallback(command.description(), source.getDescription())),
                false,
                true,
                source.getPromptFragment(),
                source.getDraftSpecText(),
                source.getToolWhitelist(),
                source.getKbWhitelist(),
                source.getHandoffRule(),
                source.getOutputContract(),
                source.getRiskLevel(),
                SkillSourceType.TENANT_DERIVED,
                SkillVisibility.VISIBLE,
                SkillEditPolicy.EDITABLE,
                SkillBindingPolicy.OPTIONAL,
                SkillUpdatePolicy.MANUAL,
                fallback(source.getTemplateCode(), source.getSkillCode()),
                baseTemplateVersion
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(derived);
        SkillVersionEntity draft = createDraftVersion(orgId, saved, new UpsertCommand(
                saved.getSkillCode(),
                saved.getName(),
                saved.getDescription(),
                saved.isEnabled(),
                saved.getPromptFragment(),
                saved.getDraftSpecText(),
                splitCsv(saved.getToolWhitelist()),
                splitCsv(saved.getKbWhitelist()),
                saved.getHandoffRule(),
                saved.getOutputContract(),
                saved.getRiskLevel(),
                "derive",
                null,
                "derivedFrom=" + source.getSkillCode() + "@v" + baseTemplateVersion,
                "创建派生技能",
                "system"
        ), "DERIVE", null);
        saved.markDraft(draft.getId());
        return skillDefinitionRepository.save(saved);
        */
    }

    public List<AgentSkillBindingEntity> listBindings(String orgId, String agentId) {
        ensurePhaseOneDefaults(orgId);
        return listBindingsInternal(orgId, agentId);
    }

    @Transactional
    public List<AgentSkillBindingEntity> replaceBindings(String orgId, String requestedAgentId, List<BindingInput> inputs) {
        ensurePhaseOneDefaults(orgId);
        String agentId = normalizeAgentId(requestedAgentId);
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("bindings cannot be empty");
        }

        Map<Long, SkillDefinitionEntity> skillById = skillDefinitionRepository.findByOrgIdOrderByBuiltinDescNameAsc(orgId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        List<AgentSkillBindingEntity> next = new ArrayList<>();
        LinkedHashSet<Long> seenSkillIds = new LinkedHashSet<>();

        int fallbackPriority = 10;
        for (BindingInput input : inputs) {
            Long skillId = resolveSkillId(orgId, input, skillById);
            if (!seenSkillIds.add(skillId)) {
                throw new IllegalArgumentException("duplicate skill binding: " + skillId);
            }
            SkillDefinitionEntity skill = skillById.get(skillId);
            if (skill == null || !skill.isEnabled()) {
                throw new IllegalArgumentException("skill is not available: " + skillId);
            }
            int priority = input.priority() == null ? fallbackPriority : input.priority();
            fallbackPriority += 10;
            next.add(new AgentSkillBindingEntity(
                    orgId,
                    agentId,
                    skillId,
                    normalizeActivationMode(input.activationMode()),
                    trimToNull(input.activationCondition()),
                    priority,
                    input.enabled() == null || input.enabled()
            ));
        }

        agentSkillBindingRepository.deleteByOrgIdAndAgentId(orgId, agentId);
        agentSkillBindingRepository.flush();
        return agentSkillBindingRepository.saveAll(next);
    }

    public PreviewResult previewCompile(String orgId, PreviewCommand command) {
        ensurePhaseOneDefaults(orgId);
        String riskLevel = normalizeRiskLevel(command.riskLevel());
        List<String> tools = normalizeNameList(command.toolWhitelist());
        List<String> kbIds = normalizeNameList(command.kbWhitelist());
        String specText = resolveDraftSpecText(command.specText(), command.promptFragment());
        String promptFragment = trimToNull(command.promptFragment());
        String handoffRule = trimToNull(command.handoffRule());
        String outputContract = trimToNull(command.outputContract());
        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "skill-policy",
                fallback(command.name(), "Skill Preview"),
                specText,
                tools,
                kbIds,
                handoffRule,
                riskLevel
        ));
        List<String> warnings = new ArrayList<>(compiled.warnings());
        if (promptFragment == null || promptFragment.length() < 30) {
            warnings.add("promptFragment 偏短，建议补充触发条件与响应策略。");
        }

        SkillResolverService.ResolvedSkillContext context = new SkillResolverService.ResolvedSkillContext(
                "preview-agent",
                List.of(new SkillResolverService.ResolvedSkill(
                        normalizeSkillCode(command.skillCode()),
                        fallback(command.name(), "Skill Preview"),
                        promptFragment,
                        tools,
                        kbIds,
                        handoffRule,
                        outputContract,
                        riskLevel,
                        "always-on"
                )),
                List.of(normalizeSkillCode(command.skillCode())),
                tools,
                List.of(),
                tools,
                tools,
                kbIds,
                handoffRule == null ? List.of() : List.of(handoffRule),
                outputContract,
                null,
                null,
                normalizeSkillCode(command.skillCode()),
                null,
                null,
                List.of(new SkillResolverService.ResolvedSkillVersionRef(
                        normalizeSkillCode(command.skillCode()),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "always-on"
                )),
                List.of(),
                SkillResolverService.ResolvedPolicyBundle.EMPTY
        );
        String promptPreview = skillPromptAssembler.assemble(
                "You are CiCi assistant. Follow platform policy and answer safely.",
                context
        );
        List<String> compileSummary = new ArrayList<>(compiled.compileSummary());
        compileSummary.add("skillCode=" + normalizeSkillCode(command.skillCode()) + ", riskLevel=" + riskLevel);
        String runtimeApiJson = skillApiToolService.serializeDraftApis(command.runtimeApis());
        SkillApiToolService.RuntimeApiCompilePreview apiPreview =
                skillApiToolService.previewCompileApis(orgId, normalizeSkillCode(command.skillCode()), runtimeApiJson);
        warnings.addAll(apiPreview.warnings());
        apiPreview.errors().forEach(error -> warnings.add("阻断: " + error));
        compileSummary.add("runtimeApis=" + apiPreview.toolDefinitions().size());
        return new PreviewResult(
                promptPreview,
                tools,
                kbIds,
                riskLevel,
                warnings,
                compileSummary,
                toPolicyJson(compiled.specIr()),
                apiPreview
        );
    }

    public String normalizeAgentId(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return "cici-system";
        }
        String trimmed = agentId.trim();
        if ("cici".equalsIgnoreCase(trimmed) || "cici-default".equalsIgnoreCase(trimmed)) {
            return "cici-system";
        }
        return trimmed;
    }

    private List<AgentSkillBindingEntity> listBindingsInternal(String orgId, String agentId) {
        return agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                orgId, normalizeAgentId(agentId)
        );
    }

    private Long resolveSkillId(String orgId, BindingInput input, Map<Long, SkillDefinitionEntity> skillById) {
        if (input.skillId() != null) {
            return input.skillId();
        }
        if (input.skillCode() == null || input.skillCode().isBlank()) {
            throw new IllegalArgumentException("skillId or skillCode is required");
        }
        String code = normalizeSkillCode(input.skillCode());
        return skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, code)
                .map(SkillDefinitionEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("Skill not found for code: " + code));
    }

    private String normalizeActivationMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "always-on";
        }
        String normalized = mode.trim().toLowerCase();
        if (!List.of("always-on", "intent-route", "manual").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported activationMode: " + mode);
        }
        return normalized;
    }

    private String normalizeSkillCode(String code) {
        String normalized = fallback(code, "").trim().toLowerCase();
        if (!normalized.matches("[a-z0-9][a-z0-9-_]{1,63}")) {
            throw new IllegalArgumentException("Invalid skillCode format");
        }
        return normalized;
    }

    private String normalizeRiskLevel(String riskLevel) {
        String normalized = fallback(riskLevel, "MEDIUM").trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported riskLevel: " + riskLevel);
        }
        return normalized;
    }

    private List<String> normalizeNameList(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String item : names) {
            String cleaned = trimToNull(item);
            if (cleaned != null) {
                values.add(cleaned);
            }
        }
        return List.copyOf(values);
    }

    private String joinCsv(List<String> names) {
        List<String> normalized = normalizeNameList(names);
        return normalized.isEmpty() ? null : String.join(",", normalized);
    }

    private String requireText(String value, String field) {
        String cleaned = trimToNull(value);
        if (cleaned == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return cleaned;
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private String resolveDraftSpecText(String draftSpecText, String promptFragment) {
        String draft = trimToNull(draftSpecText);
        if (draft != null) {
            return draft;
        }
        return trimToNull(promptFragment);
    }

    private SkillVersionEntity createDraftVersion(String orgId,
                                                  SkillDefinitionEntity skill,
                                                  UpsertCommand command,
                                                  String versionSource,
                                                  Long restoredFromVersionId) {
        Integer nextVersionNo = skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, skill.getId())
                .map(existing -> existing.getVersionNo() + 1)
                .orElse(1);
        List<String> tools = normalizeNameList(command.toolWhitelist());
        List<String> kbIds = normalizeNameList(command.kbWhitelist());
        String riskLevel = normalizeRiskLevel(command.riskLevel());
        String specText = resolveDraftSpecText(command.draftSpecText(), command.promptFragment());
        SpecCompilerService.SpecCompilation compiled = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "skill-policy",
                skill.getName(),
                specText,
                tools,
                kbIds,
                command.handoffRule(),
                riskLevel
        ));
        SkillVersionEntity saved = skillVersionRepository.save(new SkillVersionEntity(
                orgId,
                skill.getId(),
                nextVersionNo,
                specText,
                "policy",
                fallback(trimToNull(command.sourceType()), "manual"),
                trimToNull(command.specIrJson()),
                trimToNull(command.authoringNotes()),
                trimToNull(command.promptFragment()),
                toPolicyJson(compiled.specIr()),
                joinCsv(tools),
                joinCsv(kbIds),
                riskLevel,
                String.join("\n", compiled.compileSummary()),
                String.join("\n", compiled.warnings()),
                "DRAFT"
        ));
        saved.setRuntimeApiSnapshotJson(skill.getRuntimeApiDraftJson());
        saved.applyGovernance(
                fallback(trimToNull(command.changeLog()), defaultChangeLog(versionSource)),
                String.join("\n", buildDiffSummary(skill, tools, kbIds, riskLevel, versionSource)),
                versionSource,
                fallback(trimToNull(command.actorUserId()), "system"),
                true,
                "ACTIVE_RECENT",
                restoredFromVersionId,
                null
        );
        SkillVersionEntity version = skillVersionRepository.save(saved);
        pruneRestoreHistory(orgId, skill.getId());
        return version;
    }

    private void pruneRestoreHistory(String orgId, Long skillId) {
        List<SkillVersionEntity> versions = skillVersionRepository
                .findByOrgIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(orgId, skillId);
        for (int i = 3; i < versions.size(); i++) {
            SkillVersionEntity version = versions.get(i);
            boolean protectedRuntime = agentWorkflowSkillRefRepository.existsByOrgIdAndSkillVersionId(orgId, version.getId());
            version.markRetention(protectedRuntime ? "PROTECTED_RUNTIME" : "PRUNED", false);
            skillVersionRepository.save(version);
        }
    }

    private List<String> buildDiffSummary(SkillDefinitionEntity skill,
                                          List<String> tools,
                                          List<String> kbIds,
                                          String riskLevel,
                                          String versionSource) {
        List<String> summary = new ArrayList<>();
        summary.add("来源动作：" + versionSource);
        summary.add("工具白名单：" + tools.size() + " 项");
        summary.add("知识库白名单：" + kbIds.size() + " 项");
        summary.add("风险等级：" + riskLevel);
        summary.add("技能：" + skill.getSkillCode());
        return summary;
    }

    private String defaultChangeLog(String versionSource) {
        return switch (fallback(versionSource, "SAVE")) {
            case "CREATE" -> "创建技能草稿";
            case "PUBLISH" -> "发布技能版本";
            case "RESTORE" -> "恢复历史版本";
            case "IMPORT" -> "导入技能包";
            default -> "保存技能配置";
        };
    }

    private String toPolicyJson(SpecCompilerService.SpecIr specIr) {
        try {
            return objectMapper.writeValueAsString(specIr);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void ensureBuiltinSkills(String orgId) {
        for (BuiltinSkillSpec spec : BUILTIN_SKILLS) {
            Optional<SkillDefinitionEntity> existing = skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, spec.skillCode());
            if (existing.isPresent()) {
                continue;
            }
            skillDefinitionRepository.save(new SkillDefinitionEntity(
                    orgId,
                    spec.skillCode(),
                    spec.name(),
                    spec.description(),
                    true,
                    true,
                    spec.promptFragment(),
                    spec.promptFragment(),
                    spec.toolWhitelist(),
                    spec.kbWhitelist(),
                    spec.handoffRule(),
                    spec.outputContract(),
                    spec.riskLevel(),
                    SkillSourceType.PLATFORM_STANDARD,
                    visibilityForBuiltin(spec.skillCode()),
                    editPolicyForBuiltin(spec.skillCode()),
                    bindingPolicyForBuiltin(spec.skillCode()),
                    SkillUpdatePolicy.AUTO,
                    spec.skillCode(),
                    null
            ));
        }
    }

    private SkillVisibility visibilityForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillVisibility.HIDDEN : SkillVisibility.VISIBLE;
    }

    private SkillEditPolicy editPolicyForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillEditPolicy.LOCKED : SkillEditPolicy.CONFIGURABLE;
    }

    private SkillBindingPolicy bindingPolicyForBuiltin(String skillCode) {
        return CORE_POLICY_CODES.contains(skillCode) ? SkillBindingPolicy.MANDATORY : SkillBindingPolicy.OPTIONAL;
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private void ensureDefaultBindings(String orgId) {
        Map<String, SkillDefinitionEntity> skillByCode = new LinkedHashMap<>();
        for (SkillDefinitionEntity entity : skillDefinitionRepository.findByOrgIdAndEnabledTrueOrderByBuiltinDescNameAsc(orgId)) {
            skillByCode.put(entity.getSkillCode(), entity);
        }
        for (Map.Entry<String, List<DefaultBinding>> entry : DEFAULT_AGENT_SKILLS.entrySet()) {
            String agentId = entry.getKey();
            List<DefaultBinding> bindings = entry.getValue();
            for (int index = 0; index < bindings.size(); index++) {
                DefaultBinding binding = bindings.get(index);
                SkillDefinitionEntity skill = skillByCode.get(binding.skillCode());
                if (skill == null) {
                    continue;
                }
                if (agentSkillBindingRepository.existsByOrgIdAndAgentIdAndSkillId(orgId, agentId, skill.getId())) {
                    continue;
                }
                agentSkillBindingRepository.save(new AgentSkillBindingEntity(
                        orgId,
                        agentId,
                        skill.getId(),
                        binding.activationMode(),
                        null,
                        (index + 1) * 10,
                        true
                ));
            }
        }
    }

    public record UpsertCommand(
            String skillCode,
            String name,
            String description,
            Boolean enabled,
            String promptFragment,
            String draftSpecText,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            List<Map<String, Object>> runtimeApis,
            String riskLevel,
            String sourceType,
            String specIrJson,
            String authoringNotes,
            String changeLog,
            String actorUserId
    ) {
        static UpsertCommand fromEntity(SkillDefinitionEntity entity, String changeLog, String actorUserId) {
            return new UpsertCommand(
                    entity.getSkillCode(),
                    entity.getName(),
                    entity.getDescription(),
                    entity.isEnabled(),
                    entity.getPromptFragment(),
                    entity.getDraftSpecText(),
                    entity.getToolWhitelist() == null ? List.of() : java.util.Arrays.stream(entity.getToolWhitelist().split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(),
                    entity.getKbWhitelist() == null ? List.of() : java.util.Arrays.stream(entity.getKbWhitelist().split(",")).map(String::trim).filter(item -> !item.isBlank()).toList(),
                    entity.getHandoffRule(),
                    entity.getOutputContract(),
                    List.of(),
                    entity.getRiskLevel(),
                    "manual",
                    null,
                    null,
                    changeLog,
                    actorUserId
            );
        }
    }

    public record BindingInput(
            Long skillId,
            String skillCode,
            String activationMode,
            String activationCondition,
            Integer priority,
            Boolean enabled
    ) {
    }

    public record PreviewCommand(
            String skillCode,
            String name,
            String specText,
            String promptFragment,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            List<Map<String, Object>> runtimeApis,
            String riskLevel
    ) {
    }

    public record PreviewResult(
            String promptPreview,
            List<String> effectiveToolNames,
            List<String> effectiveKnowledgeBaseIds,
            String riskLevel,
            List<String> warnings,
            List<String> compileSummary,
            String specIr,
            SkillApiToolService.RuntimeApiCompilePreview runtimeApiPreview
    ) {
    }

    public record DeriveCommand(
            String skillCode,
            String name,
            String description
    ) {
    }

    public record PublishCommand(String changeLog, String actorUserId) {
    }

    public record RestoreCommand(String changeLog, String actorUserId) {
    }

    public record DeleteImpact(
            Long skillId,
            String skillCode,
            String name,
            String sourceType,
            String editPolicy,
            boolean canDelete,
            boolean hasActiveBindings,
            boolean hasRuntimePins,
            List<String> blockers
    ) {
    }

    private record BuiltinSkillSpec(
            String skillCode,
            String name,
            String description,
            String promptFragment,
            String toolWhitelist,
            String kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel
    ) {
    }
}
