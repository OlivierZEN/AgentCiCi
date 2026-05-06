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
    void shouldLoginBySmsAndReadCurrentUser() throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sendJson = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = sendJson.path("data").path("devCode").asText();
        assertThat(code).hasSize(6);

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000",
                                  "code": "%s"
                                }
                                """.formatted(code)))
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
    void shouldRejectWrongCode() throws Exception {
        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001",
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid verification code"));
    }

    @Test
    void shouldRejectLoginWithoutCodeIssued() throws Exception {
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
                .andExpect(jsonPath("$.message").value("Verification code expired or missing"));
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

        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sendJson = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = sendJson.path("data").path("devCode").asText();

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188",
                                  "code": "%s"
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_ADMIN"));
    }

    @Test
    void shouldExposePlatformRoleAndAllowPlatformBootstrap() throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String code = objectMapper.readTree(sendResult.getResponse().getContentAsString())
                .path("data")
                .path("devCode")
                .asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111",
                                  "code": "%s"
                                }
                                """.formatted(code)))
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
