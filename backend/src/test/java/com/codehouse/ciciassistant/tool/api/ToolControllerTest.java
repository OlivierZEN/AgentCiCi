package com.codehouse.ciciassistant.tool.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.mcp.service.ApplicationMcpBindingService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.tenant.TenantContext;
import com.codehouse.ciciassistant.tool.domain.ToolDefinitionRepository;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ToolControllerTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void unifiedCatalogMarksApplicationMcpToolsAsExternalAndKeepsLegacyDeliveryToolsOutOfBuiltins() {
        ToolDefinitionRepository repository = mock(ToolDefinitionRepository.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        ApplicationMcpBindingService bindings = mock(ApplicationMcpBindingService.class);
        TenantContext.setCompanyId("org-1");
        when(governance.listEffectiveBuiltinTools("org-1")).thenReturn(List.of(
                new BuiltinToolCatalog.ToolCatalogItem(
                        "rag-search", "企业知识检索", "检索企业知识", "低风险", "knowledge")));
        when(repository.findByCompanyIdAndEnabledTrue("org-1")).thenReturn(List.of());
        when(bindings.boundTools("org-1")).thenReturn(List.of(
                new ApplicationMcpBindingService.BoundTool(
                        "devautopilot", "DevAutopilot 研发交付系统", "devautopilot.mcp", 7L,
                        "DevAutopilot MCP", "semattice_project_delivery_query", "查询研发交付项目",
                        new ObjectMapper().createObjectNode(), "LOW", false)));

        List<Map<String, Object>> rows = new ToolController(repository, governance, bindings).list().data();

        assertThat(rows).anySatisfy(row -> {
            assertThat(row.get("toolName")).isEqualTo("semattice_project_delivery_query");
            assertThat(row.get("builtin")).isEqualTo(false);
            assertThat(row.get("sourceType")).isEqualTo("APPLICATION_MCP");
            assertThat(row.get("appCode")).isEqualTo("devautopilot");
            assertThat(row.get("providerKey")).isEqualTo("devautopilot.mcp");
        });
        assertThat(rows.stream()
                .filter(row -> Boolean.TRUE.equals(row.get("builtin")))
                .map(row -> String.valueOf(row.get("toolName"))))
                .noneMatch(name -> name.startsWith("semattice_project_delivery_"));
    }
}
