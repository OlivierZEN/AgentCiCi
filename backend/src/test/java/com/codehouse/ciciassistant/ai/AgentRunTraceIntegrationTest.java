package com.codehouse.ciciassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
@TestPropertySource(properties = "spring.profiles.active=default")
@AutoConfigureMockMvc
class AgentRunTraceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeChatSessionAsAgentRunLogAndTraceDetail() throws Exception {
        String token = loginToken("13800138017");
        String sessionId = "s-run-trace-1";

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "agentId": "cici-system",
                                  "question": "看下最近的潜在客户",
                                  "knowledgeBaseIds": []
                                }
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").exists());

        MvcResult listResult = mockMvc.perform(get("/me/agents/run-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("q", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.items[0].traceId").exists())
                .andExpect(jsonPath("$.data.items[0].agentId").value("cici-system"))
                .andReturn();

        JsonNode first = objectMapper.readTree(listResult.getResponse().getContentAsString())
                .path("data").path("items").get(0);
        String traceId = first.path("traceId").asText();
        assertThat(traceId).isNotBlank();

        MvcResult detailResult = mockMvc.perform(get("/me/agents/run-logs/{traceId}", traceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.model").exists())
                .andExpect(jsonPath("$.data.rag").exists())
                .andExpect(jsonPath("$.data.skills").exists())
                .andExpect(jsonPath("$.data.skills.boundSkillCodes").isArray())
                .andExpect(jsonPath("$.data.skills.activatedSkillCodes").isArray())
                .andExpect(jsonPath("$.data.detail.modelCalls").isArray())
                .andExpect(jsonPath("$.data.nodes[3].type").value("SKILL"))
                .andExpect(jsonPath("$.data.nodes[3].status").value("SKIPPED"))
                .andReturn();
        JsonNode detail = objectMapper.readTree(detailResult.getResponse().getContentAsString()).path("data");
        assertThat(detail.path("skills").path("boundSkillCodes").size()).isGreaterThanOrEqualTo(
                detail.path("skills").path("activatedSkillCodes").size());

        String adminToken = loginToken("13800138111");
        mockMvc.perform(get("/admin/agents/run-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("q", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.items[0].traceId").value(traceId));

        mockMvc.perform(get("/admin/agents/run-logs/{traceId}", traceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.nodes").isArray());
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
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
