package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class AgentProductionReadinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldBlockPublishWhenAgentHasNoProductionEntry() throws Exception {
        String token = loginToken("13800138111");
        String agentId = "ready-no-entry-" + suffix();
        createAgent(token, agentId, "无入口 Agent", "[]");
        int versionNo = compileAgent(token, agentId, "[]");

        mockMvc.perform(get("/agents/{agentId}/readiness?versionNo={versionNo}", agentId, versionNo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.status").value("blocked"));

        mockMvc.perform(post("/agents/{agentId}/publish", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(versionNo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("缺少可用生产入口")));
    }

    @Test
    void shouldExposeReadinessAndIncludeItInPublishResponse() throws Exception {
        String token = loginToken("13800138111");
        String agentId = "ready-web-" + suffix();
        createAgent(token, agentId, "Web Agent", "[\"web\"]");
        int versionNo = compileAgent(token, agentId, "[\"web\"]");

        MvcResult readinessResult = mockMvc.perform(get("/agents/{agentId}/readiness?versionNo={versionNo}", agentId, versionNo)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(false))
                .andExpect(jsonPath("$.data.summary.channelCount").value(1))
                .andReturn();

        JsonNode checks = objectMapper.readTree(readinessResult.getResponse().getContentAsString())
                .path("data").path("checks");
        assertThat(checks).extracting(item -> item.path("code").asText())
                .contains("compiled_artifacts", "model_route", "runtime_entry");

        mockMvc.perform(post("/agents/{agentId}/publish", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": %d
                                }
                                """.formatted(versionNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.readiness.blocked").value(false))
                .andExpect(jsonPath("$.data.readiness.summary.versionNo").value(versionNo));
    }

    private void createAgent(String token, String agentId, String name, String channelsJson) throws Exception {
        mockMvc.perform(post("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "name": "%s",
                                  "summary": "用于验证生产就绪检查",
                                  "model": "gpt-4.1",
                                  "systemPrompt": "请按测试约束回答。",
                                  "enabled": true,
                                  "specText": "生产就绪检查测试",
                                  "channels": %s
                                }
                                """.formatted(agentId, name, channelsJson)))
                .andExpect(status().isOk());
    }

    private int compileAgent(String token, String agentId, String channelsJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/agents/{agentId}/compile", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "生产就绪测试 Agent",
                                  "model": "gpt-4.1",
                                  "systemPrompt": "请按测试约束回答。",
                                  "specText": "当收到用户输入时回答，并记录生产就绪检查。",
                                  "channels": %s,
                                  "knowledgeBaseIds": [],
                                  "toolIds": [],
                                  "skillRefs": []
                                }
                                """.formatted(channelsJson)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("draftVersionNo").asInt();
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andReturn();
        if (loginResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + loginResult.getResponse().getContentAsString());
        }
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
