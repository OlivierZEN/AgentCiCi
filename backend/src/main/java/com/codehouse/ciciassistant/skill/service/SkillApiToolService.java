package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.skill.domain.SkillApiToolEntity;
import com.codehouse.ciciassistant.skill.domain.SkillApiToolRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillVersionEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillApiToolService {

    public static final String TOOL_PREFIX = "skillapi__";

    private static final Pattern TEMPLATE_REF = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> SCHEMA_TYPES = Set.of("string", "integer", "number", "boolean", "object", "array");

    private final SkillApiToolRepository repository;
    private final AuditService auditService;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final IntegrationAppService integrationAppService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Set<String> allowedHosts;
    private final boolean allowLocalhost;

    public SkillApiToolService(SkillApiToolRepository repository,
                               AuditService auditService,
                               CloudccAccessTokenService cloudccAccessTokenService,
                               IntegrationAppService integrationAppService,
                               ObjectMapper objectMapper,
                               @Value("${app.skill-api.allowed-hosts:}") String allowedHostsRaw,
                               @Value("${app.skill-api.allow-localhost:false}") boolean allowLocalhost) {
        this.repository = repository;
        this.auditService = auditService;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
        this.integrationAppService = integrationAppService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.allowedHosts = parseHosts(allowedHostsRaw);
        this.allowLocalhost = allowLocalhost;
    }

    public String serializeDraftApis(List<Map<String, Object>> runtimeApis) {
        List<Map<String, Object>> normalized = new ArrayList<>();
        if (runtimeApis != null) {
            runtimeApis.stream()
                    .filter(Objects::nonNull)
                    .map(item -> new LinkedHashMap<String, Object>(item))
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            throw new IllegalArgumentException("runtimeApis must be valid JSON-compatible objects");
        }
    }

    public List<Map<String, Object>> readDraftApis(String runtimeApiJson) {
        if (runtimeApiJson == null || runtimeApiJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(runtimeApiJson, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("runtimeApis JSON is invalid");
        }
    }

    public RuntimeApiCompilePreview previewCompileApis(String companyId, String skillCode, String runtimeApiJson) {
        List<Map<String, Object>> rawApis = readDraftApis(runtimeApiJson);
        List<Map<String, Object>> toolDefinitions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (int i = 0; i < rawApis.size(); i++) {
            try {
                CompiledApi api = compileOne(companyId, skillCode, rawApis.get(i), new LinkedHashSet<>());
                toolDefinitions.add(api.toolDefinition());
                if ("HIGH".equals(api.riskLevel())) {
                    warnings.add(api.apiCode() + ": 高风险 API 已配置确认策略，运行时未确认前不会执行。");
                }
            } catch (IllegalArgumentException ex) {
                errors.add("runtimeApis[" + i + "]: " + ex.getMessage());
            }
        }
        return new RuntimeApiCompilePreview(toolDefinitions, errors, warnings);
    }

    @Transactional
    public List<SkillApiToolEntity> publishApisForVersion(String companyId,
                                                          SkillDefinitionEntity skill,
                                                          SkillVersionEntity version,
                                                          String runtimeApiJson) {
        repository.deleteByCompanyIdAndSkillVersionId(companyId, version.getId());
        List<Map<String, Object>> rawApis = readDraftApis(runtimeApiJson);
        if (rawApis.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> seenApiCodes = new LinkedHashSet<>();
        List<SkillApiToolEntity> entities = new ArrayList<>();
        for (Map<String, Object> rawApi : rawApis) {
            CompiledApi compiled = compileOne(companyId, skill.getSkillCode(), rawApi, seenApiCodes);
            entities.add(new SkillApiToolEntity(
                    companyId,
                    skill.getId(),
                    version.getId(),
                    skill.getSkillCode(),
                    compiled.apiCode(),
                    compiled.toolName(),
                    compiled.displayName(),
                    compiled.description(),
                    compiled.riskLevel(),
                    compiled.triggerMode(),
                    compiled.inputSchemaJson(),
                    compiled.executionPlanJson()
            ));
        }
        return repository.saveAll(entities);
    }

    public List<ResolvedSkillApiTool> findRuntimeTools(String companyId, Collection<Long> skillVersionIds) {
        if (skillVersionIds == null || skillVersionIds.isEmpty()) {
            return List.of();
        }
        List<Long> ids = skillVersionIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return repository.findByCompanyIdAndSkillVersionIdInAndEnabledTrueOrderByIdAsc(companyId, ids).stream()
                .map(item -> new ResolvedSkillApiTool(
                        item.getSkillCode(),
                        item.getSkillVersionId(),
                        item.getApiCode(),
                        item.getToolName(),
                        item.getDescription(),
                        item.getRiskLevel(),
                        readJsonMap(item.getInputSchemaJson())
                ))
                .toList();
    }

    public List<Map<String, Object>> getRuntimeToolDefinitions(List<ResolvedSkillApiTool> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .map(tool -> Map.<String, Object>of(
                        "type", "function",
                        "function", Map.of(
                                "name", tool.toolName(),
                                "description", tool.description(),
                                "parameters", tool.inputSchema()
                        )
                ))
                .toList();
    }

    public String dispatch(String companyId, String userId, String toolName, String argumentsJson) {
        long started = System.nanoTime();
        SkillApiToolEntity entity = repository.findByCompanyIdAndToolNameAndEnabledTrue(companyId, toolName)
                .orElseThrow(() -> new IllegalArgumentException("Skill API tool not found: " + toolName));
        try {
            if ("HIGH".equalsIgnoreCase(entity.getRiskLevel())) {
                audit(companyId, userId, entity, "BLOCKED_CONFIRMATION_REQUIRED", 0, started, argumentsJson, "confirmation required");
                return "{\"ok\":false,\"error\":\"CONFIRMATION_REQUIRED\",\"message\":\"高风险 Skill API 需要用户确认后才能执行。\"}";
            }
            Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> inputSchema = readJsonMap(entity.getInputSchemaJson());
            validateArguments(inputSchema, args);
            Map<String, Object> plan = readJsonMap(entity.getExecutionPlanJson());
            HttpRequest request = buildHttpRequest(companyId, userId, plan, args);
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int maxBytes = intValue(mapValue(plan, "responseMapping", "maxBytes"), 12000);
            if (response.body().length > maxBytes) {
                audit(companyId, userId, entity, "FAILED_RESPONSE_TOO_LARGE", response.statusCode(), started, argumentsJson, "response too large");
                return "{\"ok\":false,\"error\":\"RESPONSE_TOO_LARGE\"}";
            }
            Object data = extractAndShapeResponse(plan, response.body());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", response.statusCode() >= 200 && response.statusCode() < 300);
            payload.put("status", response.statusCode());
            payload.put("skillCode", entity.getSkillCode());
            payload.put("apiCode", entity.getApiCode());
            payload.put("data", data);
            audit(companyId, userId, entity, "SUCCESS", response.statusCode(), started, argumentsJson, null);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            audit(companyId, userId, entity, "FAILED", 0, started, argumentsJson, ex.getMessage());
            return "{\"ok\":false,\"error\":\"SKILL_API_EXECUTION_FAILED\",\"message\":\""
                    + escapeJson(ex.getMessage()) + "\"}";
        }
    }

    private CompiledApi compileOne(String companyId,
                                   String skillCode,
                                   Map<String, Object> rawApi,
                                   LinkedHashSet<String> seenApiCodes) {
        String apiCode = requiredString(rawApi, "apiCode").toLowerCase(Locale.ROOT);
        if (!apiCode.matches("[a-z0-9_]{1,64}")) {
            throw new IllegalArgumentException("apiCode must use lowercase letters, numbers, and underscores");
        }
        if (!seenApiCodes.add(apiCode)) {
            throw new IllegalArgumentException("duplicate apiCode: " + apiCode);
        }
        String displayName = requiredString(rawApi, "displayName");
        String description = requiredString(rawApi, "description");
        String riskLevel = optionalString(rawApi, "riskLevel", "LOW").toUpperCase(Locale.ROOT);
        if (!List.of("LOW", "MEDIUM", "HIGH").contains(riskLevel)) {
            throw new IllegalArgumentException("unsupported riskLevel: " + riskLevel);
        }
        String triggerMode = optionalString(rawApi, "triggerMode", "model_decide");
        if (!"model_decide".equals(triggerMode)) {
            throw new IllegalArgumentException("only triggerMode=model_decide is supported");
        }
        if ("HIGH".equals(riskLevel) && !Boolean.TRUE.equals(rawApi.get("confirmationRequired"))) {
            throw new IllegalArgumentException("riskLevel=HIGH requires confirmationRequired=true");
        }
        String method = requiredString(rawApi, "method").toUpperCase(Locale.ROOT);
        if (!METHODS.contains(method)) {
            throw new IllegalArgumentException("unsupported HTTP method: " + method);
        }
        String url = requiredString(rawApi, "url");
        validateUrl(url, true);
        String authRef = trimToNull(stringValue(rawApi.get("authRef")));
        if (authRef != null && !authRef.matches("[a-zA-Z0-9_.:-]{1,128}")) {
            throw new IllegalArgumentException("authRef format is invalid");
        }
        validateAuthRef(companyId, authRef);
        Map<String, Object> inputSchema = getObjectMap(rawApi.get("inputSchema"));
        validateInputSchema(inputSchema);
        Map<String, Object> requestTemplate = getObjectMap(rawApi.get("request"));
        Set<String> properties = getObjectMap(inputSchema.get("properties")).keySet();
        validateTemplateReferences(url, properties);
        validateTemplateReferences(requestTemplate, properties);

        Map<String, Object> response = getObjectMap(rawApi.get("response"));
        Map<String, Object> responseMapping = new LinkedHashMap<>();
        responseMapping.put("resultPath", optionalString(response, "resultPath", "$"));
        responseMapping.put("maxItems", intValue(response.get("maxItems"), 50));
        responseMapping.put("maxBytes", intValue(response.get("maxBytes"), 12000));
        responseMapping.put("redactPaths", toStringList(response.get("redactPaths")));

        String toolName = TOOL_PREFIX + skillCode.replaceAll("[^a-zA-Z0-9_]", "_") + "__" + apiCode;
        Map<String, Object> toolDefinition = Map.of(
                "type", "function",
                "function", Map.of(
                        "name", toolName,
                        "description", description,
                        "parameters", inputSchema
                )
        );
        Map<String, Object> executionPlan = new LinkedHashMap<>();
        executionPlan.put("toolName", toolName);
        executionPlan.put("skillCode", skillCode);
        executionPlan.put("apiCode", apiCode);
        executionPlan.put("method", method);
        executionPlan.put("url", url);
        executionPlan.put("authRef", authRef);
        executionPlan.put("timeoutSeconds", intValue(rawApi.get("timeoutSeconds"), 10));
        executionPlan.put("requestTemplate", requestTemplate);
        executionPlan.put("responseMapping", responseMapping);
        try {
            return new CompiledApi(
                    apiCode,
                    displayName,
                    description,
                    riskLevel,
                    triggerMode,
                    toolName,
                    objectMapper.writeValueAsString(inputSchema),
                    objectMapper.writeValueAsString(executionPlan),
                    toolDefinition
            );
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to serialize compiled API");
        }
    }

    private HttpRequest buildHttpRequest(String companyId, String userId, Map<String, Object> plan, Map<String, Object> args) throws IOException {
        String method = requiredString(plan, "method");
        String renderedUrl = renderString(requiredString(plan, "url"), args);
        Map<String, Object> template = getObjectMap(plan.get("requestTemplate"));
        Map<String, Object> query = renderMap(getObjectMap(template.get("query")), args);
        if (!query.isEmpty()) {
            renderedUrl = appendQuery(renderedUrl, query);
        }
        validateUrl(renderedUrl, false);
        URI uri = URI.create(renderedUrl);
        int timeout = Math.max(1, Math.min(30, intValue(plan.get("timeoutSeconds"), 10)));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(timeout));
        Map<String, Object> headers = renderMap(getObjectMap(template.get("headers")), args);
        headers.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                builder.header(key, String.valueOf(value));
            }
        });
        Map<String, String> authHeaders = resolveAuthHeaders(companyId, userId, trimToNull(stringValue(plan.get("authRef"))), headers.keySet());
        authHeaders.forEach(builder::header);
        Object renderedBody = renderValue(template.get("body"), args);
        if ("GET".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            String body = renderedBody == null ? "{}" : objectMapper.writeValueAsString(renderedBody);
            if (!headers.keySet().stream().map(String::toLowerCase).toList().contains("content-type")) {
                builder.header("Content-Type", "application/json");
            }
            builder.method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private void validateAuthRef(String companyId, String authRef) {
        if (authRef == null) {
            return;
        }
        if (isTavilyApiKeyRef(authRef)) {
            if (resolveTavilyApiKey(companyId) == null) {
                throw new IllegalArgumentException("authRef integration:tavily.apiKey is not configured");
            }
            return;
        }
        if (isCloudccAccessTokenRef(authRef)) {
            if (integrationAppService.findRawConfig(companyId, IntegrationAppService.APP_CODE_CLOUDCC_CRM).isEmpty()) {
                throw new IllegalArgumentException("authRef integration:cloudcc.accessToken is not configured");
            }
            return;
        }
        throw new IllegalArgumentException("unsupported authRef: " + authRef);
    }

    private Map<String, String> resolveAuthHeaders(String companyId,
                                                   String userId,
                                                   String authRef,
                                                   Collection<String> existingHeaderNames) {
        if (authRef == null) {
            return Map.of();
        }
        if (isTavilyApiKeyRef(authRef)) {
            if (containsHeader(existingHeaderNames, "Authorization")) {
                throw new IllegalArgumentException("authRef cannot be combined with request.headers.Authorization");
            }
            String apiKey = resolveTavilyApiKey(companyId);
            if (apiKey == null) {
                throw new IllegalArgumentException("authRef integration:tavily.apiKey is not configured");
            }
            return Map.of("Authorization", "Bearer " + apiKey);
        }
        if (isCloudccAccessTokenRef(authRef)) {
            if (containsHeader(existingHeaderNames, "accessToken")) {
                throw new IllegalArgumentException("authRef cannot be combined with request.headers.accessToken");
            }
            CloudccAccessTokenService.CloudccSessionContext ctx = cloudccAccessTokenService
                    .getSessionContext(companyId, userId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "authRef integration:cloudcc.accessToken is not available for current user"));
            return Map.of("accessToken", ctx.accessToken());
        }
        throw new IllegalArgumentException("unsupported authRef: " + authRef);
    }

    private boolean isTavilyApiKeyRef(String authRef) {
        return "integration:tavily.apiKey".equals(authRef) || "tavily.apiKey".equals(authRef);
    }

    private boolean isCloudccAccessTokenRef(String authRef) {
        return "integration:cloudcc.accessToken".equals(authRef)
                || "cloudcc.accessToken".equals(authRef)
                || "integration:cloudcc.userToken".equals(authRef)
                || "cloudcc.userToken".equals(authRef);
    }

    private String resolveTavilyApiKey(String companyId) {
        return integrationAppService.findRawConfig(companyId, IntegrationAppService.APP_CODE_TAVILY)
                .flatMap(integrationAppService::decryptTavilyApiKey)
                .orElse(null);
    }

    private boolean containsHeader(Collection<String> headers, String name) {
        if (headers == null || name == null) {
            return false;
        }
        return headers.stream().anyMatch(item -> item != null && item.equalsIgnoreCase(name));
    }

    private Object extractAndShapeResponse(Map<String, Object> plan, byte[] body) throws IOException {
        Map<String, Object> mapping = getObjectMap(plan.get("responseMapping"));
        String text = new String(body, StandardCharsets.UTF_8);
        JsonNode root;
        try {
            root = objectMapper.readTree(text);
        } catch (Exception ex) {
            return text;
        }
        JsonNode extracted = extractPath(root, optionalString(mapping, "resultPath", "$"));
        JsonNode redacted = extracted.deepCopy();
        for (String redactPath : toStringList(mapping.get("redactPaths"))) {
            redact(redacted, redactPath);
        }
        int maxItems = intValue(mapping.get("maxItems"), 50);
        if (redacted.isArray() && redacted.size() > maxItems) {
            ArrayNode clipped = objectMapper.createArrayNode();
            for (int i = 0; i < maxItems; i++) {
                clipped.add(redacted.get(i));
            }
            redacted = clipped;
        }
        return objectMapper.convertValue(redacted, Object.class);
    }

    private JsonNode extractPath(JsonNode root, String path) {
        if (path == null || path.isBlank() || "$".equals(path)) {
            return root;
        }
        if (!path.startsWith("$.")) {
            return root;
        }
        JsonNode current = root;
        for (String part : path.substring(2).split("\\.")) {
            if (current == null || current.isMissingNode()) {
                return objectMapper.nullNode();
            }
            current = current.path(part);
        }
        return current == null ? objectMapper.nullNode() : current;
    }

    private void redact(JsonNode node, String redactPath) {
        if (node == null || redactPath == null || !redactPath.startsWith("$..")) {
            return;
        }
        String key = redactPath.substring(3);
        if (key.isBlank()) {
            return;
        }
        redactKey(node, key);
    }

    private void redactKey(JsonNode node, String key) {
        if (node instanceof ObjectNode objectNode) {
            if (objectNode.has(key)) {
                objectNode.put(key, "[REDACTED]");
            }
            objectNode.fields().forEachRemaining(entry -> redactKey(entry.getValue(), key));
        } else if (node.isArray()) {
            node.forEach(child -> redactKey(child, key));
        }
    }

    private void validateArguments(Map<String, Object> inputSchema, Map<String, Object> args) {
        Map<String, Object> props = getObjectMap(inputSchema.get("properties"));
        List<String> required = toStringList(inputSchema.get("required"));
        for (String field : required) {
            if (!args.containsKey(field) || args.get(field) == null || String.valueOf(args.get(field)).isBlank()) {
                throw new IllegalArgumentException("missing required argument: " + field);
            }
        }
        for (String key : args.keySet()) {
            if (!props.containsKey(key)) {
                throw new IllegalArgumentException("argument is not declared in inputSchema: " + key);
            }
            String type = optionalString(getObjectMap(props.get(key)), "type", "string");
            Object value = args.get(key);
            if (!matchesType(type, value)) {
                throw new IllegalArgumentException("argument type mismatch: " + key);
            }
        }
    }

    private boolean matchesType(String type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "number" -> value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            default -> false;
        };
    }

    private void validateInputSchema(Map<String, Object> inputSchema) {
        if (!"object".equals(inputSchema.get("type"))) {
            throw new IllegalArgumentException("inputSchema must be an object schema");
        }
        Map<String, Object> props = getObjectMap(inputSchema.get("properties"));
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            if (!entry.getKey().matches("[a-zA-Z0-9_]{1,64}")) {
                throw new IllegalArgumentException("inputSchema property name is invalid: " + entry.getKey());
            }
            String type = optionalString(getObjectMap(entry.getValue()), "type", "string");
            if (!SCHEMA_TYPES.contains(type)) {
                throw new IllegalArgumentException("unsupported inputSchema type: " + type);
            }
        }
        for (String required : toStringList(inputSchema.get("required"))) {
            if (!props.containsKey(required)) {
                throw new IllegalArgumentException("required field not defined in properties: " + required);
            }
        }
    }

    private void validateTemplateReferences(Object value, Set<String> allowedNames) {
        if (value == null) {
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.values().forEach(child -> validateTemplateReferences(child, allowedNames));
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(child -> validateTemplateReferences(child, allowedNames));
            return;
        }
        if (value instanceof String s) {
            Matcher matcher = TEMPLATE_REF.matcher(s);
            while (matcher.find()) {
                String name = matcher.group(1);
                if (!allowedNames.contains(name)) {
                    throw new IllegalArgumentException("request template references undeclared input: " + name);
                }
            }
        }
    }

    private Object renderValue(Object value, Map<String, Object> args) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, child) -> out.put(String.valueOf(key), renderValue(child, args)));
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(child -> renderValue(child, args)).toList();
        }
        if (value instanceof String s) {
            Matcher matcher = TEMPLATE_REF.matcher(s.trim());
            if (matcher.matches()) {
                return args.get(matcher.group(1));
            }
            return renderString(s, args);
        }
        return value;
    }

    private Map<String, Object> renderMap(Map<String, Object> map, Map<String, Object> args) {
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, value) -> out.put(key, renderValue(value, args)));
        return out;
    }

    private String renderString(String raw, Map<String, Object> args) {
        Matcher matcher = TEMPLATE_REF.matcher(raw);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = args.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String appendQuery(String url, Map<String, Object> query) {
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                builder.append("&");
            }
            first = false;
            builder.append(urlEncode(entry.getKey()));
            builder.append("=");
            builder.append(urlEncode(String.valueOf(entry.getValue())));
        }
        return builder.toString();
    }

    private void validateUrl(String rawUrl, boolean publishTime) {
        URI uri;
        try {
            uri = URI.create(rawUrl.replaceAll("\\{\\{\\s*[a-zA-Z0-9_]+\\s*}}", "sample"));
        } catch (Exception ex) {
            throw new IllegalArgumentException("url is invalid");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!List.of("https", "http").contains(scheme)) {
            throw new IllegalArgumentException("url must use HTTP(S)");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (host.isBlank()) {
            throw new IllegalArgumentException("url host is required");
        }
        if (!allowedHosts.contains("*") && !allowedHosts.contains(host)) {
            throw new IllegalArgumentException("url host is not allowed: " + host);
        }
        if (isUnsafeHost(host) && !allowLocalhost) {
            throw new IllegalArgumentException("url host is private or unsafe: " + host);
        }
        if ("http".equals(scheme) && !allowLocalhost) {
            throw new IllegalArgumentException("url must use HTTPS");
        }
        if (publishTime && uri.getRawQuery() != null) {
            throw new IllegalArgumentException("put query parameters in request.query, not url");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private boolean isUnsafeHost(String host) {
        return "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.startsWith("127.")
                || host.equals("0.0.0.0")
                || host.equals("::1")
                || host.startsWith("10.")
                || host.startsWith("192.168.")
                || host.startsWith("169.254.")
                || host.matches("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")
                || host.toLowerCase(Locale.ROOT).startsWith("fc")
                || host.toLowerCase(Locale.ROOT).startsWith("fd")
                || host.toLowerCase(Locale.ROOT).startsWith("fe80");
    }

    private void audit(String companyId,
                       String userId,
                       SkillApiToolEntity entity,
                       String status,
                       int httpStatus,
                       long startedNanos,
                       String argumentsJson,
                       String error) {
        try {
            long elapsedMs = Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
            Map<String, Object> args = argumentsJson == null || argumentsJson.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("timestamp", Instant.now().toString());
            detail.put("skillCode", entity.getSkillCode());
            detail.put("skillId", entity.getSkillId());
            detail.put("skillVersionId", entity.getSkillVersionId());
            detail.put("apiCode", entity.getApiCode());
            detail.put("toolName", entity.getToolName());
            detail.put("status", status);
            detail.put("httpStatus", httpStatus);
            detail.put("elapsedMs", elapsedMs);
            detail.put("argumentKeys", args.keySet());
            if (error != null && !error.isBlank()) {
                detail.put("error", error.length() > 300 ? error.substring(0, 300) : error);
            }
            auditService.log(companyId, userId, "SKILL_API_TOOL_INVOCATION", objectMapper.writeValueAsString(detail));
        } catch (Exception ignored) {
            // Runtime API execution must not fail because audit persistence failed.
        }
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> getObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        map.forEach((key, child) -> {
            if (key != null) {
                out.put(key.toString(), child);
            }
        });
        return out;
    }

    private Object mapValue(Map<String, Object> map, String objectKey, String fieldKey) {
        return getObjectMap(map.get(objectKey)).get(fieldKey);
    }

    private String requiredString(Map<String, Object> map, String field) {
        String value = trimToNull(stringValue(map.get(field)));
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private String optionalString(Map<String, Object> map, String field, String fallback) {
        return trimToNull(stringValue(map.get(field))) == null ? fallback : trimToNull(stringValue(map.get(field)));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (Exception ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Set<String> parseHosts(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .map(item -> item.toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record RuntimeApiCompilePreview(
            List<Map<String, Object>> toolDefinitions,
            List<String> errors,
            List<String> warnings
    ) {
    }

    public record ResolvedSkillApiTool(
            String skillCode,
            Long skillVersionId,
            String apiCode,
            String toolName,
            String description,
            String riskLevel,
            Map<String, Object> inputSchema
    ) {
    }

    private record CompiledApi(
            String apiCode,
            String displayName,
            String description,
            String riskLevel,
            String triggerMode,
            String toolName,
            String inputSchemaJson,
            String executionPlanJson,
            Map<String, Object> toolDefinition
    ) {
    }
}
