package com.codehouse.ciciassistant.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
class AgentDefinitionListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listShouldReturnChannelsForEachAgent() throws Exception {
        String token = loginToken("13800138111");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String noApiAgentId = "list-no-api-" + suffix;
        String apiAgentId = "list-api-" + suffix;

        createAgent(token, noApiAgentId, "无 API Agent", """
                "channels": ["web"]
                """);
        createAgent(token, apiAgentId, "开放 API Agent", """
                "channels": ["api", "web"]
                """);

        MvcResult listResult = mockMvc.perform(get("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        assertThat(channelsFor(data, noApiAgentId)).containsExactly("web");
        assertThat(channelsFor(data, apiAgentId)).containsExactly("api", "web");
    }

    private void createAgent(String token, String agentId, String name, String channelsJson) throws Exception {
        mockMvc.perform(post("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId": "%s",
                                  "name": "%s",
                                  "summary": "用于验证列表渠道",
                                  "model": "qwen3.6-plus",
                                  "systemPrompt": "请按测试约束回答。",
                                  "enabled": true,
                                  "specText": "测试列表渠道",
                                  %s
                                }
                                """.formatted(agentId, name, channelsJson)))
                .andExpect(status().isOk());
    }

    private List<String> channelsFor(JsonNode agents, String agentId) {
        for (JsonNode agent : agents) {
            if (agentId.equals(agent.path("agentId").asText())) {
                List<String> channels = new ArrayList<>();
                agent.path("channels").forEach(channel -> channels.add(channel.asText()));
                return channels;
            }
        }
        throw new AssertionError("agent not found: " + agentId);
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "demo-org",
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
