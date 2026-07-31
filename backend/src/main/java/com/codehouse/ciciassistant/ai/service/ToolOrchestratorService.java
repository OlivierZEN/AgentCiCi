package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.email.service.EmailToolService;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.mcp.service.McpServerService;
import com.codehouse.ciciassistant.mcp.service.McpServerService.ResolvedTool;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.security.service.SafetyGatewayService;
import com.codehouse.ciciassistant.semattice.SematticeProjectDeliveryToolService;
import com.codehouse.ciciassistant.skill.service.SkillApiToolService;
import com.codehouse.ciciassistant.tool.service.ToolNameNormalizer;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import com.codehouse.ciciassistant.userworkflow.service.AssistantScheduleToolService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Converts MCP tools to OpenAI function-calling format and dispatches tool executions.
 * Also manages built-in native tools (CloudCC OpenAPI, etc.).
 */
@Service
public class ToolOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(ToolOrchestratorService.class);

    static final String TOOL_MEMORY_REMEMBER = "memory_remember";
    static final String TOOL_MEMORY_FORGET = "memory_forget";

    private final McpServerService mcpServerService;
    private final CloudccOpenApiService cloudccOpenApiService;
    private final CrmProductSalesAnalysisToolService crmProductSalesAnalysisToolService;
    private final EmailToolService emailToolService;
    private final UserMemoryService userMemoryService;
    private final TavilyToolService tavilyToolService;
    private final PlatformGovernanceService platformGovernanceService;
    private final SkillApiToolService skillApiToolService;
    private final SematticeProjectDeliveryToolService sematticeProjectDeliveryToolService;
    private final SafetyGatewayService safetyGatewayService;
    private final ObjectMapper objectMapper;
    private AssistantScheduleToolService assistantScheduleToolService;

    public ToolOrchestratorService(McpServerService mcpServerService,
                                   CloudccOpenApiService cloudccOpenApiService,
                                   CrmProductSalesAnalysisToolService crmProductSalesAnalysisToolService,
                                   EmailToolService emailToolService,
                                   UserMemoryService userMemoryService,
                                   TavilyToolService tavilyToolService,
                                   PlatformGovernanceService platformGovernanceService,
                                   SkillApiToolService skillApiToolService,
                                   SematticeProjectDeliveryToolService sematticeProjectDeliveryToolService,
                                   SafetyGatewayService safetyGatewayService,
                                   ObjectMapper objectMapper) {
        this.mcpServerService = mcpServerService;
        this.cloudccOpenApiService = cloudccOpenApiService;
        this.crmProductSalesAnalysisToolService = crmProductSalesAnalysisToolService;
        this.emailToolService = emailToolService;
        this.userMemoryService = userMemoryService;
        this.tavilyToolService = tavilyToolService;
        this.platformGovernanceService = platformGovernanceService;
        this.skillApiToolService = skillApiToolService;
        this.sematticeProjectDeliveryToolService = sematticeProjectDeliveryToolService;
        this.safetyGatewayService = safetyGatewayService;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setAssistantScheduleToolService(AssistantScheduleToolService assistantScheduleToolService) {
        this.assistantScheduleToolService = assistantScheduleToolService;
    }

    /**
     * Get all available tools for the org in OpenAI function-calling format.
     * Includes both MCP-discovered tools and built-in native tools.
     */
    public List<Map<String, Object>> getToolDefinitions(String companyId) {
        return getToolDefinitions(companyId, null);
    }

    public List<Map<String, Object>> getToolDefinitions(String companyId, List<String> allowedToolNames) {
        return getToolDefinitions(companyId, allowedToolNames, List.of());
    }

    public List<Map<String, Object>> getToolDefinitions(String companyId,
                                                        List<String> allowedToolNames,
                                                        List<SkillApiToolService.ResolvedSkillApiTool> skillApiTools) {
        List<String> normalizedAllowedToolNames = normalizeAllowedToolNames(allowedToolNames);
        List<Map<String, Object>> result = new ArrayList<>();

        // 1. Built-in native tools
        addBuiltInTool(result, normalizedAllowedToolNames, CloudccOpenApiService.toolName(),
                CloudccOpenApiService.toolDescription(),
                CloudccOpenApiService.toolSchema(objectMapper),
                companyId);
        addBuiltInTool(result, normalizedAllowedToolNames, CloudccOpenApiService.toolNameGetStandardObjects(),
                CloudccOpenApiService.toolDescriptionGetStandardObjects(),
                CloudccOpenApiService.toolSchemaGetStandardObjects(objectMapper),
                companyId);
        addBuiltInTool(result, normalizedAllowedToolNames, CloudccOpenApiService.toolNameGetCustomObjects(),
                CloudccOpenApiService.toolDescriptionGetCustomObjects(),
                CloudccOpenApiService.toolSchemaGetCustomObjects(objectMapper),
                companyId);
        addBuiltInTool(result, normalizedAllowedToolNames, CloudccOpenApiService.toolNameGetObjectFields(),
                CloudccOpenApiService.toolDescriptionGetObjectFields(),
                CloudccOpenApiService.toolSchemaGetObjectFields(objectMapper),
                companyId);
        addBuiltInTool(result, normalizedAllowedToolNames, CrmProductSalesAnalysisToolService.TOOL_NAME,
                CrmProductSalesAnalysisToolService.toolDescription(),
                CrmProductSalesAnalysisToolService.toolSchema(objectMapper),
                companyId);
        addBuiltInTool(result, normalizedAllowedToolNames, SematticeProjectDeliveryToolService.TOOL_NAME,
                SematticeProjectDeliveryToolService.toolDescription(),
                SematticeProjectDeliveryToolService.toolSchema(objectMapper), companyId);

        // Memory built-in tools (always available, no skill restriction)
        result.add(buildMemoryRememberTool());
        result.add(buildMemoryForgetTool());
        if (assistantScheduleToolService != null) {
            result.add(assistantScheduleToolService.toolDefinition());
        }

        // Email built-in tools (POP3 + SMTP)
        for (String toolName : EmailToolService.ALL_TOOL_NAMES) {
            if (!isAllowed(normalizedAllowedToolNames, toolName, false)) {
                continue;
            }
            if (!platformGovernanceService.isRuntimeToolEnabled(companyId, toolName)) {
                continue;
            }
            result.add(emailToolService.toolDefinition(toolName));
        }

        // Tavily web-search / web-extract built-in tools
        for (String toolName : TavilyToolService.ALL_TOOL_NAMES) {
            if (!isAllowed(normalizedAllowedToolNames, toolName, false)) {
                continue;
            }
            if (!platformGovernanceService.isRuntimeToolEnabled(companyId, toolName)) {
                continue;
            }
            result.add(tavilyToolService.toolDefinition(toolName));
        }

        // Skill-private declarative API tools are injected only from the resolved active skill context.
        result.addAll(skillApiToolService.getRuntimeToolDefinitions(skillApiTools).stream()
                .filter(tool -> {
                    Object function = tool.get("function");
                    if (!(function instanceof Map<?, ?> map)) {
                        return false;
                    }
                    Object name = map.get("name");
                    return name != null && isAllowed(normalizedAllowedToolNames, name.toString(), false);
                })
                .toList());

        // 2. MCP-discovered tools
        List<ResolvedTool> mcpTools = mcpServerService.getAllToolsForOrg(companyId);
        for (ResolvedTool tool : mcpTools) {
            if (!isAllowed(normalizedAllowedToolNames, tool.name(), true)) {
                continue;
            }
            Map<String, Object> parameters;
            try {
                JsonNode schema = tool.inputSchema();
                parameters = objectMapper.convertValue(schema, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                parameters = Map.of("type", "object", "properties", Map.of());
            }
            result.add(Map.of(
                    "type", "function",
                    "function", Map.of(
                            "name", tool.name(),
                            "description", tool.description(),
                            "parameters", parameters
                    )
            ));
        }
        return result;
    }

    private void addBuiltInTool(List<Map<String, Object>> result, List<String> allowedToolNames,
                                String name, String description, JsonNode schema, String companyId) {
        if (!isAllowed(allowedToolNames, name, false)) {
            return;
        }
        if (!platformGovernanceService.isRuntimeToolEnabled(companyId, name)) {
            return;
        }
        result.add(builtInTool(name, description, schema));
    }

    private Map<String, Object> builtInTool(String name, String description, JsonNode schema) {
        Map<String, Object> parameters;
        try {
            parameters = objectMapper.convertValue(schema, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            parameters = Map.of("type", "object", "properties", Map.of());
        }
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    /**
     * Execute a tool call. Routes to native tools first, then falls back to MCP servers.
     */
    public String executeTool(String companyId, String userId, String toolName, String argumentsJson) {
        return executeTool(companyId, userId, toolName, argumentsJson, null, null, null);
    }

    public String executeTool(String companyId, String userId, String toolName, String argumentsJson, List<String> allowedToolNames) {
        return executeTool(companyId, userId, toolName, argumentsJson, allowedToolNames, null, null);
    }

    /**
     * @param agentDirectToolNames when non-null, used only for audit logging (agent_direct vs skill_scoped).
     */
    public String executeTool(String companyId, String userId, String toolName, String argumentsJson,
                              List<String> allowedToolNames, List<String> agentDirectToolNames) {
        return executeTool(companyId, userId, toolName, argumentsJson, allowedToolNames, agentDirectToolNames, null);
    }

    public String executeTool(String companyId, String userId, String toolName, String argumentsJson,
                              List<String> allowedToolNames, List<String> agentDirectToolNames, String currentAgentId) {
        List<String> normalizedAllowedToolNames = normalizeAllowedToolNames(allowedToolNames);
        String canonicalToolName = ToolNameNormalizer.canonicalize(toolName);
        String invocationType = resolveInvocationType(canonicalToolName, normalizeAllowedToolNames(agentDirectToolNames));
        log.info("Executing tool: org={}, user={}, tool={}, invocationType={}", companyId, userId, canonicalToolName, invocationType);
        if (!isAllowed(normalizedAllowedToolNames, canonicalToolName, false)
                && !isAllowed(normalizedAllowedToolNames, canonicalToolName, true)) {
            return "Tool is not allowed for the current skill policy: " + toolName;
        }
        if (!AssistantScheduleToolService.TOOL_NAME.equals(canonicalToolName)
                && !platformGovernanceService.isRuntimeToolEnabled(companyId, canonicalToolName)) {
            return "Tool is disabled by platform runtime control: " + canonicalToolName;
        }
        SafetyGatewayService.SafetyDecision inputDecision =
                safetyGatewayService.checkToolCall(companyId, userId, canonicalToolName, argumentsJson);
        if (inputDecision.blocked()) {
            return "Tool call blocked by security gateway: " + canonicalToolName;
        }
        String safeArgumentsJson = inputDecision.safeText();

        if (canonicalToolName != null && canonicalToolName.startsWith(SkillApiToolService.TOOL_PREFIX)) {
            if (normalizedAllowedToolNames.isEmpty() || !normalizedAllowedToolNames.contains(canonicalToolName)) {
                return "Skill API tool is not active for the current skill context: " + canonicalToolName;
            }
            return safeToolResult(companyId, userId, canonicalToolName,
                    skillApiToolService.dispatch(companyId, userId, canonicalToolName, safeArgumentsJson));
        }

        if (AssistantScheduleToolService.TOOL_NAME.equals(canonicalToolName)) {
            return assistantScheduleToolService == null
                    ? "创建定时任务失败：服务未就绪。"
                    : assistantScheduleToolService.dispatch(companyId, userId, currentAgentId, argumentsJson);
        }

        // Native built-in tools
        if (CrmProductSalesAnalysisToolService.TOOL_NAME.equals(canonicalToolName)) {
            return crmProductSalesAnalysisToolService.dispatch(companyId, userId, argumentsJson);
        }
        if (SematticeProjectDeliveryToolService.TOOL_NAME.equals(canonicalToolName)) {
            return sematticeProjectDeliveryToolService.dispatch(companyId, userId, safeArgumentsJson);
        }
        if (CloudccOpenApiService.toolName().equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    executeCloudccPageQuery(companyId, userId, safeArgumentsJson));
        }
        if (CloudccOpenApiService.toolNameGetStandardObjects().equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    cloudccOpenApiService.getStandardObjects(companyId, userId));
        }
        if (CloudccOpenApiService.toolNameGetCustomObjects().equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    cloudccOpenApiService.getCustomObjects(companyId, userId));
        }
        if (CloudccOpenApiService.toolNameGetObjectFields().equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    executeGetObjectFields(companyId, userId, safeArgumentsJson));
        }

        // Memory built-in tools
        if (TOOL_MEMORY_REMEMBER.equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    executeMemoryRemember(companyId, userId, safeArgumentsJson));
        }
        if (TOOL_MEMORY_FORGET.equals(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    executeMemoryForget(companyId, userId, safeArgumentsJson));
        }

        // Email built-in tools
        if (canonicalToolName != null && canonicalToolName.startsWith("email_") && EmailToolService.ALL_TOOL_NAMES.contains(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    emailToolService.dispatch(companyId, userId, canonicalToolName, safeArgumentsJson));
        }

        // Tavily web-search / web-extract built-in tools
        if (canonicalToolName != null && canonicalToolName.startsWith("tavily_") && TavilyToolService.ALL_TOOL_NAMES.contains(canonicalToolName)) {
            return safeToolResult(companyId, userId, canonicalToolName,
                    tavilyToolService.dispatch(companyId, userId, canonicalToolName, safeArgumentsJson));
        }

        // MCP-discovered tools
        return safeToolResult(companyId, userId, canonicalToolName,
                mcpServerService.executeTool(companyId, userId, canonicalToolName, safeArgumentsJson));
    }

    private String safeToolResult(String companyId, String userId, String toolName, String rawResult) {
        SafetyGatewayService.SafetyDecision decision =
                safetyGatewayService.checkOutput(companyId, userId, "TOOL_RESULT:" + toolName, rawResult);
        if (decision.blocked()) {
            return "Tool result blocked by security gateway: " + toolName;
        }
        return decision.safeText();
    }

    private String executeCloudccPageQuery(String companyId, String userId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String objectApiName = args.path("objectApiName").asText(null);
            if (objectApiName == null) {
                return "❌ 缺少必需参数: objectApiName（对象 API 名称）";
            }
            String fields = args.path("fields").asText("");
            if (fields.isEmpty()) fields = null;
            String expressions = args.path("expressions").asText("");
            if (expressions.isEmpty()) expressions = null;
            Integer pageNum = args.path("pageNum").isInt() ? args.path("pageNum").asInt() : null;
            Integer pageSize = args.path("pageSize").isInt() ? args.path("pageSize").asInt() : null;
            return cloudccOpenApiService.pageQuery(companyId, userId, objectApiName, fields, expressions, pageNum, pageSize);
        } catch (Exception e) {
            log.error("cloudcc_pageQuery execution failed: {}", e.getMessage(), e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    private String executeGetObjectFields(String companyId, String userId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String objprefix = args.path("objprefix").asText(null);
            if (objprefix == null || objprefix.isBlank()) {
                return "❌ 缺少必需参数: objprefix（对象前缀，可从对象列表中获取）";
            }
            return cloudccOpenApiService.getObjectFields(companyId, userId, objprefix);
        } catch (Exception e) {
            log.error("cloudcc_getObjectFields execution failed: {}", e.getMessage(), e);
            return "❌ 执行失败: " + e.getMessage();
        }
    }

    /**
     * Legacy method kept for backward compatibility.
     */
    public Map<String, Object> maybeCallTools(String companyId, String question) {
        Map<String, Object> result = new HashMap<>();
        result.put("toolCalls", List.of());
        return result;
    }

    private String executeMemoryRemember(String companyId, String userId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            String category = args.path("category").asText("FACT");
            String content = args.path("content").asText(null);
            String memoryKey = args.path("memoryKey").asText(null);
            if (memoryKey != null && memoryKey.isBlank()) memoryKey = null;
            if (content == null || content.isBlank()) {
                return "❌ 缺少必需参数: content（记忆内容）";
            }
            BigDecimal confidence = args.path("confidence").isMissingNode()
                    ? BigDecimal.ONE
                    : new BigDecimal(args.path("confidence").asText("1.0"));
            userMemoryService.upsertExtracted(companyId, userId, "cici-system", category, content, memoryKey, confidence);
            return "✅ 已记住：" + content;
        } catch (Exception e) {
            log.error("memory_remember execution failed: {}", e.getMessage(), e);
            return "❌ 记忆保存失败: " + e.getMessage();
        }
    }

    private String executeMemoryForget(String companyId, String userId, String argumentsJson) {
        try {
            JsonNode args = objectMapper.readTree(argumentsJson);
            Long id = args.path("id").isLong() ? args.path("id").asLong() : null;
            if (id == null) {
                return "❌ 缺少必需参数: id（记忆 ID）";
            }
            userMemoryService.delete(companyId, userId, id);
            return "✅ 已删除记忆 #" + id;
        } catch (Exception e) {
            log.error("memory_forget execution failed: {}", e.getMessage(), e);
            return "❌ 记忆删除失败: " + e.getMessage();
        }
    }

    private Map<String, Object> buildMemoryRememberTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> categoryProp = new LinkedHashMap<>();
        categoryProp.put("type", "string");
        categoryProp.put("enum", List.of("FACT", "PREFERENCE", "CONTEXT", "INSTRUCTION"));
        categoryProp.put("description", "记忆类别：FACT=用户事实，PREFERENCE=个人偏好，CONTEXT=工作上下文，INSTRUCTION=行为指令");
        properties.put("category", categoryProp);

        Map<String, Object> contentProp = new LinkedHashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "要记住的内容（中文，简洁清晰，不超过 200 字）");
        properties.put("content", contentProp);

        Map<String, Object> keyProp = new LinkedHashMap<>();
        keyProp.put("type", "string");
        keyProp.put("description", "可选的语义键，用于覆盖同类记忆，如 user.role、user.location、user.language");
        properties.put("memoryKey", keyProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", List.of("category", "content"));

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_MEMORY_REMEMBER,
                        "description", "当用户在对话中提到关于自己的重要信息（身份、偏好、工作背景、持久行为指令）时，将其保存为专属记忆。仅在对话中出现了明确值得长期记住的新信息时才调用。",
                        "parameters", params
                )
        );
    }

    private Map<String, Object> buildMemoryForgetTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> idProp = new LinkedHashMap<>();
        idProp.put("type", "integer");
        idProp.put("description", "要删除的记忆 ID");
        properties.put("id", idProp);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", properties);
        params.put("required", List.of("id"));

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", TOOL_MEMORY_FORGET,
                        "description", "当用户明确要求忘记某条记忆时，根据 ID 删除该记忆。",
                        "parameters", params
                )
        );
    }

    private String resolveInvocationType(String canonicalToolName, List<String> normalizedDirectToolNames) {
        if (TOOL_MEMORY_REMEMBER.equals(canonicalToolName) || TOOL_MEMORY_FORGET.equals(canonicalToolName)) {
            return "memory_builtin";
        }
        if (normalizedDirectToolNames == null || normalizedDirectToolNames.isEmpty()) {
            return "runtime_union";
        }
        if (normalizedDirectToolNames.contains(canonicalToolName)) {
            return "agent_direct";
        }
        return "skill_scoped";
    }

    private List<String> normalizeAllowedToolNames(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return List.of();
        }
        return ToolNameNormalizer.canonicalizeAll(allowedToolNames);
    }

    private boolean isAllowed(List<String> allowedToolNames, String toolName, boolean mcpTool) {
        // memory tools are always available regardless of skill policy
        if (TOOL_MEMORY_REMEMBER.equals(toolName) || TOOL_MEMORY_FORGET.equals(toolName)
                || AssistantScheduleToolService.TOOL_NAME.equals(toolName)) {
            return true;
        }
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return true;
        }
        if (allowedToolNames.contains(toolName)) {
            return true;
        }
        return mcpTool && ToolNameNormalizer.containsMcpWildcard(allowedToolNames);
    }
}
