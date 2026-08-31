package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.crmanalysis.service.CrmProductSalesAnalysisToolService;
import com.codehouse.ciciassistant.email.service.EmailToolService;
import com.codehouse.ciciassistant.mcp.service.ApplicationMcpBindingService;
import com.codehouse.ciciassistant.mcp.service.McpServerService;
import com.codehouse.ciciassistant.memory.service.UserMemoryService;
import com.codehouse.ciciassistant.platform.service.PlatformGovernanceService;
import com.codehouse.ciciassistant.security.service.SafetyGatewayService;
import com.codehouse.ciciassistant.skill.service.SkillApiToolService;
import com.codehouse.ciciassistant.tool.codeinterpreter.SandboxCodeInterpreterService;
import com.codehouse.ciciassistant.tool.managedweb.ManagedWebToolService;
import com.codehouse.ciciassistant.tool.service.BuiltinToolCatalog;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
import com.codehouse.ciciassistant.userworkflow.service.AssistantScheduleToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolOrchestratorServiceTest {

    @Test
    void exposesAndDispatchesBothGovernedManagedWebTools() {
        McpServerService mcp = mock(McpServerService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        ManagedWebToolService managedWeb = mock(ManagedWebToolService.class);
        when(mcp.getAllToolsForOrg("org-1")).thenReturn(List.of());
        when(skillApi.getRuntimeToolDefinitions(List.of())).thenReturn(List.of());
        for (String name : ManagedWebToolService.ALL_TOOL_NAMES) {
            when(governance.isRuntimeToolEnabled("org-1", name)).thenReturn(true);
            when(managedWeb.toolDefinition(name)).thenReturn(Map.of(
                    "type", "function", "function", Map.of("name", name)));
        }
        when(managedWeb.dispatch("org-1", "user-1", ManagedWebToolService.TOOL_EXTRACT,
                "{\"url\":\"https://example.com\"}"))
                .thenReturn("{\"success\":true,\"answer\":\"page\"}");

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp, mock(CloudccOpenApiService.class), mock(CrmProductSalesAnalysisToolService.class),
                mock(EmailToolService.class), mock(UserMemoryService.class), mock(TavilyToolService.class),
                governance, skillApi, mock(ApplicationMcpBindingService.class),
                allowSafetyGateway(), new ObjectMapper().findAndRegisterModules());
        orchestrator.setManagedWebToolService(managedWeb);

        assertThat(orchestrator.getToolDefinitions("org-1", ManagedWebToolService.ALL_TOOL_NAMES, List.of()))
                .extracting(item -> String.valueOf(((Map<?, ?>) item.get("function")).get("name")))
                .contains(ManagedWebToolService.TOOL_SEARCH, ManagedWebToolService.TOOL_EXTRACT);
        assertThat(orchestrator.executeTool("org-1", "user-1", ManagedWebToolService.TOOL_EXTRACT,
                "{\"url\":\"https://example.com\"}", ManagedWebToolService.ALL_TOOL_NAMES)).contains("page");
        assertThat(BuiltinToolCatalog.list()).extracting(BuiltinToolCatalog.ToolCatalogItem::toolName)
                .contains(ManagedWebToolService.TOOL_SEARCH, ManagedWebToolService.TOOL_EXTRACT);
    }

    @Test
    void exposesAndDispatchesGovernedCodeInterpreter() {
        McpServerService mcp = mock(McpServerService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        SandboxCodeInterpreterService codeInterpreter = mock(SandboxCodeInterpreterService.class);
        when(mcp.getAllToolsForOrg("org-1")).thenReturn(List.of());
        when(skillApi.getRuntimeToolDefinitions(List.of())).thenReturn(List.of());
        when(governance.isRuntimeToolEnabled("org-1", SandboxCodeInterpreterService.TOOL_NAME)).thenReturn(true);
        when(codeInterpreter.toolDefinition()).thenReturn(Map.of(
                "type", "function", "function", Map.of("name", SandboxCodeInterpreterService.TOOL_NAME)));
        when(codeInterpreter.dispatch("org-1", "user-1", "{\"task\":\"12**3\"}"))
                .thenReturn("{\"success\":true,\"answer\":\"1728\"}");

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp, mock(CloudccOpenApiService.class), mock(CrmProductSalesAnalysisToolService.class),
                mock(EmailToolService.class), mock(UserMemoryService.class), mock(TavilyToolService.class),
                governance, skillApi, mock(ApplicationMcpBindingService.class),
                allowSafetyGateway(), new ObjectMapper().findAndRegisterModules());
        orchestrator.setSandboxCodeInterpreterService(codeInterpreter);

        assertThat(orchestrator.getToolDefinitions("org-1", List.of(SandboxCodeInterpreterService.TOOL_NAME), List.of()))
                .anySatisfy(item -> assertThat(((Map<?, ?>) item.get("function")).get("name"))
                        .isEqualTo(SandboxCodeInterpreterService.TOOL_NAME));
        assertThat(orchestrator.executeTool("org-1", "user-1", SandboxCodeInterpreterService.TOOL_NAME,
                "{\"task\":\"12**3\"}", List.of(SandboxCodeInterpreterService.TOOL_NAME)))
                .contains("1728");
        verify(codeInterpreter).dispatch("org-1", "user-1", "{\"task\":\"12**3\"}");
    }

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
                mock(ApplicationMcpBindingService.class),
                allowSafetyGateway(),
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

    @Test
    void exposesScheduleCreationForCurrentAgentAndBypassesGenericToolPolicy() {
        McpServerService mcp = mock(McpServerService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        AssistantScheduleToolService schedules = mock(AssistantScheduleToolService.class);
        when(mcp.getAllToolsForOrg("org-1")).thenReturn(List.of());
        when(skillApi.getRuntimeToolDefinitions(List.of())).thenReturn(List.of());
        when(schedules.toolDefinition()).thenReturn(Map.of("type", "function", "function", Map.of(
                "name", AssistantScheduleToolService.TOOL_NAME)));
        when(schedules.dispatch(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("{\"status\":\"CREATED\"}");

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp, mock(CloudccOpenApiService.class), mock(CrmProductSalesAnalysisToolService.class),
                mock(EmailToolService.class), mock(UserMemoryService.class), mock(TavilyToolService.class),
                governance, skillApi, mock(ApplicationMcpBindingService.class),
                allowSafetyGateway(), new ObjectMapper().findAndRegisterModules());
        orchestrator.setAssistantScheduleToolService(schedules);

        assertThat(orchestrator.getToolDefinitions("org-1", List.of("tavily_search"), List.of()))
                .anySatisfy(item -> assertThat(((Map<?, ?>) item.get("function")).get("name"))
                        .isEqualTo(AssistantScheduleToolService.TOOL_NAME));
        assertThat(orchestrator.executeTool("org-1", "user-1", AssistantScheduleToolService.TOOL_NAME,
                "{\"cadence\":\"每天 09:00\",\"task\":\"搜索美国 K12\"}", List.of(), List.of(), "agent-1"))
                .isEqualTo("{\"status\":\"CREATED\"}");
        verify(schedules).dispatch("org-1", "user-1", "agent-1", "{\"cadence\":\"每天 09:00\",\"task\":\"搜索美国 K12\"}");
    }

    @Test
    void exposesAndDispatchesApplicationBoundMcpToolOnlyThroughItsBinding() {
        McpServerService mcp = mock(McpServerService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        ApplicationMcpBindingService bindings = mock(ApplicationMcpBindingService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        String toolName = "semattice_project_delivery_query";
        ApplicationMcpBindingService.BoundTool boundTool = new ApplicationMcpBindingService.BoundTool(
                "devautopilot", "DevAutopilot 研发交付系统", "devautopilot.mcp", 7L, "DevAutopilot MCP",
                toolName, "查询研发交付项目", objectMapper.createObjectNode(), "LOW", false);
        when(mcp.getAllToolsForOrg("org-1")).thenReturn(List.of());
        when(skillApi.getRuntimeToolDefinitions(List.of())).thenReturn(List.of());
        when(governance.isRuntimeToolEnabled("org-1", toolName)).thenReturn(true);
        when(bindings.boundTools("org-1")).thenReturn(List.of(boundTool));
        when(bindings.hasTool("org-1", toolName)).thenReturn(true);
        when(bindings.execute("org-1", "user-1", toolName, "{\"focus\":\"overview\"}"))
                .thenReturn("{\"status\":\"SUCCESS\",\"execution_principal_type\":\"SERVICE\"}");

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp, mock(CloudccOpenApiService.class), mock(CrmProductSalesAnalysisToolService.class),
                mock(EmailToolService.class), mock(UserMemoryService.class), mock(TavilyToolService.class),
                governance, skillApi, bindings, allowSafetyGateway(), objectMapper);

        assertThat(orchestrator.getToolDefinitions("org-1", List.of(toolName), List.of()))
                .extracting(item -> String.valueOf(((Map<?, ?>) item.get("function")).get("name")))
                .contains(toolName);
        String result = orchestrator.executeTool(
                "org-1", "user-1", toolName, "{\"focus\":\"overview\"}", List.of(toolName),
                List.of(toolName), "dev-autopilot-pm");

        assertThat(result).contains("SERVICE");
        verify(bindings).execute("org-1", "user-1", toolName, "{\"focus\":\"overview\"}");
        verify(mcp, never()).executeTool("org-1", "user-1", toolName, "{\"focus\":\"overview\"}");
    }

    @Test
    void applicationManagedToolWithoutTenantBindingFailsClosed() {
        McpServerService mcp = mock(McpServerService.class);
        PlatformGovernanceService governance = mock(PlatformGovernanceService.class);
        SkillApiToolService skillApi = mock(SkillApiToolService.class);
        ApplicationMcpBindingService bindings = mock(ApplicationMcpBindingService.class);
        String toolName = "semattice_project_delivery_query";
        when(governance.isRuntimeToolEnabled("org-1", toolName)).thenReturn(true);
        when(bindings.hasTool("org-1", toolName)).thenReturn(false);
        when(bindings.isApplicationManagedTool(toolName)).thenReturn(true);

        ToolOrchestratorService orchestrator = new ToolOrchestratorService(
                mcp, mock(CloudccOpenApiService.class), mock(CrmProductSalesAnalysisToolService.class),
                mock(EmailToolService.class), mock(UserMemoryService.class), mock(TavilyToolService.class),
                governance, skillApi, bindings, allowSafetyGateway(), new ObjectMapper().findAndRegisterModules());

        assertThat(orchestrator.executeTool(
                "org-1", "user-1", toolName, "{\"focus\":\"overview\"}", List.of(toolName),
                List.of(toolName), "dev-autopilot-pm"))
                .contains("APPLICATION_MCP_BINDING_REQUIRED");
        verify(bindings, never()).execute("org-1", "user-1", toolName, "{\"focus\":\"overview\"}");
        verify(mcp, never()).executeTool("org-1", "user-1", toolName, "{\"focus\":\"overview\"}");
    }

    @Test
    void legacyDeliveryToolsAreNotNativeBuiltins() {
        assertThat(BuiltinToolCatalog.list())
                .extracting(BuiltinToolCatalog.ToolCatalogItem::toolName)
                .noneMatch(name -> name.startsWith("semattice_project_delivery_"));
    }

    private SafetyGatewayService allowSafetyGateway() {
        SafetyGatewayService safetyGateway = mock(SafetyGatewayService.class);
        when(safetyGateway.checkToolCall(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> new SafetyGatewayService.SafetyDecision(
                        "ALLOW", invocation.getArgument(3), List.of(), false, "test"));
        when(safetyGateway.checkOutput(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> new SafetyGatewayService.SafetyDecision(
                        "ALLOW", invocation.getArgument(3), List.of(), false, "test"));
        return safetyGateway;
    }
}
