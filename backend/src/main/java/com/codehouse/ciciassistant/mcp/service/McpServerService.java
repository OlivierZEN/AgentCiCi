package com.codehouse.ciciassistant.mcp.service;

import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService.CloudccSessionContext;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.codehouse.ciciassistant.mcp.domain.McpServerRepository;
import com.codehouse.ciciassistant.mcp.service.McpClient.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class McpServerService {

    private static final Logger log = LoggerFactory.getLogger(McpServerService.class);

    private final McpServerRepository repository;
    private final McpClient mcpClient;
    private final CloudccAccessTokenService cloudccAccessTokenService;
    private final ObjectMapper objectMapper;

    /** serverId → cached tool list */
    private final ConcurrentHashMap<Long, List<McpTool>> toolCache = new ConcurrentHashMap<>();

    /** serverId → server entity (for tool dispatch) */
    private final ConcurrentHashMap<Long, McpServerEntity> serverCache = new ConcurrentHashMap<>();

    /** toolName → serverId (for fast reverse lookup during tool execution) */
    private final ConcurrentHashMap<String, Long> toolServerIndex = new ConcurrentHashMap<>();

    /** serverId → refresh lock */
    private final ConcurrentHashMap<Long, ReentrantLock> refreshLocks = new ConcurrentHashMap<>();

    public McpServerService(
            McpServerRepository repository,
            McpClient mcpClient,
            CloudccAccessTokenService cloudccAccessTokenService,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.mcpClient = mcpClient;
        this.cloudccAccessTokenService = cloudccAccessTokenService;
        this.objectMapper = objectMapper;
    }

    // ── CRUD ──

    public List<McpServerEntity> list(String orgId) {
        return repository.findByOrgIdOrderByIdDesc(orgId);
    }

    @Transactional
    public McpServerEntity create(String orgId, String name, String description,
                                  String transportType, String url, String headers,
                                  int timeoutSeconds) {
        McpServerEntity entity = new McpServerEntity(orgId, name, description,
                transportType, url, headers, timeoutSeconds);
        return repository.save(entity);
    }

    @Transactional
    public McpServerEntity update(String orgId, Long id, String name, String description,
                                  String transportType, String url, String headers,
                                  int timeoutSeconds, boolean enabled) {
        McpServerEntity entity = repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        entity.setName(name);
        entity.setDescription(description);
        entity.setTransportType(transportType);
        entity.setUrl(url);
        entity.setHeaders(headers);
        entity.setTimeoutSeconds(timeoutSeconds);
        entity.setEnabled(enabled);
        clearDatabaseCache(entity);
        entity.touch();
        invalidateCache(id);
        return repository.save(entity);
    }

    @Transactional
    public void delete(String orgId, Long id) {
        McpServerEntity entity = repository.findByIdAndOrgId(id, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        invalidateCache(id);
        repository.delete(entity);
    }

    // ── Tool Discovery ──

    /**
     * Connect to the MCP server, initialize, and fetch tool definitions.
     * Results are cached until invalidated.
     */
    public List<McpTool> discoverTools(String orgId, Long serverId) throws Exception {
        return refreshToolCache(orgId, serverId).tools();
    }

    /**
     * Get cached tools for a server, or discover if not cached.
     */
    public List<McpTool> getTools(String orgId, Long serverId) {
        List<McpTool> cached = toolCache.get(serverId);
        if (cached != null) return cached;
        Optional<List<McpTool>> databaseCache = loadToolsFromDatabaseCache(orgId, serverId);
        if (databaseCache.isPresent()) {
            return databaseCache.get();
        }
        try {
            return refreshToolCache(orgId, serverId).tools();
        } catch (Exception e) {
            log.warn("Failed to auto-discover MCP tools for server {}: {}", serverId, e.getMessage());
            return Collections.emptyList();
        }
    }

    public ToolCacheSnapshot getToolCacheSnapshot(String orgId, Long serverId) {
        McpServerEntity server = repository.findByIdAndOrgId(serverId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        List<McpTool> tools = getTools(orgId, serverId);
        return snapshotOf(server, tools);
    }

    @Transactional
    public ToolCacheSnapshot refreshToolCache(String orgId, Long serverId) throws Exception {
        McpServerEntity server = repository.findByIdAndOrgId(serverId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        ReentrantLock lock = refreshLocks.computeIfAbsent(serverId, ignored -> new ReentrantLock());
        if (!lock.tryLock()) {
            ToolCacheSnapshot current = getToolCacheSnapshot(orgId, serverId);
            return new ToolCacheSnapshot(
                    current.serverId(),
                    current.cacheCount(),
                    "refreshing",
                    current.cacheUpdatedAt(),
                    current.cacheLastAttemptAt(),
                    current.cacheErrorMessage(),
                    current.cacheVersion(),
                    current.tools()
            );
        }
        try {
            server.setToolCacheStatus("refreshing");
            server.setToolCacheLastAttemptAt(Instant.now());
            server.touch();
            repository.save(server);

            Map<String, String> extraHeaders = resolveDynamicHeaders(server, orgId, null);
            mcpClient.initialize(server, extraHeaders);
            List<McpTool> tools = mcpClient.listTools(server, extraHeaders);

            server.setToolCacheJson(objectMapper.writeValueAsString(tools));
            server.setToolCacheCount(tools.size());
            server.setToolCacheStatus("ready");
            server.setToolCacheErrorMessage(null);
            server.setToolCacheUpdatedAt(Instant.now());
            server.setToolCacheLastAttemptAt(Instant.now());
            server.setToolCacheVersion(computeToolCacheVersion(tools));
            server.touch();
            repository.save(server);

            replaceHotCache(serverId, server, tools);
            log.info("Discovered {} tools from MCP server '{}' (id={})", tools.size(), server.getName(), serverId);
            return snapshotOf(server, tools);
        } catch (Exception e) {
            log.warn("Failed to refresh tools for MCP server {}: {}", serverId, e.getMessage());
            server.setToolCacheStatus("error");
            server.setToolCacheErrorMessage(truncateError(e.getMessage()));
            server.setToolCacheLastAttemptAt(Instant.now());
            server.touch();
            repository.save(server);
            throw e;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Get ALL tools from all enabled MCP servers for an org.
     * Each tool name is prefixed with serverId to avoid collisions.
     */
    public List<ResolvedTool> getAllToolsForOrg(String orgId) {
        List<McpServerEntity> servers = repository.findByOrgIdAndEnabledTrue(orgId);
        List<ResolvedTool> allTools = new ArrayList<>();
        for (McpServerEntity server : servers) {
            List<McpTool> tools = getTools(orgId, server.getId());
            for (McpTool tool : tools) {
                allTools.add(new ResolvedTool(
                        server.getId(),
                        server.getName(),
                        tool.name(),
                        tool.description(),
                        tool.inputSchema()
                ));
            }
        }
        return allTools;
    }

    // ── Tool Execution ──

    /**
     * Execute a tool by name on the appropriate MCP server.
     */
    public String executeTool(String orgId, String userId, String toolName, String argumentsJson) {
        List<McpServerEntity> servers = repository.findByOrgIdAndEnabledTrue(orgId);
        for (McpServerEntity server : servers) {
            List<McpTool> tools = getTools(orgId, server.getId());
            for (McpTool tool : tools) {
                if (tool.name().equals(toolName)) {
                    try {
                        Map<String, String> extraHeaders = resolveDynamicHeaders(server, orgId, userId);
                        String args = argumentsJson;
                        if (isCloudccRelatedServer(server)) {
                            Optional<CloudccSessionContext> ctx =
                                    cloudccAccessTokenService.getSessionContext(orgId, userId);
                            if (ctx.isEmpty()) {
                                return "CloudCC 调用失败：无法获取访问令牌。请在「集成应用」中启用并配置 CloudCC CRM，"
                                        + "并在用户资料中绑定 CloudCC 用户名与安全码后再试。";
                            }
                            // Only inject credential fields that the tool's schema actually declares.
                            // Tools that authenticate via HTTP headers do not declare open_api_token /
                            // base_url as parameters, so injecting them causes Pydantic validation
                            // errors on the MCP server side.
                            args = mergeCloudccToolArguments(args, ctx.get(), tool.inputSchema());
                        }
                        String result = mcpClient.callTool(server, toolName, args, extraHeaders);
                        result = rewriteKnownToolFailure(toolName, result);
                        if (isCloudccRelatedServer(server) && isCloudccAuthFailure(result)) {
                            String refreshed = retryCloudccToolOnce(orgId, userId, server, toolName, argumentsJson);
                            if (refreshed != null) {
                                return refreshed;
                            }
                        }
                        return result;
                    } catch (Exception e) {
                        if (isCloudccRelatedServer(server) && isCloudccAuthFailure(e.getMessage())) {
                            String refreshed = retryCloudccToolOnce(orgId, userId, server, toolName, argumentsJson);
                            if (refreshed != null) {
                                return refreshed;
                            }
                        }
                        log.error("MCP tool call failed: server={}, tool={}, error={}",
                                server.getName(), toolName, e.getMessage());
                        return "Tool execution failed: " + e.getMessage();
                    }
                }
            }
        }
        return "Tool not found: " + toolName;
    }

    // ── Health Check ──

    public Map<String, Object> healthCheck(String orgId, Long serverId) throws Exception {
        McpServerEntity server = repository.findByIdAndOrgId(serverId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        Map<String, String> extraHeaders = resolveDynamicHeaders(server, orgId, null);
        return mcpClient.healthCheck(server, extraHeaders);
    }

    // ── Cache ──

    public void invalidateCache(Long serverId) {
        List<McpTool> removed = toolCache.remove(serverId);
        serverCache.remove(serverId);
        if (removed != null) {
            for (McpTool tool : removed) {
                toolServerIndex.remove(qualifiedName(serverId, tool.name()));
            }
        }
        mcpClient.clearSession(serverId);
    }

    private void replaceHotCache(Long serverId, McpServerEntity server, List<McpTool> tools) {
        List<McpTool> previous = toolCache.put(serverId, tools);
        if (previous != null) {
            for (McpTool tool : previous) {
                toolServerIndex.remove(qualifiedName(serverId, tool.name()));
            }
        }
        serverCache.put(serverId, server);
        for (McpTool tool : tools) {
            toolServerIndex.put(qualifiedName(serverId, tool.name()), serverId);
        }
    }

    private Optional<List<McpTool>> loadToolsFromDatabaseCache(String orgId, Long serverId) {
        McpServerEntity server = repository.findByIdAndOrgId(serverId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found"));
        if (!server.hasToolCache()) {
            return Optional.empty();
        }
        try {
            List<McpTool> tools = objectMapper.readerForListOf(McpTool.class).readValue(server.getToolCacheJson());
            replaceHotCache(serverId, server, tools);
            return Optional.of(tools);
        } catch (Exception ex) {
            log.warn("Failed to parse MCP tool cache json, serverId={}, error={}", serverId, ex.getMessage());
            toolCache.remove(serverId);
            serverCache.remove(serverId);
            server.setToolCacheStatus("error");
            server.setToolCacheErrorMessage("缓存解析失败，请手动刷新");
            server.setToolCacheLastAttemptAt(Instant.now());
            server.touch();
            repository.save(server);
            return Optional.empty();
        }
    }

    private ToolCacheSnapshot snapshotOf(McpServerEntity server, List<McpTool> tools) {
        return new ToolCacheSnapshot(
                server.getId(),
                server.getToolCacheCount(),
                normalizeStatus(server.getToolCacheStatus()),
                server.getToolCacheUpdatedAt(),
                server.getToolCacheLastAttemptAt(),
                server.getToolCacheErrorMessage(),
                server.getToolCacheVersion(),
                tools
        );
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "empty";
        }
        return status;
    }

    private String computeToolCacheVersion(List<McpTool> tools) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String payload = objectMapper.writeValueAsString(tools);
            byte[] bytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < bytes.length; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String truncateError(String message) {
        if (message == null || message.isBlank()) {
            return "unknown error";
        }
        if (message.length() <= 400) {
            return message;
        }
        return message.substring(0, 400);
    }

    private void clearDatabaseCache(McpServerEntity entity) {
        entity.setToolCacheJson(null);
        entity.setToolCacheCount(0);
        entity.setToolCacheStatus("empty");
        entity.setToolCacheUpdatedAt(null);
        entity.setToolCacheErrorMessage(null);
        entity.setToolCacheLastAttemptAt(null);
        entity.setToolCacheVersion(null);
    }

    private String qualifiedName(Long serverId, String toolName) {
        return serverId + "::" + toolName;
    }

    private Map<String, String> resolveDynamicHeaders(McpServerEntity server, String orgId, String userId) {
        if (!isCloudccRelatedServer(server)) {
            return Map.of();
        }
        String uid = userId;
        if (uid == null || uid.isBlank()) {
            uid = com.codehouse.ciciassistant.tenant.TenantContext.getUserId().orElse("");
        }
        if (uid.isBlank()) {
            return Map.of();
        }
        Optional<CloudccSessionContext> ctx = cloudccAccessTokenService.getSessionContext(orgId, uid);
        if (ctx.isEmpty()) {
            return Map.of();
        }
        return Map.of(
                "accessToken", ctx.get().accessToken(),
                "base_url", ensureUrlWithScheme(ctx.get().baseUrl())
        );
    }

    /**
     * Returns {@code true} when the tool's JSON-Schema {@code properties} object contains the
     * given field name. Used to guard against injecting credential arguments into tools that do
     * not declare them (those tools authenticate via HTTP headers instead).
     */
    private boolean toolSchemaAcceptsField(JsonNode inputSchema, String fieldName) {
        if (inputSchema == null) {
            return true; // unknown schema – fall back to always injecting (legacy behaviour)
        }
        JsonNode props = inputSchema.path("properties");
        if (props.isMissingNode() || !props.isObject()) {
            return true; // schema has no properties block – treat as accepting everything
        }
        return props.has(fieldName);
    }

    /**
     * CloudCC MCP tools expect {@code base_url} / {@code open_api_token} in tool arguments; the model cannot know them.
     * We inject them from {@link CloudccAccessTokenService}. Headers are also sent for transports that read them.
     */
    private boolean isCloudccRelatedServer(McpServerEntity server) {
        String name = server.getName() == null ? "" : server.getName().toLowerCase();
        String url = server.getUrl() == null ? "" : server.getUrl().toLowerCase();
        String desc = server.getDescription() == null ? "" : server.getDescription().toLowerCase();
        return name.contains("cloudcc")
                || url.contains("cloudcc")
                || desc.contains("cloudcc")
                || name.contains("cc-mcp")
                || name.contains("cc_mcp");
    }

    /**
     * Schema-aware variant: only injects {@code base_url} / {@code open_api_token} when the
     * tool's input schema actually declares those properties. Tools that rely on HTTP headers
     * for authentication omit those fields from their schema, so injecting them would produce
     * a Pydantic "unexpected_keyword_argument" error on the MCP server side.
     */
    private String mergeCloudccToolArguments(String argumentsJson, CloudccSessionContext ctx, JsonNode inputSchema) {
        ObjectNode args;
        try {
            if (argumentsJson == null || argumentsJson.isBlank()) {
                args = objectMapper.createObjectNode();
            } else {
                args = (ObjectNode) objectMapper.readTree(argumentsJson);
            }
        } catch (Exception e) {
            args = objectMapper.createObjectNode();
        }

        if (toolSchemaAcceptsField(inputSchema, "base_url")) {
            String baseUrl = ensureUrlWithScheme(ctx.baseUrl());
            if (baseUrl != null && !baseUrl.isBlank()) {
                args.put("base_url", baseUrl);
                args.remove("baseUrl");
            }
        }
        String accessToken = ctx.accessToken();
        if (accessToken != null && !accessToken.isBlank()) {
            // Different CloudCC MCP tools use different parameter names for the access token.
            // Inject only the field name(s) that the tool's schema actually declares,
            // to avoid Pydantic "unexpected_keyword_argument" errors.
            if (toolSchemaAcceptsField(inputSchema, "open_api_token")) {
                args.put("open_api_token", accessToken);
                args.remove("openApiToken");
            }
            if (toolSchemaAcceptsField(inputSchema, "token")) {
                args.put("token", accessToken);
            }
        }

        // Normalize pagination keys: pageNum/pageSize -> page_num/page_size
        normalizeIntField(args, "page_num", "pageNum");
        normalizeIntField(args, "page_size", "pageSize");

        return args.toString();
    }

    /** Legacy overload kept for any callers that do not have the schema available. */
    private String mergeCloudccToolArguments(String argumentsJson, CloudccSessionContext ctx) {
        return mergeCloudccToolArguments(argumentsJson, ctx, null);
    }

    private static String ensureUrlWithScheme(String raw) {
        if (raw == null) {
            return "";
        }
        String u = raw.trim();
        if (u.isEmpty()) {
            return "";
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.startsWith("http://") || u.startsWith("https://")) {
            return u;
        }
        return "https://" + u.replaceFirst("^/+", "");
    }

    private void normalizeIntField(ObjectNode args, String snakeKey, String camelKey) {
        if (!args.has(snakeKey) && args.has(camelKey)) {
            Integer v = parseJsonInt(args.get(camelKey));
            if (v != null) {
                args.put(snakeKey, v);
                args.remove(camelKey);
            }
        } else if (args.has(snakeKey)) {
            JsonNode n = args.get(snakeKey);
            Integer v = parseJsonInt(n);
            if (v != null) {
                args.put(snakeKey, v);
            }
            args.remove(camelKey);
        }
    }

    private static Integer parseJsonInt(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isInt() || node.isLong()) return node.intValue();
        if (node.isNumber()) return node.numberValue().intValue();
        if (node.isTextual()) {
            String s = node.asText().trim();
            if (s.isEmpty()) return null;
            try {
                return Integer.parseInt(s);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private String retryCloudccToolOnce(
            String orgId, String userId, McpServerEntity server, String toolName, String originalArgsJson) {
        try {
            cloudccAccessTokenService.invalidateSessionContext(orgId, userId);
            Optional<CloudccSessionContext> fresh = cloudccAccessTokenService.getSessionContext(orgId, userId);
            if (fresh.isEmpty()) {
                return "CloudCC 调用失败：令牌已失效且刷新失败，请检查 CloudCC 账号绑定信息后重试。";
            }
            String retriedArgs = mergeCloudccToolArguments(originalArgsJson, fresh.get());
            Map<String, String> headers = Map.of(
                    "accessToken", fresh.get().accessToken(),
                    "base_url", ensureUrlWithScheme(fresh.get().baseUrl())
            );
            return mcpClient.callTool(server, toolName, retriedArgs, headers);
        } catch (Exception retryErr) {
            log.warn("CloudCC tool retry after token refresh failed: server={}, tool={}, error={}",
                    server.getName(), toolName, retryErr.getMessage());
            return "CloudCC 调用失败：令牌刷新后仍鉴权失败，请确认 CloudCC 用户名/安全码及权限配置。";
        }
    }

    private boolean isCloudccAuthFailure(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.toLowerCase();
        return s.contains("登录失败")
                || s.contains("请再次登录")
                || s.contains("token")
                || s.contains("鉴权失败")
                || s.contains("unauthorized")
                || s.contains("401")
                || s.contains("expired");
    }

    private String rewriteKnownToolFailure(String toolName, String result) {
        if (result == null || result.isBlank()) {
            return result;
        }
        if ("get_pending_approvals".equalsIgnoreCase(toolName)
                && result.contains("'NoneType' object is not iterable")) {
            return "获取待审批记录失败：CloudCC MCP 审批工具内部异常，远端接口返回了空值且未做兼容处理。"
                    + " 当前用户凭证与网关已可用，请联系该 MCP 服务维护方排查待审批接口返回数据。"
                    + " 原始错误：" + result;
        }
        return result;
    }

    // ── Value types ──

    public record ResolvedTool(
            Long serverId,
            String serverName,
            String name,
            String description,
            com.fasterxml.jackson.databind.JsonNode inputSchema
    ) {}

    public record ToolCacheSnapshot(
            Long serverId,
            int cacheCount,
            String cacheStatus,
            Instant cacheUpdatedAt,
            Instant cacheLastAttemptAt,
            String cacheErrorMessage,
            String cacheVersion,
            List<McpTool> tools
    ) {}
}
