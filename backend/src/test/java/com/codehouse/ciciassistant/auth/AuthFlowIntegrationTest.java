package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Test
    void shouldLoginByPasswordAndReadCurrentUser() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongPassword() throws Exception {
        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001",
                                  "password": "bad-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid mobile or password"));
    }

    @Test
    void shouldDisableSmsLoginEndpoints() throws Exception {
        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138002"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS verification login is disabled"));

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138002",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS verification login is disabled"));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void shouldPromoteExistingOrgUserToAdminWhenMobileInBootstrapList() throws Exception {
        var org = orgRepository.findById("demo-org").orElseThrow();
        userRepository.findByOrgIdAndMobile("demo-org", "13800138188").ifPresent(userRepository::delete);
        userRepository.flush();
        userRepository.save(new UserEntity(org, "13800138188", RoleCodes.ORG_USER));

        mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_ADMIN"));
    }

    @Test
    void shouldExposePlatformRoleAndAllowPlatformBootstrap() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists());
    }

    @Test
    void shouldExposePublicAgentAvatarsForLoginModeCube() throws Exception {
        agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", "public-avatar-agent")
                .ifPresent(agentDefinitionRepository::delete);
        agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", "public-avatar-disabled")
                .ifPresent(agentDefinitionRepository::delete);
        agentDefinitionRepository.flush();

        String avatarDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJ"
                + "AAAADUlEQVR42mP8z8BQDwAFgwJ/lx6mHQAAAABJRU5ErkJggg==";
        agentDefinitionRepository.save(new AgentDefinitionEntity(
                "demo-org",
                "public-avatar-agent",
                "登录立方体智能体",
                "登录页展示头像",
                "",
                "deepseek-v4-pro",
                "",
                "",
                "standard",
                "autonomous",
                "v1",
                avatarDataUrl,
                true,
                true
        ));
        agentDefinitionRepository.save(new AgentDefinitionEntity(
                "demo-org",
                "public-avatar-disabled",
                "禁用智能体",
                "不应出现在公开头像池",
                "",
                "deepseek-v4-pro",
                "",
                "",
                "standard",
                "autonomous",
                "v1",
                avatarDataUrl,
                true,
                false
        ));
        agentDefinitionRepository.flush();

        MvcResult result = mockMvc.perform(get("/public/agents/avatars")
                        .queryParam("orgId", "demo-org"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        List<JsonNode> items = new ArrayList<>();
        data.forEach(items::add);
        JsonNode avatarItem = items.stream()
                .filter(item -> item.path("agentId").asText().equals("public-avatar-agent"))
                .findFirst()
                .orElseThrow();
        assertThat(avatarItem.path("name").asText()).isEqualTo("登录立方体智能体");
        assertThat(avatarItem.path("avatarBase64").asText()).isEqualTo(avatarDataUrl);
        assertThat(items.stream().map(item -> item.path("agentId").asText()))
                .doesNotContain("public-avatar-disabled");
    }
}
