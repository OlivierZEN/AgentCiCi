package com.codehouse.ciciassistant.tool.tavily;

import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.tool.tavily.dto.TavilyExtractRequest;
import com.codehouse.ciciassistant.tool.tavily.dto.TavilySearchRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central service for the {@code tavily_search} and {@code tavily_extract} builtin tools.
 *
 * <p>Resolves the per-tenant API key from {@code integration_app(code="tavily")} —
 * there is <b>no environment / yaml fallback</b>. Produces OpenAI-style function schemas
 * for {@link com.codehouse.ciciassistant.ai.service.ToolOrchestratorService} and
 * dispatches execution via {@link TavilyClient}.
 */
@Service
public class TavilyToolService {

    private static final Logger log = LoggerFactory.getLogger(TavilyToolService.class);

    public static final String TOOL_SEARCH = "tavily_search";
    public static final String TOOL_EXTRACT = "tavily_extract";
    public static final List<String> ALL_TOOL_NAMES = List.of(TOOL_SEARCH, TOOL_EXTRACT);

    private static final Set<String> ALLOWED_SEARCH_DEPTH = Set.of("ultra-fast", "fast", "basic", "advanced");
    private static final Set<String> ALLOWED_TOPIC = Set.of("general", "news", "finance");
    private static final Set<String> ALLOWED_TIME_RANGE = Set.of("day", "week", "month", "year");
    // Tavily accepts true/false (boolean) or "basic"/"advanced" (string).
    // We only expose the string variants; null/omitted = Tavily default (false, no answer).
    private static final Set<String> ALLOWED_INCLUDE_ANSWER = Set.of("basic", "advanced");
    private static final Set<String> ALLOWED_INCLUDE_RAW_CONTENT = Set.of("none", "markdown", "text");
    private static final Set<String> ALLOWED_EXTRACT_DEPTH = Set.of("basic", "advanced");
    private static final Set<String> ALLOWED_EXTRACT_FORMAT = Set.of("markdown", "text");

    private final TavilyClient client;
    private final TavilyProperties properties;
    private final IntegrationAppService integrationAppService;
    private final ObjectMapper objectMapper;

    public TavilyToolService(TavilyClient client,
                             TavilyProperties properties,
                             IntegrationAppService integrationAppService,
                             ObjectMapper objectMapper) {
        this.client = client;
        this.properties = properties;
        this.integrationAppService = integrationAppService;
        this.objectMapper = objectMapper;
    }

    // =============================================================================================
    // Tool definitions (OpenAI function-calling schemas)
    // =============================================================================================

    public List<Map<String, Object>> toolDefinitions() {
        return List.of(toolDefinition(TOOL_SEARCH), toolDefinition(TOOL_EXTRACT));
    }

    public Map<String, Object> toolDefinition(String toolName) {
        return switch (toolName) {
            case TOOL_SEARCH -> functionTool(TOOL_SEARCH, searchDescription(), searchSchema());
            case TOOL_EXTRACT -> functionTool(TOOL_EXTRACT, extractDescription(), extractSchema());
            default -> throw new IllegalArgumentException("Unknown Tavily tool: " + toolName);
        };
    }

    private Map<String, Object> functionTool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "function");
        out.put("function", function);
        return out;
    }

    private String searchDescription() {
        return "Search the public web via Tavily and return LLM-optimized results with snippets, "
                + "relevance scores and source URLs. Use when the user asks for fresh, external or "
                + "verifiable online information that is unlikely to be covered by the tenant knowledge base.";
    }

    private String extractDescription() {
        return "Fetch one or more URLs via Tavily and return cleaned, readable page content "
                + "(markdown or plain text). Use after tavily_search when you need the full body of a "
                + "specific page, or when the user pastes a URL.";
    }

    private Map<String, Object> searchSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("query", primitive("string", "Search query. Keep it under 400 characters; use search-style phrasing rather than a long prompt."));
        properties.put("search_depth", enumProp(ALLOWED_SEARCH_DEPTH, "Tavily search depth; default basic."));
        Map<String, Object> maxResults = new LinkedHashMap<>();
        maxResults.put("type", "integer");
        maxResults.put("minimum", 1);
        maxResults.put("maximum", 20);
        maxResults.put("description", "Max results (1-20, default 5)");
        properties.put("max_results", maxResults);
        properties.put("topic", enumProp(ALLOWED_TOPIC, "Topic preset (general / news / finance)."));
        properties.put("time_range", enumProp(ALLOWED_TIME_RANGE, "Recency filter (day / week / month / year)."));
        properties.put("start_date", Map.of("type", "string", "description", "Inclusive lower bound in YYYY-MM-DD."));
        properties.put("end_date", Map.of("type", "string", "description", "Inclusive upper bound in YYYY-MM-DD."));
        properties.put("include_domains", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Allow list of domains to include (e.g. ['github.com'])."));
        properties.put("exclude_domains", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "Deny list of domains to exclude."));
        properties.put("country", Map.of("type", "string", "description", "ISO country hint, e.g. 'cn' or 'us'."));
        properties.put("include_answer", enumProp(ALLOWED_INCLUDE_ANSWER, "Whether Tavily should include a pre-answer (basic / advanced). Omit to skip."));
        properties.put("include_raw_content", enumProp(ALLOWED_INCLUDE_RAW_CONTENT, "Attach raw page text per result (none / markdown / text)."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("query"));
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> extractSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> urls = new LinkedHashMap<>();
        urls.put("type", "array");
        urls.put("minItems", 1);
        urls.put("maxItems", 20);
        urls.put("items", Map.of("type", "string"));
        urls.put("description", "One or more URLs to extract readable content from (max 20).");
        properties.put("urls", urls);
        properties.put("format", enumProp(ALLOWED_EXTRACT_FORMAT, "Output format (markdown default or text)."));
        properties.put("extract_depth", enumProp(ALLOWED_EXTRACT_DEPTH, "Extraction depth (basic default or advanced)."));
        properties.put("include_images", Map.of("type", "boolean", "description", "Include inline image URLs."));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("required", List.of("urls"));
        schema.put("properties", properties);
        return schema;
    }

    private Map<String, Object> primitive(String type, String description) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("description", description);
        return out;
    }

    private Map<String, Object> enumProp(Set<String> values, String description) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "string");
        out.put("enum", List.copyOf(values));
        out.put("description", description);
        return out;
    }

    // =============================================================================================
    // Dispatch
    // =============================================================================================

    public String dispatch(String companyId, String userId, String toolName, String argumentsJson) {
        try {
            return switch (toolName) {
                case TOOL_SEARCH -> invokeSearch(companyId, userId, argumentsJson);
                case TOOL_EXTRACT -> invokeExtract(companyId, userId, argumentsJson);
                default -> toJson(errorResult("TAVILY_UNKNOWN_TOOL", "Unsupported Tavily tool: " + toolName));
            };
        } catch (Exception ex) {
            log.error("Tavily dispatch failed: tool={} err={}", toolName, ex.getMessage(), ex);
            return toJson(errorResult("TAVILY_INTERNAL_ERROR", ex.getMessage()));
        }
    }

    private String invokeSearch(String companyId, String userId, String argumentsJson) throws Exception {
        JsonNode args = argumentsJson == null || argumentsJson.isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(argumentsJson);
        String query = clampQuery(text(args, "query"));
        if (query == null || query.isEmpty()) {
            return toJson(errorResult("TAVILY_BAD_REQUEST", "query 不能为空"));
        }

        String apiKey = resolveApiKey(companyId);
        if (apiKey == null) {
            return toJson(errorResult("TAVILY_NOT_CONFIGURED",
                    "管理员尚未在「集成应用 → Tavily」配置 API Key；请配置后再试。"));
        }

        TavilySearchRequest req = new TavilySearchRequest(
                null,
                query,
                enumOrDefault(text(args, "search_depth"), ALLOWED_SEARCH_DEPTH, properties.defaultSearchDepth()),
                clampMaxResults(args.path("max_results")),
                enumOrDefault(text(args, "topic"), ALLOWED_TOPIC, properties.defaultTopic()),
                enumOrNull(text(args, "time_range"), ALLOWED_TIME_RANGE),
                text(args, "start_date"),
                text(args, "end_date"),
                stringList(args.path("include_domains")),
                stringList(args.path("exclude_domains")),
                text(args, "country"),
                enumOrNull(text(args, "include_answer"), ALLOWED_INCLUDE_ANSWER),
                enumOrNull(text(args, "include_raw_content"), ALLOWED_INCLUDE_RAW_CONTENT)
        );

        TavilyClient.TavilyCallResult<Map<String, Object>> result = client.search(apiKey, req);
        if (!result.ok()) {
            log.warn("tavily_search failed org={} user={} code={} latencyMs={}",
                    companyId, userId, result.errorCode(), result.latencyMs());
            return toJson(errorResult(result.errorCode(), result.errorMessage()));
        }

        Map<String, Object> shaped = shapeSearchResponse(result.data());
        log.info("tavily_search ok org={} user={} queryLen={} results={} latencyMs={}",
                companyId, userId, query.length(),
                shaped.get("resultCount"), result.latencyMs());
        return toJson(shaped);
    }

    private String invokeExtract(String companyId, String userId, String argumentsJson) throws Exception {
        JsonNode args = argumentsJson == null || argumentsJson.isBlank()
                ? objectMapper.createObjectNode()
                : objectMapper.readTree(argumentsJson);
        List<String> urls = stringList(args.path("urls"));
        if (urls == null || urls.isEmpty()) {
            return toJson(errorResult("TAVILY_BAD_REQUEST", "urls 至少需要 1 条"));
        }
        if (urls.size() > 20) {
            urls = urls.subList(0, 20);
        }

        String apiKey = resolveApiKey(companyId);
        if (apiKey == null) {
            return toJson(errorResult("TAVILY_NOT_CONFIGURED",
                    "管理员尚未在「集成应用 → Tavily」配置 API Key；请配置后再试。"));
        }

        TavilyExtractRequest req = new TavilyExtractRequest(
                null,
                urls,
                enumOrDefault(text(args, "format"), ALLOWED_EXTRACT_FORMAT, properties.defaultExtractFormat()),
                enumOrDefault(text(args, "extract_depth"), ALLOWED_EXTRACT_DEPTH, "basic"),
                args.path("include_images").isBoolean() ? args.path("include_images").asBoolean() : null
        );

        TavilyClient.TavilyCallResult<Map<String, Object>> result = client.extract(apiKey, req);
        if (!result.ok()) {
            log.warn("tavily_extract failed org={} user={} code={} latencyMs={}",
                    companyId, userId, result.errorCode(), result.latencyMs());
            return toJson(errorResult(result.errorCode(), result.errorMessage()));
        }

        Map<String, Object> shaped = shapeExtractResponse(result.data());
        log.info("tavily_extract ok org={} user={} urls={} latencyMs={}",
                companyId, userId, urls.size(), result.latencyMs());
        return toJson(shaped);
    }

    // =============================================================================================
    // API key resolution
    // =============================================================================================

    /**
     * Resolves the plaintext API key for the given org from {@code integration_app('tavily')}.
     * Returns {@code null} when the integration is missing, disabled, or has no apiKey.
     * Never falls back to env / yaml.
     */
    public String resolveApiKey(String companyId) {
        return integrationAppService.findRawConfig(companyId, IntegrationAppService.APP_CODE_TAVILY)
                .flatMap(integrationAppService::decryptTavilyApiKey)
                .orElse(null);
    }

    // =============================================================================================
    // Health check (used by /integrations/tavily/test)
    // =============================================================================================

    /** Runs a minimal {@code tavily_search query=ping max_results=1} request and returns a result envelope. */
    public Map<String, Object> testConnection(String companyId, String overrideApiKey) {
        String apiKey = (overrideApiKey == null || overrideApiKey.isBlank())
                ? resolveApiKey(companyId)
                : overrideApiKey.trim();
        if (apiKey == null) {
            return errorResult("TAVILY_NOT_CONFIGURED",
                    "管理员尚未在「集成应用 → Tavily」配置 API Key");
        }
        TavilySearchRequest req = new TavilySearchRequest(
                null, "ping", "basic", 1, "general",
                null, null, null, null, null, null, null, null);
        TavilyClient.TavilyCallResult<Map<String, Object>> result = client.search(apiKey, req);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", result.ok());
        out.put("latencyMs", result.latencyMs());
        if (result.ok()) {
            Object results = result.data() == null ? List.of() : result.data().getOrDefault("results", List.of());
            int count = results instanceof List<?> l ? l.size() : 0;
            out.put("resultCount", count);
            return out;
        }
        out.put("code", result.errorCode());
        out.put("message", result.errorMessage());
        return out;
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    private Map<String, Object> shapeSearchResponse(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        if (raw == null) {
            out.put("answer", null);
            out.put("results", List.of());
            out.put("resultCount", 0);
            return out;
        }
        out.put("answer", raw.get("answer"));
        Object responseTime = raw.get("response_time");
        if (responseTime != null) {
            out.put("responseTime", responseTime);
        }
        List<Map<String, Object>> shaped = new ArrayList<>();
        Object rawResults = raw.get("results");
        if (rawResults instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("title", map.get("title"));
                row.put("url", map.get("url"));
                Object content = map.get("content");
                if (content instanceof String s) {
                    row.put("snippet", s.length() > 500 ? s.substring(0, 500) + "…" : s);
                } else {
                    row.put("snippet", content);
                }
                row.put("score", map.get("score"));
                if (map.get("published_date") != null) {
                    row.put("publishedDate", map.get("published_date"));
                }
                if (map.get("raw_content") instanceof String raw2) {
                    String truncated = raw2.length() > properties.maxExtractChars()
                            ? raw2.substring(0, properties.maxExtractChars()) + "…" : raw2;
                    row.put("rawContent", truncated);
                    row.put("rawContentLength", raw2.length());
                }
                shaped.add(row);
            }
        }
        out.put("results", shaped);
        out.put("resultCount", shaped.size());
        return out;
    }

    private Map<String, Object> shapeExtractResponse(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        List<Map<String, Object>> shaped = new ArrayList<>();
        if (raw != null && raw.get("results") instanceof List<?> list) {
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("url", map.get("url"));
                Object content = map.get("raw_content");
                if (!(content instanceof String)) {
                    content = map.get("content");
                }
                if (content instanceof String s) {
                    String truncated = s.length() > properties.maxExtractChars()
                            ? s.substring(0, properties.maxExtractChars()) + "…" : s;
                    row.put("content", truncated);
                    row.put("rawContentLength", s.length());
                } else {
                    row.put("content", content);
                }
                shaped.add(row);
            }
        }
        out.put("results", shaped);
        if (raw != null && raw.get("failed_results") instanceof List<?> failed) {
            out.put("failedResults", failed);
        }
        out.put("resultCount", shaped.size());
        return out;
    }

    private Map<String, Object> errorResult(String code, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        out.put("code", code == null ? "TAVILY_INTERNAL_ERROR" : code);
        out.put("message", message == null ? "" : message);
        return out;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"success\":false,\"code\":\"TAVILY_SERIALIZE_ERROR\",\"message\":\"" + ex.getMessage() + "\"}";
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText("");
        return s.isBlank() ? null : s.trim();
    }

    private String clampQuery(String query) {
        if (query == null) return null;
        String trimmed = query.trim();
        if (trimmed.length() > 400) {
            return trimmed.substring(0, 400);
        }
        return trimmed;
    }

    private Integer clampMaxResults(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return properties.defaultMaxResults();
        }
        if (!node.isInt() && !node.isLong() && !node.isNumber()) {
            return properties.defaultMaxResults();
        }
        int raw = node.asInt(properties.defaultMaxResults());
        if (raw < 1) return 1;
        if (raw > 20) return 20;
        return raw;
    }

    private String enumOrDefault(String value, Set<String> allowed, String fallback) {
        if (value != null && allowed.contains(value)) {
            return value;
        }
        return fallback;
    }

    private String enumOrNull(String value, Set<String> allowed) {
        if (value != null && allowed.contains(value)) {
            return value;
        }
        return null;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        Iterator<JsonNode> it = node.elements();
        while (it.hasNext()) {
            JsonNode v = it.next();
            if (v == null || v.isNull()) continue;
            String s = v.asText("").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out.isEmpty() ? null : out;
    }

    // Unused optional import hook for tests that want to inject a specific apiKey.
    @SuppressWarnings("unused")
    Optional<String> _resolveApiKeyOptional(String companyId) {
        return Optional.ofNullable(resolveApiKey(companyId));
    }
}
