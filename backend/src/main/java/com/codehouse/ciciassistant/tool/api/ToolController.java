package com.codehouse.ciciassistant.tool.api;

import com.codehouse.ciciassistant.auth.RequireOrgAdmin;
import com.codehouse.ciciassistant.common.api.ApiResponse;
import com.codehouse.ciciassistant.mcp.service.ApplicationMcpBindingService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionEntity;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog.ToolCatalogItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tools")
@RequireOrgAdmin
public class ToolController {

    private final ToolDefinitionRepository repository;
    private final PlatformGovernanceService platformGovernanceService;
    private final ApplicationMcpBindingService applicationMcpBindings;

    public ToolController(ToolDefinitionRepository repository,
                          PlatformGovernanceService platformGovernanceService,
                          ApplicationMcpBindingService applicationMcpBindings) {
        this.repository = repository;
        this.platformGovernanceService = platformGovernanceService;
        this.applicationMcpBindings = applicationMcpBindings;
    }

    /**
     * Returns the unified catalog used by Agent Builder tool-whitelist selection.
     * It merges built-in tools (always visible) with org-level {@code tool_definition} rows.
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        String companyId = TenantContext.requireCompanyId();
        List<Map<String, Object>> out = new ArrayList<>();
        LinkedHashSet<String> listedNames = new LinkedHashSet<>();
        List<ToolCatalogItem> builtinTools = platformGovernanceService.listEffectiveBuiltinTools(companyId);
        for (ToolCatalogItem b : builtinTools) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolName", b.toolName());
            row.put("displayName", b.displayName());
            row.put("description", b.description());
            row.put("riskLevel", b.riskLevel());
            row.put("category", b.category());
            row.put("builtin", true);
            row.put("sourceType", "BUILTIN");
            out.add(row);
            listedNames.add(b.toolName());
        }
        for (ApplicationMcpBindingService.BoundTool tool : applicationMcpBindings.boundTools(companyId)) {
            if (!listedNames.add(tool.toolName())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolName", tool.toolName());
            row.put("displayName", tool.toolName());
            row.put("description", tool.description() == null ? "" : tool.description());
            row.put("riskLevel", riskLabel(tool.riskLevel()));
            row.put("category", "application_mcp");
            row.put("builtin", false);
            row.put("sourceType", "APPLICATION_MCP");
            row.put("appCode", tool.appCode());
            row.put("appDisplayName", tool.appDisplayName());
            row.put("providerKey", tool.providerKey());
            row.put("serverId", tool.serverId());
            row.put("serverName", tool.serverName());
            row.put("confirmationRequired", tool.confirmationRequired());
            out.add(row);
        }
        for (ToolDefinitionEntity item : repository.findByCompanyIdAndEnabledTrue(companyId)) {
            if (!listedNames.add(item.getToolName())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("toolName", item.getToolName());
            row.put("displayName", item.getToolName());
            row.put("description", item.getDescription());
            row.put("riskLevel", item.getRiskLevel());
            row.put("category", "custom");
            row.put("builtin", false);
            row.put("sourceType", "CUSTOM");
            out.add(row);
        }
        return ApiResponse.ok(out);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateToolRequest request) {
        String companyId = TenantContext.requireCompanyId();
        ToolDefinitionEntity entity = repository.save(
                new ToolDefinitionEntity(companyId, request.toolName(), request.description(), request.riskLevel(), true));
        return ApiResponse.ok(Map.of(
                "companyId", entity.getCompanyId(),
                "toolName", entity.getToolName(),
                "description", entity.getDescription(),
                "riskLevel", entity.getRiskLevel(),
                "enabled", entity.isEnabled()
        ));
    }

    @DeleteMapping
    public ApiResponse<Map<String, Object>> disable(@RequestParam("toolName") String toolName) {
        String companyId = TenantContext.requireCompanyId();
        ToolDefinitionEntity entity = repository.findByCompanyIdAndToolName(companyId, toolName)
                .orElseThrow(() -> new IllegalArgumentException("Tool not found"));
        entity.setEnabled(false);
        repository.save(entity);
        return ApiResponse.ok(Map.of("companyId", companyId, "toolName", toolName, "enabled", false));
    }

    public record CreateToolRequest(
            @NotBlank String toolName,
            @NotBlank String description,
            @NotBlank String riskLevel
    ) {
    }

    private static String riskLabel(String value) {
        if ("LOW".equalsIgnoreCase(value)) return "低风险";
        if ("HIGH".equalsIgnoreCase(value)) return "高风险";
        return "中风险";
    }
}
