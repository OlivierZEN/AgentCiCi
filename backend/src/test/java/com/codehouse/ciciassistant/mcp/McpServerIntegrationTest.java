package com.codehouse.ciciassistant.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
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
import com.codehouse.ciciassistant.mcp.service.McpClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

    @MockBean
    private McpClient mcpClient;

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
