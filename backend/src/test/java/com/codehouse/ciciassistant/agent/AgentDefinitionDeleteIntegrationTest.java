package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
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
class AgentDefinitionDeleteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Test
    void shouldSoftDeleteCustomAgentAndHideItFromList() throws Exception {
        String token = loginToken("13800138111");
        String agentId = "delete-agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        mockMvc.perform(post("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "name": "待删除测试 Agent",
                                  "summary": "用于验证软删除",
                                  "model": "qwen3.6-plus",
                                  "systemPrompt": "请按测试约束回答。",
                                  "enabled": true,
                                  "specText": "测试删除"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/agents/{agentId}", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.retentionMessage").value("Agent 已从构建列表隐藏；历史运行、审计、OpenAPI 调用和版本证据仍会保留。"));

        AgentDefinitionEntity stored = agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", agentId).orElseThrow();
        assertThat(stored.isEnabled()).isFalse();

        MvcResult listResult = mockMvc.perform(get("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listResult.getResponse().getContentAsString()).doesNotContain(agentId);

        mockMvc.perform(get("/agents/{agentId}", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectBuiltInAgentDelete() throws Exception {
        String token = loginToken("13800138111");

        mockMvc.perform(delete("/agents/{agentId}", "cici-system")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("System built-in Agents cannot be deleted"));
    }

    @Test
    void shouldNotDeleteAgentFromAnotherOrganization() throws Exception {
        String token = loginToken("13800138111");
        String agentId = "other-org-agent-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        agentDefinitionRepository.saveAndFlush(new AgentDefinitionEntity(
                "other-org",
                agentId,
                "跨组织 Agent",
                "不可被 demo-org 删除",
                "",
                "qwen3.6-plus",
                "system",
                "",
                "BALANCED",
                "COPILOT",
                "v0.1",
                null,
                false,
                true));

        mockMvc.perform(delete("/agents/{agentId}", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());

        AgentDefinitionEntity stored = agentDefinitionRepository.findByOrgIdAndAgentId("other-org", agentId).orElseThrow();
        assertThat(stored.isEnabled()).isTrue();
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
}
