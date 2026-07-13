package com.codehouse.ciciassistant.agent.service;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillVersionRepository;
import com.codehouse.ciciassistant.spec.SpecCompilerService;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentCompileService {

    private static final Map<String, ToolProfile> DEFAULT_TOOL_CATALOG = Map.of(
            "rag-search", new ToolProfile("rag-search", "企业知识检索", "从已授权知识库检索答案片段。", "低风险"),
            "cloudcc_pageQuery", new ToolProfile("cloudcc_pageQuery", "客户档案查询", "查询客户基础信息、负责人和跟进状态。", "中风险"),
            "quote-generator", new ToolProfile("quote-generator", "报价单生成", "根据商品与折扣策略生成标准报价单。", "中风险"),
            ToolNameNormalizer.GET_PENDING_APPROVALS,
            new ToolProfile(ToolNameNormalizer.GET_PENDING_APPROVALS, "审批待办拉取", "读取 CloudCC / OA 当前待审批项目。", "中风险"),
            "mcp-workflow", new ToolProfile("mcp-workflow", "MCP 工作流", "调用外部 MCP 工具与自动化流程。", "高风险"));

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ToolDefinitionRepository toolDefinitionRepository;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentCapabilityResolverService agentCapabilityResolverService;
    private final AgentDefinitionRepository agentDefinitionRepository;
    private final AgentWorkflowVersionRepository agentWorkflowVersionRepository;
    private final AgentSkillBindingRepository agentSkillBindingRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SpecCompilerService specCompilerService;
    private final ObjectMapper objectMapper;

    public AgentCompileService(KnowledgeBaseRepository knowledgeBaseRepository,
                               ToolDefinitionRepository toolDefinitionRepository,
                               AgentDefinitionService agentDefinitionService,
                               AgentCapabilityResolverService agentCapabilityResolverService,
                               AgentDefinitionRepository agentDefinitionRepository,
                               AgentWorkflowVersionRepository agentWorkflowVersionRepository,
                               AgentSkillBindingRepository agentSkillBindingRepository,
                               SkillDefinitionRepository skillDefinitionRepository,
                               SkillVersionRepository skillVersionRepository,
                               SpecCompilerService specCompilerService,
                               ObjectMapper objectMapper) {
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.agentDefinitionService = agentDefinitionService;
        this.agentCapabilityResolverService = agentCapabilityResolverService;
        this.agentDefinitionRepository = agentDefinitionRepository;
        this.agentWorkflowVersionRepository = agentWorkflowVersionRepository;
        this.agentSkillBindingRepository = agentSkillBindingRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.specCompilerService = specCompilerService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CompileResult compile(String orgId, CompileCommand command) {
        agentDefinitionService.warmupBuiltinAgents(orgId);
        List<Long> knowledgeBaseIds = normalizeList(command.knowledgeBaseIds());
        List<String> toolIds = normalizeList(command.toolIds());
        List<String> channels = normalizeList(command.channels());

        List<ResolvedSkillRef> resolvedSkillRefs = resolveSkillRefs(orgId, command.agentId(), command.skillRefs());
        List<String> resolvedSkillCodes = resolvedSkillRefs.stream()
                .filter(ResolvedSkillRef::resolved)
                .map(ResolvedSkillRef::skillCode)
                .toList();
        AgentCapabilityResolverService.AgentCapabilityResolution capability = agentCapabilityResolverService.resolve(
                orgId,
                command.agentId(),
                command.skillRefs()
        );
        List<String> effectiveToolIds = capability.effectiveToolNames();
        List<Long> effectiveKnowledgeBaseIds = capability.effectiveKnowledgeBaseIds();
        List<KnowledgeBaseEntity> selectedKbs = effectiveKnowledgeBaseIds.stream()
                .map(id -> knowledgeBaseRepository.findByIdAndOrgId(id, orgId))
                .flatMap(Optional::stream)
                .toList();
        List<ToolProfile> selectedTools = effectiveToolIds.stream()
                .map(toolId -> resolveTool(orgId, toolId))
                .toList();
        List<String> kbNames = selectedKbs.stream().map(KnowledgeBaseEntity::getName).toList();
        List<String> toolNames = selectedTools.stream().map(ToolProfile::name).toList();
        SpecCompilerService.SpecCompilation compiledSpec = specCompilerService.compile(new SpecCompilerService.SpecCompileCommand(
                "agent-workflow",
                safeText(command.name()),
                command.specText(),
                toolIds,
                kbNames,
                command.handoffRule(),
                normalizeRiskLevel(command.safetyLevel())
        ));

        List<String> warnings = new ArrayList<>(compiledSpec.warnings());
        if ("auto".equals(command.executionMode())) {
            warnings.add("当前为自动执行模式，发布前应补充更多测试样例。");
        }
        if (selectedTools.stream().anyMatch(tool -> "高风险".equals(tool.riskLevel()))) {
            warnings.add("已启用高风险工具，建议强制人工确认后再正式发布。");
        }
        if (kbNames.isEmpty()) {
            warnings.add("尚未绑定知识库，生成结果会缺少企业知识上下文。");
        }
        warnings.addAll(capability.warnings());

        List<String> dependencies = new ArrayList<>();
        dependencies.add("model:" + safeText(command.model()));
        kbNames.forEach(name -> dependencies.add("kb:" + name));
        toolNames.forEach(name -> dependencies.add("tool:" + name));
        resolvedSkillCodes.forEach(code -> dependencies.add("skill:" + code));

        List<String> summary = new ArrayList<>(compiledSpec.compileSummary());
        summary.add("角色定义为「" + safeText(command.name()) + "」，主场景是 " + fallback(command.summary(), "未填写业务定位") + "。");
        summary.add("编译器将优先使用 " + safeText(command.model()) + "，并采用 "
                + ("auto".equals(command.executionMode()) ? "自动执行" : "协作副驾") + " 策略。");
        summary.add(kbNames.isEmpty()
                ? "当前未绑定知识库，运行时不会启用 RAG 检索。"
                : "知识检索范围锁定为：" + String.join("、", kbNames) + "。");
        summary.add(toolNames.isEmpty()
                ? "当前未启用业务工具，只能做文本理解与总结。"
                : "可调用工具白名单为：" + String.join("、", toolNames) + "。");
        summary.add(resolvedSkillCodes.isEmpty()
                ? "当前未解析到显式 skill 依赖。"
                : "已解析 skill 依赖：" + String.join("、", resolvedSkillCodes) + "。");

        WorkflowPreview preview = generatePreview(command, kbNames, selectedTools);
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("entry", "runAgent");
        manifest.put("runtimeLang", "typescript-sandbox");
        manifest.put("identity", Map.of(
                "name", safeText(command.name()),
                "summary", safeText(command.summary()),
                "model", safeText(command.model()),
                "systemPrompt", safeText(command.systemPrompt())
        ));
        manifest.put("dependencies", Map.of(
                "model", safeText(command.model()),
                "tools", effectiveToolIds,
                "knowledgeBases", effectiveKnowledgeBaseIds,
                "channels", channels,
                "skills", resolvedSkillCodes
        ));
        manifest.put("policies", Map.of(
                "safetyLevel", safeText(command.safetyLevel()),
                "executionMode", safeText(command.executionMode()),
                "handoffRule", safeText(command.handoffRule()),
                "maxToolCalls", "auto".equals(command.executionMode()) ? 4 : 2
        ));
        manifest.put("generatedFrom", Map.of(
                "version", safeText(command.version()),
                "specLength", safeText(command.specText()).length(),
                "resolvedSkillRefs", resolvedSkillRefs,
                "specIr", compiledSpec.specIr()
        ));
        String compileFingerprint = buildCompileFingerprint(
                command,
                channels,
                effectiveKnowledgeBaseIds,
                effectiveToolIds,
                resolvedSkillCodes);
        manifest.put("compileFingerprint", compileFingerprint);
        manifest.put("previewFormat", preview.format());

        PersistResult persisted = persistDraftVersion(
                orgId,
                command,
                manifest,
                preview,
                summary,
                warnings,
                dependencies,
                compileFingerprint);

        return new CompileResult(
                buildWorkflowCode(command),
                manifest,
                preview,
                summary,
                warnings,
                dependencies,
                resolvedSkillRefs,
                persisted.versionNo(),
                persisted.changed(),
                persisted.message(),
                persisted.changeLog()
        );
    }

    private PersistResult persistDraftVersion(String orgId,
                                              CompileCommand command,
                                              Map<String, Object> manifest,
                                              WorkflowPreview preview,
                                              List<String> summary,
                                              List<String> warnings,
                                              List<String> dependencies,
                                              String compileFingerprint) {
        String agentId = safeText(command.agentId()).trim().toLowerCase();
        if (agentId.isBlank()) {
            return new PersistResult(null, true, "编译完成。", List.of());
        }
        Optional<AgentDefinitionEntity> agent = agentDefinitionRepository.findByOrgIdAndAgentId(orgId, agentId);
        if (agent.isEmpty()) {
            return new PersistResult(null, true, "编译完成。", List.of());
        }
        Optional<AgentWorkflowVersionEntity> previous = agentWorkflowVersionRepository
                .findTopByOrgIdAndAgentIdOrderByVersionNoDesc(orgId, agentId);
        if (previous.isPresent() && compileFingerprint.equals(safeText(previous.get().getCompileFingerprint()))) {
            return new PersistResult(
                    previous.get().getVersionNo(),
                    false,
                    "未检测到可发布变更，本次不新增版本。",
                    List.of("无变更：当前草稿与最近编译版本一致。"));
        }
        Integer nextVersionNo = previous.map(item -> item.getVersionNo() + 1).orElse(1);
        String workflowCode = buildWorkflowCode(command);
        List<String> changeLog = previous
                .map(item -> buildChangeLog(item, command, compileFingerprint))
                .orElse(List.of("首次编译：创建初始版本。"));
        AgentWorkflowVersionEntity created = new AgentWorkflowVersionEntity(
                orgId,
                agentId,
                nextVersionNo,
                safeText(command.version()).trim().isBlank() ? null : safeText(command.version()).trim(),
                command.specText(),
                workflowCode,
                toJson(manifest),
                toJson(preview),
                toJson(summary),
                toJson(warnings),
                toJson(dependencies),
                compileFingerprint,
                toJson(changeLog),
                "DRAFT"
        );
        agentWorkflowVersionRepository.save(created);
        return new PersistResult(nextVersionNo, true, "编译完成并生成新版本。", changeLog);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize compile artifacts", e);
        }
    }

    private String buildCompileFingerprint(CompileCommand command,
                                           List<String> channels,
                                           List<Long> effectiveKnowledgeBaseIds,
                                           List<String> effectiveToolIds,
                                           List<String> resolvedSkillCodes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentId", safeText(command.agentId()).trim().toLowerCase());
        payload.put("name", safeText(command.name()));
        payload.put("summary", safeText(command.summary()));
        payload.put("greeting", safeText(command.greeting()));
        payload.put("model", safeText(command.model()));
        payload.put("systemPrompt", safeText(command.systemPrompt()));
        payload.put("specText", safeText(command.specText()));
        payload.put("channels", sortStrings(channels));
        payload.put("knowledgeBaseIds", sortLongs(effectiveKnowledgeBaseIds));
        payload.put("toolIds", sortStrings(effectiveToolIds));
        payload.put("skillRefs", sortStrings(resolvedSkillCodes));
        payload.put("handoffRule", safeText(command.handoffRule()));
        payload.put("safetyLevel", normalizeRiskLevel(command.safetyLevel()));
        payload.put("executionMode", safeText(command.executionMode()));
        payload.put("versionLabel", safeText(command.version()));
        return sha256Hex(toJson(payload));
    }

    private List<String> buildChangeLog(AgentWorkflowVersionEntity previous,
                                        CompileCommand command,
                                        String currentFingerprint) {
        List<String> changes = new ArrayList<>();
        if (!safeText(previous.getSpecText()).equals(safeText(command.specText()))) {
            changes.add("Spec 文本发生变化。");
        }
        if (!safeText(previous.getVersionLabel()).equals(safeText(command.version()).trim())) {
            changes.add("发布备注发生变化。");
        }
        if (!safeText(previous.getCompileFingerprint()).equals(currentFingerprint)) {
            changes.add("编译指纹变化：依赖/策略或流程内容有更新。");
        }
        if (changes.isEmpty()) {
            changes.add("检测到编译输入变化，已生成新版本。");
        }
        return changes;
    }

    private WorkflowPreview generatePreview(CompileCommand command,
                                            List<String> kbNames,
                                            List<ToolProfile> selectedTools) {
        List<ToolProfile> businessTools = selectedTools.stream()
                .filter(tool -> !"rag-search".equals(tool.id()))
                .toList();
        boolean hasKnowledge = !kbNames.isEmpty() || selectedTools.stream().anyMatch(tool -> "rag-search".equals(tool.id()));
        List<WorkflowNode> nodes = new ArrayList<>();
        List<WorkflowEdge> edges = new ArrayList<>();

        nodes.add(new WorkflowNode("input", "接收用户输入", "来自会话、IM 或门户渠道。", "start"));
        nodes.add(new WorkflowNode("intent", "识别意图", "根据 Spec 判断是问答、查询还是执行请求。", "decision"));
        edges.add(new WorkflowEdge("input", "intent", null));

        if (hasKnowledge) {
            nodes.add(new WorkflowNode(
                    "knowledge",
                    "知识检索",
                    kbNames.isEmpty() ? "使用已授权知识上下文" : "检索 " + String.join("、", kbNames),
                    "knowledge"));
            edges.add(new WorkflowEdge("intent", "knowledge", "知识问答"));
        }

        if (!businessTools.isEmpty()) {
            nodes.add(new WorkflowNode(
                    "tooling",
                    businessTools.size() > 1 ? "工具编排 (" + businessTools.size() + ")" : businessTools.get(0).name(),
                    businessTools.stream().map(ToolProfile::name).reduce((a, b) -> a + "、" + b).orElse("工具调用"),
                    "tool"));
            edges.add(new WorkflowEdge("intent", "tooling", "查询 / 动作"));
        }

        nodes.add(new WorkflowNode("compose", "生成回复", "按“结论 / 依据 / 下一步建议”输出。", "generate"));
        if (hasKnowledge) {
            edges.add(new WorkflowEdge("knowledge", "compose", "命中充分"));
        }
        if (!businessTools.isEmpty()) {
            edges.add(new WorkflowEdge("tooling", "compose", "结果可用"));
        }
        if (!hasKnowledge && businessTools.isEmpty()) {
            edges.add(new WorkflowEdge("intent", "compose", "直接生成"));
        }

        boolean needsHandoff = !safeText(command.handoffRule()).isBlank();
        if (needsHandoff) {
            nodes.add(new WorkflowNode("handoff", "人工兜底", safeText(command.handoffRule()), "handoff"));
            if (hasKnowledge) {
                edges.add(new WorkflowEdge("knowledge", "handoff", "低置信"));
            }
            if (!businessTools.isEmpty()) {
                edges.add(new WorkflowEdge("tooling", "handoff", "高风险 / 异常"));
            }
            if (!hasKnowledge && businessTools.isEmpty()) {
                edges.add(new WorkflowEdge("intent", "handoff", "需人工确认"));
            }
        }

        boolean autoMode = "auto".equals(command.executionMode());
        nodes.add(new WorkflowNode(
                "output",
                autoMode ? "输出并执行" : "输出建议",
                autoMode ? "自动模式可继续触发后续动作。" : "协作模式下由人工决定是否执行。",
                "output"));
        edges.add(new WorkflowEdge("compose", "output", null));
        if (needsHandoff) {
            edges.add(new WorkflowEdge("handoff", "output", "人工接管"));
        }

        return new WorkflowPreview("mermaid", buildMermaidDiagram(nodes, edges), nodes, edges);
    }

    private String buildMermaidDiagram(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        List<String> lines = new ArrayList<>();
        lines.add("flowchart TD");
        nodes.forEach(node -> lines.add("  " + nodeExpression(node)));
        edges.forEach(edge -> lines.add("  " + edge.from() + " -->"
                + (edge.label() == null || edge.label().isBlank() ? "" : "|" + escapeMermaid(edge.label()) + "|")
                + " " + edge.to()));
        lines.add("  classDef start fill:#1d4ed8,stroke:#1e3a8a,color:#ffffff,stroke-width:1.4px;");
        lines.add("  classDef decision fill:#fff7d6,stroke:#d4a72c,color:#5b4300,stroke-width:1.4px;");
        lines.add("  classDef knowledge fill:#e6f4ef,stroke:#1e9b72,color:#0d4f3a,stroke-width:1.4px;");
        lines.add("  classDef tool fill:#edf2ff,stroke:#5b7ff4,color:#2742a4,stroke-width:1.4px;");
        lines.add("  classDef generate fill:#f4ebff,stroke:#8a55d8,color:#4d217f,stroke-width:1.4px;");
        lines.add("  classDef handoff fill:#fff1f2,stroke:#df4f67,color:#8f1830,stroke-width:1.4px;");
        lines.add("  classDef output fill:#eef2f7,stroke:#64748b,color:#1f2937,stroke-width:1.4px;");
        nodes.forEach(node -> lines.add("  class " + node.id() + " " + node.kind() + ";"));
        return String.join("\n", lines);
    }

    private String nodeExpression(WorkflowNode node) {
        String label = escapeMermaid(node.label());
        return switch (node.kind()) {
            case "start", "output" -> node.id() + "([\"" + label + "\"])";
            case "decision" -> node.id() + "{\"" + label + "\"}";
            case "tool" -> node.id() + "[[\"" + label + "\"]]";
            default -> node.id() + "[\"" + label + "\"]";
        };
    }

    private String buildWorkflowCode(CompileCommand command) {
        List<String> lines = List.of(
                "export async function runAgent(ctx: WorkflowContext): Promise<WorkflowResult> {",
                "  const spec = " + quote(command.specText()) + ";",
                "  const role = " + quote(command.name()) + ";",
                "  const knowledgeBases = " + listLiteral(normalizeList(command.knowledgeBaseIds())) + ";",
                "  const allowedTools = " + listLiteral(normalizeList(command.toolIds())) + ";",
                "",
                "  const intent = await ctx.model.classify({",
                "    role,",
                "    input: ctx.input,",
                "    spec,",
                "  });",
                "",
                "  const knowledge = knowledgeBases.length > 0",
                "    ? await ctx.knowledge.search({ input: ctx.input, knowledgeBaseIds: knowledgeBases })",
                "    : null;",
                "",
                "  if (knowledge && knowledge.confidence < 0.7) {",
                "    return ctx.handoff.request({ reason: " + quote(command.handoffRule()) + " });",
                "  }",
                "",
                "  const toolResult = intent.requiresTool && allowedTools.length > 0",
                "    ? await ctx.tools.invokeBest({ intent, allowedTools, input: ctx.input })",
                "    : null;",
                "",
                "  return ctx.model.generate({",
                "    role,",
                "    input: ctx.input,",
                "    systemPrompt: ctx.policy.systemPrompt,",
                "    knowledge,",
                "    toolResult,",
                "    outputTemplate: '结论 / 依据 / 下一步建议',",
                "  });",
                "}"
        );
        return String.join("\n", lines);
    }

    private ToolProfile resolveTool(String orgId, String toolId) {
        if (DEFAULT_TOOL_CATALOG.containsKey(toolId)) {
            ToolProfile profile = DEFAULT_TOOL_CATALOG.get(toolId);
            return new ToolProfile(toolId, profile.name(), profile.description(), profile.riskLevel());
        }
        Optional<ToolDefinitionEntity> customTool = toolDefinitionRepository.findByOrgIdAndToolName(orgId, toolId);
        if (customTool.isPresent()) {
            ToolDefinitionEntity entity = customTool.get();
            return new ToolProfile(entity.getToolName(), entity.getToolName(), entity.getDescription(), entity.getRiskLevel());
        }
        return new ToolProfile(toolId, toolId, "未注册工具", "未知");
    }

    private String listLiteral(List<?> values) {
        return values.stream()
                .map(this::quoteLiteral)
                .reduce((left, right) -> left + ", " + right)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");
    }

    private String quoteLiteral(Object value) {
        if (value instanceof Number number) {
            return String.valueOf(number);
        }
        return quote(Objects.toString(value, ""));
    }

    private String quote(String value) {
        return "\"" + safeText(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n") + "\"";
    }

    private String escapeMermaid(String value) {
        return safeText(value).replace("\"", "\\\"").replace("\n", " ");
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash compile fingerprint", ex);
        }
    }

    private List<String> sortStrings(List<String> input) {
        return normalizeList(input).stream()
                .map(this::safeText)
                .sorted()
                .toList();
    }

    private List<Long> sortLongs(List<Long> input) {
        return normalizeList(input).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String fallback(String value, String fallback) {
        String text = safeText(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private <T> List<T> normalizeList(List<T> values) {
        return values == null ? List.of() : values.stream().filter(Objects::nonNull).toList();
    }

    private List<ResolvedSkillRef> resolveSkillRefs(String orgId, String agentId, List<String> requestedSkillRefs) {
        List<String> requested = normalizeList(requestedSkillRefs).stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase())
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (!requested.isEmpty()) {
            return requested.stream()
                    .map(skillCode -> resolveSkillRefByCode(orgId, skillCode, "explicit"))
                    .toList();
        }
        if (agentId == null || agentId.isBlank()) {
            return List.of();
        }
        List<AgentSkillBindingEntity> bindings = agentSkillBindingRepository.findByOrgIdAndAgentIdAndEnabledTrueOrderByPriorityAscIdAsc(
                orgId,
                agentId.trim()
        );
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> skillIds = bindings.stream()
                .map(AgentSkillBindingEntity::getSkillId)
                .distinct()
                .toList();
        Map<Long, SkillDefinitionEntity> byId = skillDefinitionRepository.findByOrgIdAndIdInAndEnabledTrue(orgId, skillIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(SkillDefinitionEntity::getId, item -> item));
        List<ResolvedSkillRef> refs = new ArrayList<>();
        for (Long skillId : skillIds) {
            SkillDefinitionEntity skill = byId.get(skillId);
            if (skill == null) {
                continue;
            }
            Integer latestVersionNo = skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, skill.getId())
                    .map(item -> item.getVersionNo())
                    .orElse(null);
            refs.add(new ResolvedSkillRef(
                    skill.getSkillCode(),
                    skill.getId(),
                    latestVersionNo,
                    "agent-binding",
                    true
            ));
        }
        return refs;
    }

    private ResolvedSkillRef resolveSkillRefByCode(String orgId, String skillCode, String source) {
        Optional<SkillDefinitionEntity> found = skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, skillCode);
        if (found.isEmpty()) {
            return new ResolvedSkillRef(skillCode, null, null, source, false);
        }
        SkillDefinitionEntity skill = found.get();
        Integer latestVersionNo = skillVersionRepository.findTopByOrgIdAndSkillIdOrderByVersionNoDesc(orgId, skill.getId())
                .map(item -> item.getVersionNo())
                .orElse(null);
        return new ResolvedSkillRef(skill.getSkillCode(), skill.getId(), latestVersionNo, source, true);
    }

    private String normalizeRiskLevel(String riskLevel) {
        String text = safeText(riskLevel).trim().toUpperCase();
        if (List.of("LOW", "MEDIUM", "HIGH").contains(text)) {
            return text;
        }
        return "MEDIUM";
    }

    private List<SkillDefinitionEntity> loadResolvedSkills(String orgId, List<String> resolvedSkillCodes) {
        if (resolvedSkillCodes.isEmpty()) {
            return List.of();
        }
        return resolvedSkillCodes.stream()
                .map(code -> skillDefinitionRepository.findByOrgIdAndSkillCode(orgId, code))
                .flatMap(Optional::stream)
                .toList();
    }

    private List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(",")).stream()
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private List<Long> parseLongList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Long> parsed = new ArrayList<>();
        for (String item : raw) {
            try {
                parsed.add(Long.parseLong(item));
            } catch (NumberFormatException ignore) {
                // Skill kb whitelist may use code-based ids in future.
            }
        }
        return parsed;
    }

    private List<String> mergeBoundary(List<String> agentBoundary, List<String> skillBoundary) {
        List<String> agent = normalizeList(agentBoundary).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
        List<String> skill = normalizeList(skillBoundary).stream().map(String::trim).filter(item -> !item.isBlank()).toList();
        if (!agent.isEmpty() && !skill.isEmpty()) {
            LinkedHashSet<String> intersection = new LinkedHashSet<>(agent);
            intersection.retainAll(skill);
            if (!intersection.isEmpty()) {
                return List.copyOf(intersection);
            }
            LinkedHashSet<String> union = new LinkedHashSet<>(agent);
            union.addAll(skill);
            return List.copyOf(union);
        }
        if (!agent.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(agent));
        }
        if (!skill.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(skill));
        }
        return List.of();
    }

    private List<Long> mergeBoundaryLong(List<Long> agentBoundary, List<Long> skillBoundary) {
        List<Long> agent = normalizeList(agentBoundary);
        List<Long> skill = normalizeList(skillBoundary);
        if (!agent.isEmpty() && !skill.isEmpty()) {
            LinkedHashSet<Long> intersection = new LinkedHashSet<>(agent);
            intersection.retainAll(skill);
            if (!intersection.isEmpty()) {
                return List.copyOf(intersection);
            }
            LinkedHashSet<Long> union = new LinkedHashSet<>(agent);
            union.addAll(skill);
            return List.copyOf(union);
        }
        if (!agent.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(agent));
        }
        if (!skill.isEmpty()) {
            return List.copyOf(new LinkedHashSet<>(skill));
        }
        return List.of();
    }

    public record CompileCommand(
            String agentId,
            String name,
            String summary,
            String greeting,
            String model,
            String systemPrompt,
            String specText,
            List<String> channels,
            List<Long> knowledgeBaseIds,
            List<String> toolIds,
            List<String> skillRefs,
            String handoffRule,
            String safetyLevel,
            String executionMode,
            String version
    ) {
    }

    public record CompileResult(
            String workflowCode,
            Map<String, Object> workflowManifest,
            WorkflowPreview workflowPreview,
            List<String> compileSummary,
            List<String> warnings,
            List<String> dependencies,
            List<ResolvedSkillRef> resolvedSkillRefs,
            Integer draftVersionNo,
            boolean changed,
            String compileMessage,
            List<String> changeLog
    ) {
    }

    private record PersistResult(
            Integer versionNo,
            boolean changed,
            String message,
            List<String> changeLog
    ) {
    }

    public record ResolvedSkillRef(
            String skillCode,
            Long skillId,
            Integer versionNo,
            String source,
            boolean resolved
    ) {
    }

    public record WorkflowPreview(
            String format,
            String diagramDsl,
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges
    ) {
    }

    public record WorkflowNode(
            String id,
            String label,
            String detail,
            String kind
    ) {
    }

    public record WorkflowEdge(
            String from,
            String to,
            String label
    ) {
    }

    private record ToolProfile(
            String id,
            String name,
            String description,
            String riskLevel
    ) {
    }
}
