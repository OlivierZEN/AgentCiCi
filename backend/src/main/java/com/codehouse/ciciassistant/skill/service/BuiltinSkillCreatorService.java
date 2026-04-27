package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.ai.service.AliyunBailianClient;
import com.codehouse.ciciassistant.ai.service.ModelRouterService;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog.ToolCatalogItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Hidden platform capability that turns natural language intent into a structured skill draft.
 * It is intentionally not exposed as a visible builtin skill asset.
 */
@Service
public class BuiltinSkillCreatorService {

    private static final Logger log = LoggerFactory.getLogger(BuiltinSkillCreatorService.class);

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillSpecSchemaValidator schemaValidator;
    private final ModelRouterService modelRouterService;
    private final AliyunBailianClient aliyunBailianClient;
    private final ObjectMapper objectMapper;

    public BuiltinSkillCreatorService(ToolDefinitionRepository toolDefinitionRepository,
                                      KnowledgeBaseRepository knowledgeBaseRepository,
                                      SkillDefinitionRepository skillDefinitionRepository,
                                      SkillSpecSchemaValidator schemaValidator,
                                      ModelRouterService modelRouterService,
                                      AliyunBailianClient aliyunBailianClient,
                                      ObjectMapper objectMapper) {
        this.toolDefinitionRepository = toolDefinitionRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.skillDefinitionRepository = skillDefinitionRepository;
        this.schemaValidator = schemaValidator;
        this.modelRouterService = modelRouterService;
        this.aliyunBailianClient = aliyunBailianClient;
        this.objectMapper = objectMapper;
    }

    public GeneratedSkillDraft generate(String orgId, GenerateCommand command) {
        String sourceText = requireText(command.sourceText(), "sourceText");
        List<ToolOption> tools = loadToolOptions(orgId);
        List<KnowledgeBaseOption> knowledgeBases = loadKnowledgeBases(orgId);
        Set<String> availableToolNames = tools.stream().map(ToolOption::toolName).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> availableKbIds = knowledgeBases.stream().map(KnowledgeBaseOption::id).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        GeneratedSkillDraft modelDraft = tryGenerateByModel(orgId, command, tools, knowledgeBases, availableToolNames, availableKbIds);
        if (modelDraft != null) {
            return modelDraft;
        }
        return generateByHeuristic(orgId, command, tools, knowledgeBases, availableToolNames, availableKbIds);
    }

    private GeneratedSkillDraft generateByHeuristic(String orgId,
                                                    GenerateCommand command,
                                                    List<ToolOption> tools,
                                                    List<KnowledgeBaseOption> knowledgeBases,
                                                    Set<String> availableToolNames,
                                                    Set<String> availableKbIds) {
        String sourceText = requireText(command.sourceText(), "sourceText");
        String lowered = sourceText.toLowerCase(Locale.ROOT);
        List<String> orderedSteps = extractOrderedSteps(sourceText);
        List<String> sourceFacts = extractSourceFacts(sourceText);

        List<String> warnings = new ArrayList<>();
        List<String> clarificationQuestions = new ArrayList<>();

        String skillCode = command.preferredSkillCode() != null && !command.preferredSkillCode().isBlank()
                ? schemaValidator.sanitizeSkillCode(command.preferredSkillCode())
                : buildGenericSkillCode(sourceText, orderedSteps, warnings);
        skillCode = ensureUniqueSkillCode(orgId, skillCode, warnings);

        String name = trimToNull(command.preferredName());
        if (name == null) {
            name = inferGenericSkillName(sourceText, orderedSteps);
        }
        String riskLevel = inferGenericRiskLevel(lowered);
        List<String> toolWhitelist = inferToolWhitelist(sourceText, lowered, tools);
        List<String> kbWhitelist = inferKnowledgeBaseIds(lowered, knowledgeBases);
        if (toolWhitelist.isEmpty()) {
            warnings.add("未匹配到明确工具，建议后续补充工具白名单。");
            clarificationQuestions.add("是否需要绑定特定工具，例如 CRM 查询、审批待办、邮件或网页搜索？");
        }
        if (kbWhitelist.isEmpty()) {
            warnings.add("未匹配到明确知识库，如需 grounded answer 建议后续绑定知识库。");
            clarificationQuestions.add("是否需要绑定一个知识库来承载制度、流程或产品资料？");
        }
        if ("HIGH".equals(riskLevel) && !containsAny(lowered, "转人工", "人工", "确认", "审批")) {
            clarificationQuestions.add("这个技能是否需要在高风险场景下强制转人工或请求确认？");
        }

        String outputContract = inferGenericOutputContract(sourceText, orderedSteps);
        String handoffRule = inferGenericHandoffRule(sourceText, toolWhitelist, riskLevel);
        String description = inferGenericDescription(sourceText, orderedSteps, outputContract);
        String promptFragment = inferPromptFragment(
                sourceText, outputContract, handoffRule, toolWhitelist, kbWhitelist, orderedSteps);
        List<String> triggerHints = inferGenericTriggerHints(sourceText, orderedSteps);
        List<String> userIntentExamples = inferGenericIntentExamples(name, triggerHints, outputContract);
        String draftSpecText = buildDraftSpecText(
                name, description, triggerHints, toolWhitelist, kbWhitelist, handoffRule, outputContract, orderedSteps, sourceFacts);

        GeneratedSkillDraft draft = new GeneratedSkillDraft(
                skillCode,
                name,
                description,
                promptFragment,
                draftSpecText,
                toolWhitelist,
                kbWhitelist,
                handoffRule,
                outputContract,
                riskLevel,
                triggerHints,
                userIntentExamples,
                clarificationQuestions,
                warnings
        );
        return schemaValidator.sanitize(draft, availableToolNames, availableKbIds);
    }

    private GeneratedSkillDraft tryGenerateByModel(String orgId,
                                                   GenerateCommand command,
                                                   List<ToolOption> tools,
                                                   List<KnowledgeBaseOption> knowledgeBases,
                                                   Set<String> availableToolNames,
                                                   Set<String> availableKbIds) {
        try {
            Map<String, String> route = modelRouterService.route(orgId, "skill-authoring");
            String provider = trimToNull(command.preferredProvider());
            if (provider == null) {
                provider = route.get("provider");
            }
            String modelName = trimToNull(command.preferredModel());
            if (modelName == null) {
                modelName = route.get("modelName");
            }
            if ("mock".equals(provider) || "cici-default".equals(modelName)) {
                modelName = null;
            }
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "system", "content", buildModelSystemPrompt()),
                    Map.of("role", "user", "content", buildModelUserPrompt(command, tools, knowledgeBases))
            );
            log.info("Skill authoring: calling model [{}] from provider [{}] for org [{}]", modelName, provider, orgId);
            AliyunBailianClient.ChatCompletionResult result =
                    aliyunBailianClient.chatCompletion(modelName, messages, null, true);
            String content = trimToNull(result.content());
            if (content == null) {
                log.warn("Skill authoring: model returned empty content");
                return null;
            }
            if (content.startsWith("Aliyun API key") || content.startsWith("Model call failed")) {
                log.warn("Skill authoring: model call error: {}", content);
                return null;
            }
            JsonNode root = parseModelJson(content);
            if (root == null || !root.isObject()) {
                log.warn("Skill authoring: model returned non-JSON content (length={})", content.length());
                return null;
            }
            GeneratedSkillDraft draft = new GeneratedSkillDraft(
                    text(root, "skillCode"),
                    text(root, "name"),
                    text(root, "description"),
                    text(root, "promptFragment"),
                    text(root, "draftSpecText"),
                    list(root, "toolWhitelist"),
                    list(root, "kbWhitelist"),
                    text(root, "handoffRule"),
                    text(root, "outputContract"),
                    text(root, "riskLevel"),
                    list(root, "triggerHints"),
                    list(root, "userIntentExamples"),
                    list(root, "clarificationQuestions"),
                    list(root, "warnings")
            );
            GeneratedSkillDraft sanitized = schemaValidator.sanitize(draft, availableToolNames, availableKbIds);
            List<String> warnings = new ArrayList<>(sanitized.warnings());
            warnings.add("由模型驱动结构化生成，已经过 schema 与资源白名单校验。");
            if (command.preferredSkillCode() != null && !command.preferredSkillCode().isBlank()) {
                sanitized = new GeneratedSkillDraft(
                        schemaValidator.sanitizeSkillCode(command.preferredSkillCode()),
                        sanitized.name(),
                        sanitized.description(),
                        sanitized.promptFragment(),
                        sanitized.draftSpecText(),
                        sanitized.toolWhitelist(),
                        sanitized.kbWhitelist(),
                        sanitized.handoffRule(),
                        sanitized.outputContract(),
                        sanitized.riskLevel(),
                        sanitized.triggerHints(),
                        sanitized.userIntentExamples(),
                        sanitized.clarificationQuestions(),
                        warnings
                );
            }
            String ensuredCode = ensureUniqueSkillCode(orgId, sanitized.skillCode(), warnings);
            log.info("Skill authoring: model-based generation succeeded, skillCode={}", ensuredCode);
            return new GeneratedSkillDraft(
                    ensuredCode,
                    sanitized.name(),
                    sanitized.description(),
                    sanitized.promptFragment(),
                    sanitized.draftSpecText(),
                    sanitized.toolWhitelist(),
                    sanitized.kbWhitelist(),
                    sanitized.handoffRule(),
                    sanitized.outputContract(),
                    sanitized.riskLevel(),
                    sanitized.triggerHints(),
                    sanitized.userIntentExamples(),
                    sanitized.clarificationQuestions(),
                    warnings
            );
        } catch (Exception ex) {
            log.warn("Skill authoring: model-based generation failed, will fall back to heuristic: {}", ex.getMessage());
            return null;
        }
    }

    private String buildModelSystemPrompt() {
        return """
                你是企业智能体平台的 Skill Authoring Compiler。你的任务是把管理员的自然语言需求转换成一个标准 JSON 对象。

                严格要求：
                1. 只输出一个 JSON 对象，不要 markdown 代码块，不要 ```json，不要额外解释文字。
                2. JSON 必须包含以下全部字段：skillCode, name, description, promptFragment, draftSpecText, toolWhitelist, kbWhitelist, handoffRule, outputContract, riskLevel, triggerHints, userIntentExamples, clarificationQuestions, warnings。
                3. riskLevel 仅允许 LOW / MEDIUM / HIGH。
                4. toolWhitelist 和 kbWhitelist 只能从用户消息中给出的候选集合里选择，不确定就输出空数组并在 warnings 中说明。
                5. skillCode 使用小写英文加连字符，如 "approval-risk-guard"。
                6. description 要同时说明"做什么"和"什么时候触发"，语言稍微主动（pushy），让路由器更容易命中。
                7. promptFragment 是给 AI 模型的执行指令片段，要具体、可操作，包含处理步骤、证据收集方式、输出结构和兜底规则。
                8. draftSpecText 是面向人类可读的技能规格草稿，用中文分步描述：技能名称、目标、触发场景、处理步骤、工具边界、知识边界、转人工规则、输出要求。
                9. triggerHints 给出 3-5 个触发场景短语。
                10. userIntentExamples 给出 3 个用户可能说的自然语言示例。
                11. clarificationQuestions 列出需要管理员确认的问题（不确定的工具/知识库/边界等）。
                12. warnings 列出当前草稿的不足或待补充项。
                13. 如果 sourceText 中已经给出了明确的处理步骤、工具名、边界或输出要求，必须优先保留这些事实，不要改写成其他业务场景。
                14. 不要依赖任何内置行业模板去猜业务。优先根据 sourceText 本身归纳技能主题；如果信息不足，就保留通用表述并通过 clarificationQuestions 提问。
                15. 示例仅用于 JSON 结构参考，不代表你应该优先生成类似的审批/CRM/合同技能。

                示例输出（仅供参考格式，内容需根据实际需求生成）：
                {"skillCode":"skill-draft","name":"自定义业务技能","description":"用于处理管理员描述的业务场景；当用户提出与该场景相似的请求时触发；输出包含结论、依据和下一步建议。","promptFragment":"先复述并确认用户要处理的业务目标，再严格按照已知步骤、工具边界和输出要求执行。若事实不足，不要套用其他行业模板。优先基于已授权工具和知识库收集证据，再给出结构化结论。","draftSpecText":"技能名称：自定义业务技能\\n目标：处理管理员描述的业务场景并输出结构化建议\\n触发场景：\\n- 业务请求识别\\n处理步骤：\\n1. 识别用户目标和上下文\\n2. 按已知步骤或规则执行\\n3. 输出结论、依据和下一步建议\\n工具边界：暂未指定\\n知识边界：暂未指定\\n转人工规则：遇到高风险动作或事实不足时请求人工确认\\n输出要求：输出包含结论、依据和下一步建议","toolWhitelist":[],"kbWhitelist":[],"handoffRule":"遇到高风险动作或事实不足时请求人工确认。","outputContract":"输出包含结论、依据和下一步建议。","riskLevel":"MEDIUM","triggerHints":["业务请求识别"],"userIntentExamples":["帮我处理这个业务场景","把这个流程整理成可执行建议","根据下面要求生成一个技能草稿"],"clarificationQuestions":["是否需要绑定特定工具或知识库？"],"warnings":["当前示例仅展示结构化格式，正式生成时请以 sourceText 为准。"]}
                """;
    }

    private String buildModelUserPrompt(GenerateCommand command,
                                        List<ToolOption> tools,
                                        List<KnowledgeBaseOption> knowledgeBases) {
        StringBuilder sb = new StringBuilder();
        sb.append("sourceText:\n").append(requireText(command.sourceText(), "sourceText")).append("\n\n");
        if (trimToNull(command.preferredName()) != null) {
            sb.append("preferredName: ").append(command.preferredName().trim()).append("\n");
        }
        if (trimToNull(command.preferredSkillCode()) != null) {
            sb.append("preferredSkillCode: ").append(command.preferredSkillCode().trim()).append("\n");
        }
        sb.append("\n工具候选(toolName|displayName):\n");
        for (ToolOption tool : tools) {
            sb.append("- ").append(tool.toolName()).append("|").append(tool.displayName()).append("\n");
        }
        sb.append("\n知识库候选(id|name):\n");
        for (KnowledgeBaseOption kb : knowledgeBases) {
            sb.append("- ").append(kb.id()).append("|").append(kb.name()).append("\n");
        }
        return sb.toString();
    }

    private JsonNode parseModelJson(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            if (firstNewline > 0) {
                cleaned = cleaned.substring(firstNewline + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception ex) {
            int start = cleaned.indexOf('{');
            int end = cleaned.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(cleaned.substring(start, end + 1));
                } catch (Exception ignored) {
                    log.debug("Skill authoring: failed to parse extracted JSON fragment");
                    return null;
                }
            }
            return null;
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> list(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = item == null ? null : item.asText();
            if (value != null && !value.isBlank()) {
                out.add(value.trim());
            }
        }
        return List.copyOf(out);
    }

    private List<ToolOption> loadToolOptions(String orgId) {
        Map<String, ToolOption> out = new LinkedHashMap<>();
        for (ToolCatalogItem item : BuiltinToolCatalog.list()) {
            out.put(item.toolName(), new ToolOption(item.toolName(), item.displayName(), item.description(), item.category()));
        }
        for (ToolDefinitionEntity item : toolDefinitionRepository.findByOrgIdAndEnabledTrue(orgId)) {
            out.putIfAbsent(item.getToolName(), new ToolOption(item.getToolName(), item.getToolName(), item.getDescription(), "custom"));
        }
        return List.copyOf(out.values());
    }

    private List<KnowledgeBaseOption> loadKnowledgeBases(String orgId) {
        List<KnowledgeBaseOption> out = new ArrayList<>();
        for (KnowledgeBaseEntity item : knowledgeBaseRepository.findByOrgIdOrderByIdDesc(orgId)) {
            if ("DELETED".equalsIgnoreCase(item.getStatus())) {
                continue;
            }
            out.add(new KnowledgeBaseOption(String.valueOf(item.getId()), item.getName(), item.getDescription()));
        }
        return out;
    }

    private DomainProfile inferDomain(String lowered) {
        if (containsAny(lowered, "市场活动", "营销活动", "campaign", "邮件营销", "营销邮件", "活动成员")) {
            return new DomainProfile("marketing", "campaign-orchestrator");
        }
        if (containsAny(lowered, "审批", "approval", "签批", "催办", "待办")) {
            if (containsAny(lowered, "风险", "检查", "条款", "折扣", "合同", "承诺")) {
                return new DomainProfile("approval", "risk-guard");
            }
            return new DomainProfile("approval", "assistant");
        }
        if (containsAny(lowered, "合同", "条款", "法务", "sla")) {
            return new DomainProfile("contract", containsAny(lowered, "风险", "审查", "评审") ? "risk-guard" : "review");
        }
        if (containsAny(lowered, "续约", "renewal")) {
            return new DomainProfile("crm", "renewal-guard");
        }
        if (containsAny(lowered, "线索", "lead")) {
            return new DomainProfile("crm", "lead-intake");
        }
        if (containsAny(lowered, "商机", "opportunity")) {
            return new DomainProfile("crm", "opportunity-health");
        }
        if (containsAny(lowered, "跟进", "触达", "cadence", "follow-up", "followup")) {
            return new DomainProfile("crm", "followup-orchestrator");
        }
        if (containsAny(lowered, "报价", "quote", "折扣", "价格")) {
            return new DomainProfile("sales", containsAny(lowered, "风险", "审批", "检查") ? "quote-risk-check" : "quote-assistant");
        }
        if (containsAny(lowered, "邮件", "邮箱", "email")) {
            return new DomainProfile("email", containsAny(lowered, "回复", "发送") ? "reply-assistant" : "inbox-assistant");
        }
        if (containsAny(lowered, "搜索", "网页", "公开信息", "最新", "新闻", "互联网", "web")) {
            return new DomainProfile("web", "research");
        }
        if (containsAny(lowered, "知识库", "文档", "制度", "faq", "政策")) {
            return new DomainProfile("knowledge", "grounded-qa");
        }
        return new DomainProfile("general", "assistant");
    }

    private String inferSkillName(DomainProfile domain, String lowered) {
        return switch (domain.domainCode() + ":" + domain.actionCode()) {
            case "approval:risk-guard" -> "审批前风险检查";
            case "approval:assistant" -> "审批推进助手";
            case "contract:risk-guard" -> "合同风险识别";
            case "contract:review" -> "合同条款审查";
            case "marketing:campaign-orchestrator" -> "市场活动执行助手";
            case "crm:renewal-guard" -> "CRM 续约预警";
            case "crm:lead-intake" -> "CRM 线索分诊";
            case "crm:opportunity-health" -> "CRM 商机健康扫描";
            case "crm:followup-orchestrator" -> "CRM 跟进节奏编排";
            case "sales:quote-risk-check" -> "报价风险检查";
            case "sales:quote-assistant" -> "报价辅助";
            case "email:reply-assistant" -> "邮件回复助手";
            case "email:inbox-assistant" -> "邮件协同助手";
            case "web:research" -> "公开信息检索";
            case "knowledge:grounded-qa" -> "知识问答助手";
            default -> containsAny(lowered, "风险", "检查") ? "业务风险检查" : "自定义业务技能";
        };
    }

    private String buildSkillCode(DomainProfile domain, String lowered, List<String> warnings) {
        String base = switch (domain.domainCode() + ":" + domain.actionCode()) {
            case "approval:risk-guard" -> "approval-risk-guard";
            case "approval:assistant" -> "approval-assistant";
            case "contract:risk-guard" -> "contract-risk-guard";
            case "contract:review" -> "contract-review";
            case "marketing:campaign-orchestrator" -> "marketing-campaign-orchestrator";
            case "crm:renewal-guard" -> "crm-renewal-guard";
            case "crm:lead-intake" -> "crm-lead-intake";
            case "crm:opportunity-health" -> "crm-opportunity-health";
            case "crm:followup-orchestrator" -> "crm-followup-orchestrator";
            case "sales:quote-risk-check" -> "quote-risk-check";
            case "sales:quote-assistant" -> "quote-assistant";
            case "email:reply-assistant" -> "email-reply-assistant";
            case "email:inbox-assistant" -> "email-collaboration-assistant";
            case "web:research" -> "web-research-assistant";
            case "knowledge:grounded-qa" -> "knowledge-grounded-qa";
            default -> "custom-business-skill";
        };
        warnings.add("skillCode 由系统根据场景自动生成，可在保存前手动调整。");
        return base;
    }

    private String ensureUniqueSkillCode(String orgId, String base, List<String> warnings) {
        String candidate = base;
        int suffix = 2;
        while (skillDefinitionRepository.existsByOrgIdAndSkillCode(orgId, candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        if (!candidate.equals(base)) {
            warnings.add("检测到 skillCode 已存在，系统已自动追加后缀避免冲突。");
        }
        return candidate;
    }

    private String inferRiskLevel(String lowered, DomainProfile domain) {
        if (containsAny(lowered, "合同", "条款", "法务", "折扣", "报价", "价格", "付款", "发邮件", "发送", "承诺", "sla")) {
            return "HIGH";
        }
        if (containsAny(lowered, "审批", "客户", "crm", "商机", "续约", "线索", "邮件", "市场活动", "营销")) {
            return "MEDIUM";
        }
        if ("web".equals(domain.domainCode()) || "knowledge".equals(domain.domainCode())) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private List<String> inferToolWhitelist(String sourceText, String lowered, List<ToolOption> tools) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        selected.addAll(matchExplicitToolMentions(sourceText, tools));
        if (!selected.isEmpty()) {
            if (containsAny(lowered, "发邮件", "发送邮件", "邮件发送", "邮件模板", "营销邮件")) {
                addIfAvailable(selected, "email_send", tools);
            }
            if (containsAny(lowered, "回复邮件", "邮件回复")) {
                addIfAvailable(selected, "email_reply", tools);
            }
            return List.copyOf(selected);
        }
        if (containsAny(lowered, "知识库", "文档", "faq", "制度", "政策")) {
            addIfAvailable(selected, "rag-search", tools);
        }
        if (containsAny(lowered, "市场活动", "营销活动", "campaign", "活动成员")) {
            addIfAvailable(selected, "cloudcc_pageQuery", tools);
        }
        if (containsAny(lowered, "crm", "客户", "商机", "线索", "续约", "销售", "报价")) {
            addIfAvailable(selected, "cloudcc_getStandardObjects", tools);
            addIfAvailable(selected, "cloudcc_getCustomObjects", tools);
            addIfAvailable(selected, "cloudcc_getObjectFields", tools);
            addIfAvailable(selected, "cloudcc_pageQuery", tools);
        }
        if (containsAny(lowered, "审批", "待办", "催办")) {
            addIfAvailable(selected, "get_pending_approvals", tools);
        }
        if (containsAny(lowered, "邮件", "邮箱", "收件箱")) {
            addIfAvailable(selected, "email_list_inbox", tools);
            addIfAvailable(selected, "email_search", tools);
            if (containsAny(lowered, "正文", "详情", "message")) {
                addIfAvailable(selected, "email_get_message", tools);
            }
            if (containsAny(lowered, "发送", "回复")) {
                addIfAvailable(selected, "email_send", tools);
                addIfAvailable(selected, "email_reply", tools);
            }
        }
        if (containsAny(lowered, "搜索", "公开信息", "互联网", "新闻", "最新", "网页", "web")) {
            addIfAvailable(selected, "tavily_search", tools);
            if (containsAny(lowered, "正文", "原文", "链接", "url")) {
                addIfAvailable(selected, "tavily_extract", tools);
            }
        }
        return List.copyOf(selected);
    }

    private List<String> matchExplicitToolMentions(String sourceText, List<ToolOption> tools) {
        String lowered = sourceText.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (ToolOption tool : tools) {
            String toolName = tool.toolName().toLowerCase(Locale.ROOT);
            String displayName = safe(tool.displayName()).toLowerCase(Locale.ROOT);
            if (lowered.contains(toolName) || (!displayName.isBlank() && lowered.contains(displayName))) {
                selected.add(tool.toolName());
            }
        }
        return List.copyOf(selected);
    }

    private void addIfAvailable(Set<String> selected, String toolName, List<ToolOption> tools) {
        if (tools.stream().anyMatch(item -> item.toolName().equals(toolName))) {
            selected.add(toolName);
        }
    }

    private List<String> inferKnowledgeBaseIds(String lowered, List<KnowledgeBaseOption> knowledgeBases) {
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        for (KnowledgeBaseOption item : knowledgeBases) {
            String haystack = (item.name() + " " + safe(item.description())).toLowerCase(Locale.ROOT);
            if (lowered.contains(item.name().toLowerCase(Locale.ROOT))
                    || sharedKeyword(haystack, lowered)) {
                selected.add(item.id());
            }
        }
        return List.copyOf(selected);
    }

    private boolean sharedKeyword(String a, String b) {
        for (String keyword : List.of("审批", "合同", "法务", "报价", "销售", "客户", "crm", "产品", "制度", "政策", "邮件")) {
            if (a.contains(keyword) && b.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String buildGenericSkillCode(String sourceText, List<String> orderedSteps, List<String> warnings) {
        String fromText = schemaValidator.sanitizeSkillCode(extractAsciiKeywords(sourceText));
        if (!"skill-draft".equals(fromText)) {
            warnings.add("skillCode 根据需求中的英文关键词自动生成，可在保存前手动调整。");
            return fromText;
        }
        if (!orderedSteps.isEmpty()) {
            String fromSteps = schemaValidator.sanitizeSkillCode(extractAsciiKeywords(String.join(" ", orderedSteps)));
            if (!"skill-draft".equals(fromSteps)) {
                warnings.add("skillCode 根据步骤中的英文关键词自动生成，可在保存前手动调整。");
                return fromSteps;
            }
        }
        warnings.add("skillCode 未从自然语言中提取到稳定英文标识，已回退为通用草稿编码，可在保存前手动调整。");
        return "skill-draft";
    }

    private String inferGenericSkillName(String sourceText, List<String> orderedSteps) {
        String candidate = firstMeaningfulClause(sourceText);
        if (candidate != null) {
            return candidate;
        }
        if (!orderedSteps.isEmpty()) {
            String firstStep = firstStepLabel(orderedSteps.get(0));
            if (firstStep != null) {
                return firstStep + "技能";
            }
        }
        return "自定义业务技能";
    }

    private String inferGenericRiskLevel(String lowered) {
        if (containsAny(lowered, "合同", "条款", "法务", "报价", "折扣", "承诺", "发送", "发邮件", "付款", "批量", "导入")) {
            return "HIGH";
        }
        if (containsAny(lowered, "审批", "邮件", "客户", "线索", "营销", "活动", "知识库", "同步", "检索")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String inferGenericOutputContract(String sourceText, List<String> orderedSteps) {
        String normalized = sourceText.replace('：', ':');
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(输出(?:里|中|要求)?|结果(?:里|中)?)(?:要|需|需要|包含|包括|为)?[:：]?\\s*([^。\\n]{4,80})")
                .matcher(normalized);
        if (matcher.find()) {
            String body = trimToNull(matcher.group(2));
            if (body != null) {
                String cleaned = body.replaceFirst("^(包含|包括)", "").trim();
                return "输出包含" + cleaned + "。";
            }
        }
        if (!orderedSteps.isEmpty()) {
            return "输出包含当前步骤结论、关键依据和下一步建议。";
        }
        return "输出包含结论、依据和下一步建议。";
    }

    private String inferGenericHandoffRule(String sourceText, List<String> toolWhitelist, String riskLevel) {
        String lowered = sourceText.toLowerCase(Locale.ROOT);
        if (containsAny(lowered, "转人工", "人工确认", "人工审批", "升级人工", "需要确认")) {
            return "按用户描述的规则执行人工确认；遇到高风险动作、权限不清或事实不足时，必须请求人工确认。";
        }
        if (toolWhitelist.contains("email_send") || toolWhitelist.contains("email_reply")) {
            return "涉及对外发送、批量触达、承诺回复或敏感信息外发时，必须请求人工确认。";
        }
        if ("HIGH".equals(riskLevel)) {
            return "涉及高风险动作、关键业务承诺或事实不足时，必须请求人工确认。";
        }
        return "遇到权限不清、事实不足或超出授权边界的请求时，明确说明限制并请求人工确认。";
    }

    private String inferGenericDescription(String sourceText, List<String> orderedSteps, String outputContract) {
        String trigger = firstClause(sourceText);
        if (!orderedSteps.isEmpty()) {
            return "用于处理“" + trigger + "”这类业务场景；执行时优先遵循用户给出的步骤顺序；" + outputContract;
        }
        return "用于处理“" + trigger + "”这类业务场景；优先忠实保留用户描述中的目标、边界和约束；" + outputContract;
    }

    private String firstClause(String sourceText) {
        String compact = sourceText.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 22) {
            return compact;
        }
        return compact.substring(0, 22) + "…";
    }

    private String inferPromptFragment(String sourceText,
                                       String outputContract,
                                       String handoffRule,
                                       List<String> toolWhitelist,
                                       List<String> kbWhitelist,
                                       List<String> orderedSteps) {
        String evidenceRule = kbWhitelist.isEmpty()
                ? "优先基于用户提供的信息和已授权工具形成判断。"
                : "优先基于用户描述、已授权知识库和工具检索事实，再形成判断。";
        String toolRule = toolWhitelist.isEmpty()
                ? "如果缺少可验证事实，先说明限制，不要臆测，也不要套用其他行业模板。"
                : "仅在确有需要时调用白名单内工具补充事实，不要跳过证据收集，也不要发明未授权工具。";
        String orderedStepRule = orderedSteps.isEmpty()
                ? "如果用户没有给出明确步骤，就先拆解业务目标，再输出可执行步骤。"
                : "用户已经明确给出处理步骤，执行时优先遵循以下顺序：" + joinStepsInline(orderedSteps);
        return "先识别用户真正要完成的业务目标，严格按照当前需求描述执行。"
                + evidenceRule
                + toolRule
                + orderedStepRule
                + "如果需求里已经写明目标、步骤、边界、工具名或输出字段，优先保留这些事实，不要改写成别的行业场景。"
                + "结论保持结构化，先给结果，再补充依据和下一步建议。"
                + handoffRule
                + outputContract;
    }

    private List<String> inferGenericTriggerHints(String sourceText, List<String> orderedSteps) {
        LinkedHashSet<String> hints = new LinkedHashSet<>();
        String clause = firstMeaningfulClause(sourceText);
        if (clause != null) {
            hints.add(limitLength(clause, 16));
        }
        for (String step : orderedSteps) {
            String label = firstStepLabel(step);
            if (label != null) {
                hints.add(limitLength(label, 16));
            }
            if (hints.size() >= 4) {
                break;
            }
        }
        if (hints.isEmpty()) {
            hints.add("业务请求识别");
        }
        return List.copyOf(hints);
    }

    private List<String> inferGenericIntentExamples(String name, List<String> triggerHints, String outputContract) {
        String firstTrigger = triggerHints.isEmpty() ? name : triggerHints.get(0);
        return List.of(
                "帮我处理一下" + firstTrigger,
                "把这个需求整理成可执行步骤",
                "按这个场景给我输出" + outputContract.replace("输出包含", "").replace("。", "")
        );
    }

    private String buildDraftSpecText(String name,
                                      String description,
                                      List<String> triggerHints,
                                      List<String> toolWhitelist,
                                      List<String> kbWhitelist,
                                      String handoffRule,
                                      String outputContract,
                                      List<String> orderedSteps,
                                      List<String> sourceFacts) {
        List<String> lines = new ArrayList<>();
        lines.add("技能名称：" + name);
        lines.add("目标：" + description);
        lines.add("触发场景：");
        for (String trigger : triggerHints) {
            lines.add("- " + trigger);
        }
        if (sourceFacts != null && !sourceFacts.isEmpty()) {
            lines.add("来源事实：");
            for (String fact : sourceFacts) {
                lines.add("- " + fact);
            }
        }
        lines.add("处理步骤：");
        if (!orderedSteps.isEmpty()) {
            for (int i = 0; i < orderedSteps.size(); i++) {
                lines.add((i + 1) + ". " + orderedSteps.get(i));
            }
        } else {
            lines.add("1. 先识别用户当前请求类型与风险等级。");
            lines.add("2. 优先基于已授权知识库/工具收集事实与上下文。");
            lines.add("3. 输出结论、依据和下一步建议。");
            lines.add("4. 高风险、权限不清或事实不足时不要直接替代人工决策。");
        }
        lines.add("工具边界：" + (toolWhitelist.isEmpty() ? "暂未指定" : String.join(", ", toolWhitelist)));
        lines.add("知识边界：" + (kbWhitelist.isEmpty() ? "暂未指定" : String.join(", ", kbWhitelist)));
        lines.add("转人工规则：" + handoffRule);
        lines.add("输出要求：" + outputContract);
        return String.join("\n", lines);
    }

    private List<String> extractOrderedSteps(String sourceText) {
        List<String> steps = new ArrayList<>();
        for (String rawLine : sourceText.split("\\R")) {
            String line = trimToNull(rawLine);
            if (line == null) {
                continue;
            }
            String cleaned = line.replaceFirst("^(?:步骤[:：])\\s*", "")
                    .replaceFirst("^\\d+[.、．]\\s*", "")
                    .replaceFirst("^[（(]?\\d+[)）]\\s*", "")
                    .trim();
            if (!cleaned.equals(line) && !cleaned.isBlank()) {
                steps.add(cleaned);
            }
        }
        return List.copyOf(steps);
    }

    private List<String> extractSourceFacts(String sourceText) {
        LinkedHashSet<String> facts = new LinkedHashSet<>();
        for (String raw : sourceText.split("[。！？\\n]+")) {
            String cleaned = trimToNull(raw);
            if (cleaned == null) {
                continue;
            }
            if (cleaned.matches("^\\d+[.、．].*")) {
                continue;
            }
            cleaned = cleaned
                    .replaceFirst("^我想(?:做|创建|生成)?一个?", "")
                    .replaceFirst("^请帮我", "")
                    .trim();
            if (!cleaned.isEmpty()) {
                facts.add(cleaned);
            }
            if (facts.size() >= 3) {
                break;
            }
        }
        return List.copyOf(facts);
    }

    private String joinStepsInline(List<String> orderedSteps) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < orderedSteps.size(); i++) {
            parts.add((i + 1) + "." + orderedSteps.get(i));
        }
        return String.join(" ", parts);
    }

    private String extractAsciiKeywords(String sourceText) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{2,}").matcher(sourceText);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find() && tokens.size() < 5) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return String.join("-", tokens);
    }

    private String firstMeaningfulClause(String sourceText) {
        String compact = sourceText.replaceAll("\\s+", " ").trim();
        if (compact.isEmpty()) {
            return null;
        }
        String cleaned = compact
                .replaceFirst("^当用户(?:想|需要|希望)?", "")
                .replaceFirst("^用户(?:想|需要|希望)?", "")
                .replaceFirst("^我想(?:做|创建|生成)?一个?", "")
                .replaceFirst("^请帮我", "")
                .replaceFirst("可以通过此技能进行.*$", "")
                .replaceFirst("具体流程如下.*$", "")
                .replaceFirst("[，,:：。！？].*$", "")
                .trim();
        if (cleaned.isEmpty()) {
            cleaned = compact;
        }
        cleaned = cleaned.replaceFirst("^(进行一次|做一个|创建一个|生成一个)", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        String label = limitLength(cleaned, 18);
        return label.endsWith("技能") ? label : label + "技能";
    }

    private String firstStepLabel(String step) {
        String cleaned = trimToNull(step);
        if (cleaned == null) {
            return null;
        }
        String label = cleaned.replaceFirst("[，,:：。！？].*$", "").trim();
        return label.isEmpty() ? null : label;
    }

    private String limitLength(String text, int maxLen) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "…";
    }

    private boolean containsAny(String source, String... needles) {
        for (String needle : needles) {
            if (source.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String requireText(String raw, String field) {
        String cleaned = trimToNull(raw);
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

    private String safe(String raw) {
        return raw == null ? "" : raw;
    }

    public record GenerateCommand(
            String sourceText,
            String preferredName,
            String preferredSkillCode,
            String preferredModel,
            String preferredProvider
    ) {
    }

    public record GeneratedSkillDraft(
            String skillCode,
            String name,
            String description,
            String promptFragment,
            String draftSpecText,
            List<String> toolWhitelist,
            List<String> kbWhitelist,
            String handoffRule,
            String outputContract,
            String riskLevel,
            List<String> triggerHints,
            List<String> userIntentExamples,
            List<String> clarificationQuestions,
            List<String> warnings
    ) {
    }

    public SkillSpecIr toSkillSpecIr(GeneratedSkillDraft draft) {
        return new SkillSpecIr(
                draft.skillCode(),
                draft.name(),
                draft.description(),
                "policy-skill",
                draft.triggerHints(),
                draft.userIntentExamples(),
                draft.promptFragment(),
                draft.toolWhitelist(),
                draft.kbWhitelist(),
                draft.handoffRule(),
                draft.outputContract(),
                draft.riskLevel(),
                draft.clarificationQuestions(),
                draft.warnings()
        );
    }

    private record ToolOption(String toolName, String displayName, String description, String category) {
    }

    private record KnowledgeBaseOption(String id, String name, String description) {
    }

    private record DomainProfile(String domainCode, String actionCode) {
    }
}
