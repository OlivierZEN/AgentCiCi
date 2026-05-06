package com.codehouse.ciciassistant.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.OrgEntity;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.mcp.domain.McpServerEntity;
import com.codehouse.ciciassistant.mcp.domain.McpServerRepository;
import com.codehouse.ciciassistant.mcp.service.McpClient;
import com.codehouse.ciciassistant.mcp.service.McpServerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.mockito.ArgumentCaptor;

@SpringBootTest
@AutoConfigureMockMvc
class McpServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private McpServerRepository mcpServerRepository;

    @Autowired
    private McpServerService mcpServerService;

    @MockBean
    private McpClient mcpClient;

    @MockBean
    private CloudccAccessTokenService cloudccAccessTokenService;

    @Test
    void shouldRejectOrgUserAndAllowOrgAdminForMcpServerApis() throws Exception {
        String userToken = loginTokenExpectingRole("13800138121", "ORG_USER");
        mockMvc.perform(get("/mcp-servers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要组织管理员权限"));

        String adminToken = loginAsAdmin("13800138130");
        mockMvc.perform(get("/mcp-servers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldKeepCachedSnapshotWhenDiscoverRefreshFails() throws Exception {
        String adminToken = loginAsAdmin("13800138131");

        McpClient.McpTool cachedTool = new McpClient.McpTool(
                "demo_tool",
                "demo",
                objectMapper.createObjectNode()
        );
        given(mcpClient.initialize(any(), anyMap())).willReturn(objectMapper.createObjectNode());
        given(mcpClient.listTools(any(), anyMap()))
                .willReturn(List.of(cachedTool))
                .willThrow(new RuntimeException("discover failed"));

        MvcResult createResult = mockMvc.perform(post("/mcp-servers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "mock-mcp",
                                  "description": "cache smoke",
                                  "transportType": "streamable-http",
                                  "url": "https://example.com/mcp",
                                  "headers": "",
                                  "timeoutSeconds": 10
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        long serverId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertThat(serverId).isGreaterThan(0L);

        mockMvc.perform(get("/mcp-servers/{id}/tools", serverId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCount").value(1))
                .andExpect(jsonPath("$.data.cacheStatus").value("ready"));

        mockMvc.perform(post("/mcp-servers/{id}/discover", serverId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("工具发现失败")));

        mockMvc.perform(get("/mcp-servers/{id}/tools", serverId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.toolCount").value(1))
                .andExpect(jsonPath("$.data.cacheStatus").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.equalTo("ready"),
                        org.hamcrest.Matchers.equalTo("error")
                )))
                .andExpect(jsonPath("$.data.tools[0].name").value("demo_tool"));
    }

    @Test
    void shouldStripCloudccCredentialArgumentsWhenToolSchemaDoesNotDeclareThem() throws Exception {
        ObjectNode inputSchema = objectMapper.createObjectNode();
        ObjectNode properties = inputSchema.putObject("properties");
        properties.putObject("object_api_name").put("type", "string");
        McpClient.McpTool tool = new McpClient.McpTool("get_object_fields", "object fields", inputSchema);

        given(cloudccAccessTokenService.getSessionContext("demo-org", "u-cloudcc-mcp"))
                .willReturn(Optional.of(new CloudccAccessTokenService.CloudccSessionContext(
                        "secret-access-token",
                        "example.lightningapi.cloudcc.com"
                )));
        given(mcpClient.initialize(any(), anyMap())).willReturn(objectMapper.createObjectNode());
        given(mcpClient.listTools(any(), anyMap())).willReturn(List.of(tool));
        given(mcpClient.callTool(any(), any(), any(), anyMap())).willReturn("{\"success\":true}");

        McpServerEntity server = mcpServerRepository.save(new McpServerEntity(
                "demo-org",
                "CloudCC MCP",
                "cloudcc schema-aware test",
                "streamable-http",
                "https://mcp.cloudcc.example/mcp",
                "",
                10
        ));
        mcpServerService.refreshToolCache("demo-org", server.getId());

        String result = mcpServerService.executeTool(
                "demo-org",
                "u-cloudcc-mcp",
                "get_object_fields",
                """
                        {
                          "object_api_name": "lead",
                          "open_api_token": "model-leaked-token",
                          "openApiToken": "model-leaked-token",
                          "base_url": "https://model.example",
                          "baseUrl": "https://model.example",
                          "token": "model-leaked-token"
                        }
                        """
        );

        assertThat(result).isEqualTo("{\"success\":true}");
        ArgumentCaptor<String> argsCaptor = ArgumentCaptor.forClass(String.class);
        verify(mcpClient).callTool(any(), org.mockito.ArgumentMatchers.eq("get_object_fields"), argsCaptor.capture(), anyMap());
        assertThat(argsCaptor.getValue()).contains("\"object_api_name\":\"lead\"");
        assertThat(argsCaptor.getValue()).doesNotContain("open_api_token");
        assertThat(argsCaptor.getValue()).doesNotContain("openApiToken");
        assertThat(argsCaptor.getValue()).doesNotContain("base_url");
        assertThat(argsCaptor.getValue()).doesNotContain("baseUrl");
        assertThat(argsCaptor.getValue()).doesNotContain("\"token\"");
    }

    private String loginTokenExpectingRole(String mobile, String expectedRole) throws Exception {
        OrgEntity org = orgRepository.findById("demo-org").orElseThrow();
        UserEntity user = userRepository.findByOrgIdAndMobile("demo-org", mobile)
                .orElseGet(() -> userRepository.save(new UserEntity(org, mobile, expectedRole)));
        user.setRoleCode(expectedRole);
        userRepository.save(user);
        String token = jwtService.issueToken(user);
        assertThat(token).isNotBlank();
        return token;
    }

    private String loginAsAdmin(String mobile) throws Exception {
        return loginTokenExpectingRole(mobile, RoleCodes.ORG_ADMIN);
    }
}
