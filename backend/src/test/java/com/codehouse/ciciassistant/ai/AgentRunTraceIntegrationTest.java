package com.codehouse.ciciassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

    @Autowired
    private AuditService auditService;

    @Autowired
    private AgentRunTraceRepository traceRepository;

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

        mockMvc.perform(get("/admin/agents/runtime-snapshots")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.agentId == 'cici-system')].sevenDaySessionCount").exists())
                .andExpect(jsonPath("$.data.summary.agentCount").exists())
                .andExpect(jsonPath("$.data.summary.sevenDaySessionCount").exists());

        String failedTraceId = "tool-failure-trace-1";
        Instant now = Instant.now();
        traceRepository.save(new AgentRunTraceEntity(
                failedTraceId,
                "demo-org",
                "u-runtime-failure",
                "s-runtime-failure",
                "cici-system",
                "web",
                "FAILED",
                "工具调用失败验证",
                "外部工具调用失败",
                "qwen-plus",
                "",
                now.minusSeconds(2),
                now,
                2000,
                1,
                1,
                0,
                "[]",
                "[]",
                """
                        [{"id":"tool-failed","type":"TOOL","title":"CRM 查询","status":"FAILED","startedAt":"%s","endedAt":"%s","elapsedMs":2000,"summary":"HTTP 502 upstream timeout","metadata":{}}]
                        """.formatted(now.minusSeconds(2), now),
                """
                        {"tools":[{"id":"tool-failed","name":"crm.lookup","success":false,"result":"HTTP 502 upstream timeout","errorMessage":"HTTP 502 upstream timeout","elapsedMs":2000}],"model":{},"rag":{},"skills":{}}
                        """,
                now));
        mockMvc.perform(get("/admin/agents/run-logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("q", "upstream timeout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].traceId").value(failedTraceId))
                .andExpect(jsonPath("$.data.items[0].errorReason").value("crm.lookup 调用失败：HTTP 502 upstream timeout"));

        mockMvc.perform(get("/admin/agents/run-logs/{traceId}", failedTraceId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.errorReason").value("crm.lookup 调用失败：HTTP 502 upstream timeout"))
                .andExpect(jsonPath("$.data.tools[0].errorMessage").value("HTTP 502 upstream timeout"));
    }

    @Test
    void shouldExposeFilteredRedactedAuditLogsToOrgAdminOnly() throws Exception {
        auditService.log(
                "demo-org",
                "u-admin-audit",
                "ops.audit.redaction",
                "Authorization=Bearer raw-token apiKey=abc123 password=szyd1234 mobile=13800138000");
        String adminToken = loginToken("13800138111");
        MvcResult logsResult = mockMvc.perform(get("/ops/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("eventType", "ops.audit.redaction")
                        .param("q", "u-admin-audit")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("ops.audit.redaction"))
                .andExpect(jsonPath("$.data.items[0].userId").value("u-admin-audit"))
                .andReturn();

        String body = logsResult.getResponse().getContentAsString();
        assertThat(body).contains("[redacted]");
        assertThat(body).contains("138****8000");
        assertThat(body).doesNotContain("raw-token");
        assertThat(body).doesNotContain("abc123");
        assertThat(body).doesNotContain("szyd1234");

        String userToken = loginToken("13800138017");
        mockMvc.perform(get("/ops/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isForbidden());
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
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
