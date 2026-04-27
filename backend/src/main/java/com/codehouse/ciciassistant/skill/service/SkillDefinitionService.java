package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillDefinitionService {

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

    public SkillDefinitionService(SkillDefinitionRepository skillDefinitionRepository,
                                  AgentSkillBindingRepository agentSkillBindingRepository,
                                  SkillPromptAssembler skillPromptAssembler,
                                  SkillVersionRepository skillVersionRepository,
                                  SpecCompilerService specCompilerService,
                                  ObjectMapper objectMapper) {
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillPromptAssembler = skillPromptAssembler;
        this.skillVersionRepository = skillVersionRepository;
        this.specCompilerService = specCompilerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void ensurePhaseOneDefaults(String orgId) {
        ensureBuiltinSkills(orgId);
        ensureDefaultBindings(orgId);
    }

    public List<SkillDefinitionEntity> listSkills(String orgId) {
        ensurePhaseOneDefaults(orgId);
        return skillDefinitionRepository.findByOrgIdOrderByBuiltinDescNameAsc(orgId);
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
                .toList();
    }

    @Transactional
    public SkillDefinitionEntity createSkill(String orgId, UpsertCommand command) {
        ensurePhaseOneDefaults(orgId);
        String skillCode = normalizeSkillCode(command.skillCode());
        if (skillDefinitionRepository.existsByOrgIdAndSkillCode(orgId, skillCode)) {
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
                normalizeRiskLevel(command.riskLevel())
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(created);
        createDraftVersion(orgId, saved, command);
        return saved;
    }

    @Transactional
    public SkillDefinitionEntity updateSkill(String orgId, Long id, UpsertCommand command) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        if (entity.isBuiltin()) {
            // Built-in skills are platform maintained: org admins can only toggle enabled.
            entity.setEnabled(command.enabled() == null || command.enabled());
            return skillDefinitionRepository.save(entity);
        }

        String requestedCode = normalizeSkillCode(command.skillCode());
        if (!requestedCode.equals(entity.getSkillCode())
                && skillDefinitionRepository.existsByOrgIdAndSkillCodeAndIdNot(orgId, requestedCode, id)) {
            throw new IllegalArgumentException("Skill code already exists: " + requestedCode);
        }

        entity.update(
                requireText(command.name(), "name"),
                trimToNull(command.description()),
                command.enabled() == null || command.enabled(),
                trimToNull(command.promptFragment()),
                resolveDraftSpecText(command.draftSpecText(), command.promptFragment()),
                joinCsv(command.toolWhitelist()),
                joinCsv(command.kbWhitelist()),
                trimToNull(command.handoffRule()),
                trimToNull(command.outputContract()),
                normalizeRiskLevel(command.riskLevel())
        );
        SkillDefinitionEntity saved = skillDefinitionRepository.save(entity);
        createDraftVersion(orgId, saved, command);
        return saved;
    }

    @Transactional
    public void deleteSkill(String orgId, Long id) {
        ensurePhaseOneDefaults(orgId);
        SkillDefinitionEntity entity = getSkill(orgId, id);
        if (entity.isBuiltin()) {
            throw new IllegalArgumentException("Built-in skills cannot be deleted");
        }
        entity.setEnabled(false);
        skillDefinitionRepository.save(entity);
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
                        riskLevel
                )),
                List.of(normalizeSkillCode(command.skillCode())),
                tools,
                kbIds,
                handoffRule == null ? List.of() : List.of(handoffRule),
                outputContract,
                null,
                null,
                null,
                null
        );
        String promptPreview = skillPromptAssembler.assemble(
                "You are CiCi assistant. Follow platform policy and answer safely.",
                context
        );
        List<String> compileSummary = new ArrayList<>(compiled.compileSummary());
        compileSummary.add("skillCode=" + normalizeSkillCode(command.skillCode()) + ", riskLevel=" + riskLevel);
        return new PreviewResult(
                promptPreview,
                tools,
                kbIds,
                riskLevel,
                warnings,
                compileSummary,
                toPolicyJson(compiled.specIr())
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

    private void createDraftVersion(String orgId, SkillDefinitionEntity skill, UpsertCommand command) {
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
        skillVersionRepository.save(new SkillVersionEntity(
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
                    spec.riskLevel()
            ));
        }
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
            String riskLevel,
            String sourceType,
            String specIrJson,
            String authoringNotes
    ) {
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
            String specIr
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
