package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.email.service.EmailToolService;
import com.codehouse.ciciassistant.mcp.service.McpServerService;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.skill.service.SkillApiToolService;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolOrchestratorServiceTest {

    @Test
    void exposesAndDispatchesCrmProductSalesRankAsNativeBuiltinTool() {
        McpServerService mcp = mock(McpServerService.class);
        CloudccOpenApiService cloudcc = mock(CloudccOpenApiService.class);
        CrmProductSalesAnalysisToolService crmAnalysis = mock(CrmProductSalesAnalysisToolService.class);
        EmailToolService email = mock(EmailToolService.class);
        UserMemoryService memory = mock(UserMemoryService.class);
        TavilyToolService tavily = mock(TavilyToolService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        when(mcp.getAllToolsForOrg("org-1")).thenReturn(List.of());
        when(governance.isRuntimeToolEnabled("org-1", CrmProductSalesAnalysisToolService.TOOL_NAME)).thenReturn(true);
        when(skillApi.getRuntimeToolDefinitions(List.of())).thenReturn(List.of());
        when(crmAnalysis.dispatch("org-1", "user-1", "{\"topN\":3}"))
                .thenReturn("{\"status\":\"SUCCESS\"}");

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp,
                cloudcc,
                crmAnalysis,
                email,
                memory,
                tavily,
                governance,
                skillApi,
                objectMapper
        );

        List<Map<String, Object>> definitions = orchestrator.getToolDefinitions(
                "org-1", List.of(CrmProductSalesAnalysisToolService.TOOL_NAME), List.of());
        assertThat(definitions).anySatisfy(definition -> {
            Map<?, ?> function = (Map<?, ?>) definition.get("function");
            assertThat(function.get("name")).isEqualTo(CrmProductSalesAnalysisToolService.TOOL_NAME);
        });
        assertThat(BuiltinToolCatalog.list())
                .extracting(BuiltinToolCatalog.ToolCatalogItem::toolName)
                .contains(CrmProductSalesAnalysisToolService.TOOL_NAME);

        String result = orchestrator.executeTool(
                "org-1",
                "user-1",
                CrmProductSalesAnalysisToolService.TOOL_NAME,
                "{\"topN\":3}",
                List.of(CrmProductSalesAnalysisToolService.TOOL_NAME)
        );
        assertThat(result).isEqualTo("{\"status\":\"SUCCESS\"}");
        verify(crmAnalysis).dispatch("org-1", "user-1", "{\"topN\":3}");
    }
}
