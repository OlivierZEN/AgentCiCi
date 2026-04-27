package com.codehouse.ciciassistant.mcp.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.codehouse.ciciassistant.mcp.service.McpClient.McpTool;
import com.codehouse.ciciassistant.mcp.service.McpServerService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mcp-servers")
@RequireOrgAdmin
public class McpServerController {

    private static final Logger log = LoggerFactory.getLogger(McpServerController.class);

    private final McpServerService service;

    public McpServerController(McpServerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String orgId = TenantContext.requireOrgId();
        List<McpServerEntity> servers = service.list(orgId);
        return ApiResponse.ok(servers.stream().map(this::toMap).toList());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest req) {
        String orgId = TenantContext.requireOrgId();
        McpServerEntity entity = service.create(orgId, req.name(), req.description(),
                req.transportType(), req.url(), req.headers(), req.timeoutSeconds());
        return ApiResponse.ok(toMap(entity));
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateRequest req) {
        String orgId = TenantContext.requireOrgId();
        McpServerEntity entity = service.update(orgId, id, req.name(), req.description(),
                req.transportType(), req.url(), req.headers(), req.timeoutSeconds(), req.enabled());
        return ApiResponse.ok(toMap(entity));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, String>> delete(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        service.delete(orgId, id);
        return ApiResponse.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/{id}/discover")
    public ApiResponse<?> discoverTools(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        try {
            McpServerService.ToolCacheSnapshot snapshot = service.refreshToolCache(orgId, id);
            return ApiResponse.ok(toToolCachePayload(snapshot));
        } catch (Exception e) {
            log.error("MCP discover failed, orgId={}, serverId={}", orgId, id, e);
            String reason = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
            return ApiResponse.fail("工具发现失败: " + reason);
        }
    }

    @GetMapping("/{id}/tools")
    public ApiResponse<?> getToolCache(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        try {
            McpServerService.ToolCacheSnapshot snapshot = service.getToolCacheSnapshot(orgId, id);
            return ApiResponse.ok(toToolCachePayload(snapshot));
        } catch (Exception e) {
            log.error("MCP tool cache read failed, orgId={}, serverId={}", orgId, id, e);
            String reason = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
            return ApiResponse.fail("读取工具缓存失败: " + reason);
        }
    }

    @PostMapping("/{id}/health")
    public ApiResponse<?> healthCheck(@PathVariable Long id) {
        String orgId = TenantContext.requireOrgId();
        try {
            Map<String, Object> result = service.healthCheck(orgId, id);
            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("MCP health check failed, orgId={}, serverId={}", orgId, id, e);
            String reason = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : e.toString();
            return ApiResponse.fail("连接失败: " + reason);
        }
    }

    private Map<String, Object> toMap(McpServerEntity e) {
        return Map.ofEntries(
                Map.entry("id", e.getId()),
                Map.entry("name", e.getName()),
                Map.entry("description", e.getDescription() == null ? "" : e.getDescription()),
                Map.entry("transportType", e.getTransportType()),
                Map.entry("url", e.getUrl()),
                Map.entry("headers", e.getHeaders() == null ? "" : e.getHeaders()),
                Map.entry("timeoutSeconds", e.getTimeoutSeconds()),
                Map.entry("enabled", e.isEnabled()),
                Map.entry("createdAt", e.getCreatedAt().toString()),
                Map.entry("updatedAt", e.getUpdatedAt().toString()),
                Map.entry("toolCacheCount", e.getToolCacheCount()),
                Map.entry("toolCacheStatus", e.getToolCacheStatus() == null ? "empty" : e.getToolCacheStatus()),
                Map.entry("toolCacheUpdatedAt", e.getToolCacheUpdatedAt() == null ? "" : e.getToolCacheUpdatedAt().toString()),
                Map.entry("toolCacheLastAttemptAt", e.getToolCacheLastAttemptAt() == null ? "" : e.getToolCacheLastAttemptAt().toString()),
                Map.entry("toolCacheErrorMessage", e.getToolCacheErrorMessage() == null ? "" : e.getToolCacheErrorMessage()),
                Map.entry("toolCacheVersion", e.getToolCacheVersion() == null ? "" : e.getToolCacheVersion())
        );
    }

    private Map<String, Object> toToolCachePayload(McpServerService.ToolCacheSnapshot snapshot) {
        List<Map<String, Object>> tools = snapshot.tools().stream().map(t -> Map.<String, Object>of(
                "name", t.name(),
                "description", t.description() == null ? "" : t.description(),
                "inputSchema", t.inputSchema() == null ? "" : t.inputSchema().toString()
        )).toList();
        return Map.of(
                "serverId", snapshot.serverId(),
                "cacheStatus", snapshot.cacheStatus(),
                "cacheUpdatedAt", snapshot.cacheUpdatedAt() == null ? "" : snapshot.cacheUpdatedAt().toString(),
                "cacheLastAttemptAt", snapshot.cacheLastAttemptAt() == null ? "" : snapshot.cacheLastAttemptAt().toString(),
                "cacheErrorMessage", snapshot.cacheErrorMessage() == null ? "" : snapshot.cacheErrorMessage(),
                "cacheVersion", snapshot.cacheVersion() == null ? "" : snapshot.cacheVersion(),
                "toolCount", snapshot.cacheCount(),
                "tools", tools
        );
    }

    public record CreateRequest(
            @NotBlank String name,
            String description,
            @NotBlank String transportType,
            @NotBlank String url,
            String headers,
            int timeoutSeconds
    ) {
        public CreateRequest {
            if (timeoutSeconds <= 0) timeoutSeconds = 60;
        }
    }

    public record UpdateRequest(
            @NotBlank String name,
            String description,
            @NotBlank String transportType,
            @NotBlank String url,
            String headers,
            int timeoutSeconds,
            boolean enabled
    ) {
        public UpdateRequest {
            if (timeoutSeconds <= 0) timeoutSeconds = 60;
        }
    }
}
