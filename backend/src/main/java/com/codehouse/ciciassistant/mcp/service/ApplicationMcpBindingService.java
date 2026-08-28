package com.codehouse.ciciassistant.mcp.service;

import com.codehouse.ciciassistant.common.error.ConflictException;
import com.codehouse.ciciassistant.common.error.ResourceNotFoundException;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.codehouse.ciciassistant.mcp.domain.McpServerRepository;
import com.codehouse.ciciassistant.mcp.service.McpClient.McpTool;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Exact tenant binding from an application version's declared MCP provider to one configured server. */
@Service
public class ApplicationMcpBindingService {
    private final JdbcTemplate jdbc;
    private final McpServerRepository servers;
    private final McpServerService mcp;

    public ApplicationMcpBindingService(JdbcTemplate jdbc, McpServerRepository servers, McpServerService mcp) {
        this.jdbc = jdbc;
        this.servers = servers;
        this.mcp = mcp;
    }

    @Transactional(readOnly = true)
    public List<BindingView> list(String companyId, String appCode) {
        return jdbc.query("""
                SELECT binding.id,binding.company_id,binding.app_code,version.version,binding.provider_key,
                       binding.mcp_server_id,server.name,binding.status,binding.created_at,binding.updated_at
                FROM tenant_application_mcp_binding binding
                JOIN internal_application_version version ON version.id=binding.application_version_id
                JOIN mcp_server server ON server.id=binding.mcp_server_id
                WHERE binding.company_id=? AND binding.app_code=?
                ORDER BY binding.provider_key
                """, (rs, rowNum) -> new BindingView(
                rs.getString("id"), rs.getString("company_id"), rs.getString("app_code"), rs.getString("version"),
                rs.getString("provider_key"), rs.getLong("mcp_server_id"), rs.getString("name"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()),
                companyId, normalize(appCode));
    }

    @Transactional
    public BindingView bind(String companyId, String appCode, String version, String providerKey,
                            Long serverId, String actorId) throws Exception {
        String code = normalize(appCode);
        McpServerEntity server = servers.findByIdAndCompanyId(serverId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("MCP Server not found"));
        if (!server.isEnabled()) throw new ConflictException("MCP Server must be enabled before application binding");
        Provider provider = requireProvider(code, version, providerKey);
        if (!provider.authType().equals(server.getAuthType())) {
            throw new ConflictException("MCP Server authType does not match the application provider declaration");
        }
        if (provider.audience() != null && !provider.audience().isBlank()
                && !provider.audience().equals(server.getTokenAudience())) {
            throw new ConflictException("MCP Server token audience does not match the application provider declaration");
        }
        if (provider.requiredScope() != null && !provider.requiredScope().isBlank()
                && !tokenScopes(server).contains(provider.requiredScope())) {
            throw new ConflictException("MCP Server token scopes do not satisfy the application provider declaration");
        }
        List<McpTool> discovered = mcp.discoverTools(companyId, serverId);
        Set<String> discoveredNames = discovered.stream().map(McpTool::name).collect(Collectors.toSet());
        List<String> missing = provider.tools().stream().filter(tool -> !discoveredNames.contains(tool)).toList();
        if (!missing.isEmpty()) throw new ConflictException("MCP Server is missing declared tools: " + String.join(",", missing));
        jdbc.update("""
                INSERT INTO tenant_application_mcp_binding(
                    id,company_id,app_code,application_version_id,provider_key,mcp_server_id,status,bound_by)
                VALUES (?,?,?,?,?,?,'ACTIVE',?)
                ON CONFLICT (company_id,app_code,provider_key) DO UPDATE
                SET application_version_id=EXCLUDED.application_version_id,mcp_server_id=EXCLUDED.mcp_server_id,
                    status='ACTIVE',bound_by=EXCLUDED.bound_by,updated_at=CURRENT_TIMESTAMP
                """, UUID.randomUUID().toString(), companyId, code, provider.versionId(), provider.providerKey(), serverId, actorId);
        return list(companyId, code).stream().filter(item -> item.providerKey().equals(provider.providerKey())).findFirst().orElseThrow();
    }

    @Transactional
    public void disable(String companyId, String appCode, String providerKey) {
        int updated = jdbc.update("""
                UPDATE tenant_application_mcp_binding SET status='DISABLED',updated_at=CURRENT_TIMESTAMP
                WHERE company_id=? AND app_code=? AND provider_key=?
                """, companyId, normalize(appCode), normalizeKey(providerKey));
        if (updated != 1) throw new ResourceNotFoundException("Application MCP binding not found");
    }

    @Transactional(readOnly = true)
    public boolean hasTool(String companyId, String appCode, String toolName) {
        return !jdbc.queryForList("""
                SELECT tool.id
                FROM tenant_application_mcp_binding binding
                JOIN application_version_mcp_provider provider
                  ON provider.application_version_id=binding.application_version_id
                 AND provider.provider_key=binding.provider_key
                JOIN application_version_mcp_tool tool ON tool.provider_id=provider.id
                JOIN mcp_server server ON server.id=binding.mcp_server_id
                WHERE binding.company_id=? AND binding.app_code=? AND binding.status='ACTIVE'
                  AND server.enabled=TRUE AND tool.tool_name=?
                """, String.class, companyId, normalize(appCode), toolName).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<McpServerService.ResolvedTool> tools(String companyId, String appCode) {
        return bindingsFor(companyId, appCode).stream().flatMap(binding -> mcp.getTools(companyId, binding.serverId()).stream()
                .filter(tool -> binding.allowedTools().contains(tool.name()))
                .map(tool -> new McpServerService.ResolvedTool(binding.serverId(), binding.serverName(), tool.name(), tool.description(), tool.inputSchema())))
                .toList();
    }

    public String execute(String companyId, String userId, String appCode, String toolName, String argumentsJson) {
        RuntimeBinding binding = bindingsFor(companyId, appCode).stream()
                .filter(item -> item.allowedTools().contains(toolName)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Application MCP tool is not bound"));
        return mcp.executeToolOnServer(companyId, userId, binding.serverId(), toolName, argumentsJson);
    }

    private Provider requireProvider(String appCode, String version, String providerKey) {
        List<Provider> matches = jdbc.query("""
                SELECT provider.id,provider.application_version_id,provider.provider_key,provider.auth_type,
                       provider.audience,provider.required_scope
                FROM application_version_mcp_provider provider
                JOIN internal_application_version version ON version.id=provider.application_version_id
                WHERE version.app_code=? AND version.version=? AND version.version_status IN ('VALIDATED','PUBLISHED')
                  AND provider.provider_key=?
                """, (rs, rowNum) -> new Provider(rs.getString("id"), rs.getString("application_version_id"),
                rs.getString("provider_key"), rs.getString("auth_type"), rs.getString("audience"),
                rs.getString("required_scope"), toolsForProvider(rs.getString("id"))),
                appCode, version, normalizeKey(providerKey));
        if (matches.size() != 1) throw new ResourceNotFoundException("Published application MCP provider not found");
        return matches.getFirst();
    }

    private List<String> toolsForProvider(String providerId) {
        return jdbc.queryForList("SELECT tool_name FROM application_version_mcp_tool WHERE provider_id=? ORDER BY tool_name",
                String.class, providerId);
    }

    private List<RuntimeBinding> bindingsFor(String companyId, String appCode) {
        return jdbc.query("""
                SELECT binding.mcp_server_id,server.name,provider.id AS provider_id
                FROM tenant_application_mcp_binding binding
                JOIN application_version_mcp_provider provider
                  ON provider.application_version_id=binding.application_version_id
                 AND provider.provider_key=binding.provider_key
                JOIN mcp_server server ON server.id=binding.mcp_server_id
                WHERE binding.company_id=? AND binding.app_code=? AND binding.status='ACTIVE' AND server.enabled=TRUE
                ORDER BY binding.provider_key
                """, (rs, rowNum) -> new RuntimeBinding(rs.getLong("mcp_server_id"), rs.getString("name"),
                Set.copyOf(toolsForProvider(rs.getString("provider_id")))), companyId, normalize(appCode));
    }

    private static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!normalized.matches("^[a-z][a-z0-9-]{1,63}$")) throw new IllegalArgumentException("Invalid appCode");
        return normalized;
    }

    private static String normalizeKey(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        if (!normalized.matches("^[a-z][a-z0-9._-]{1,127}$")) throw new IllegalArgumentException("Invalid providerKey");
        return normalized;
    }

    private static Set<String> tokenScopes(McpServerEntity server) {
        String value = server.getTokenScopes();
        if (value == null || value.isBlank()) return Set.of();
        return java.util.Arrays.stream(value.trim().split("\\s+"))
                .filter(item -> !item.isBlank()).collect(Collectors.toUnmodifiableSet());
    }

    private record Provider(String id, String versionId, String providerKey, String authType,
                            String audience, String requiredScope, List<String> tools) {}
    private record RuntimeBinding(Long serverId, String serverName, Set<String> allowedTools) {}
    public record BindingView(String id, String companyId, String appCode, String version, String providerKey,
                              Long serverId, String serverName, String status,
                              java.time.Instant createdAt, java.time.Instant updatedAt) {}
}
