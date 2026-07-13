package com.codehouse.ciciassistant.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
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
class AgentEvaluationControlPlaneIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtService jwtService;
    @Autowired private AgentRunTraceRepository traceRepository;

    @Test
    void shouldGovernPlatformSuiteVersionAndRedactHiddenCaseForTenant() throws Exception {
        String tenantToken = tenantToken();
        String platformToken = platformToken();
        String agentId = "eval-control-" + suffix();
        String templateCode = "eval-template-" + suffix();
        createAgent(tenantToken, agentId);

        mockMvc.perform(get("/platform/evaluation/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isForbidden());

        MvcResult suiteResult = mockMvc.perform(post("/platform/evaluation/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"平台隐藏挑战集",
                                  "description":"验证标准资产的租户隔离与发布不可变性",
                                  "gateMode":"BLOCKING",
                                  "minPassRate":1.0,
                                  "scopeType":"APP_STANDARD",
                                  "visibility":"SEALED",
                                  "templateCode":"%s",
                                  "agentId":"%s",
                                  "hiddenResults":true,
                                  "mandatory":true
                                }
                                """.formatted(templateCode, agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andReturn();
        long suiteId = data(suiteResult).path("id").asLong();

        String auditorToken = jwtService.issuePlatformToken("evaluation-auditor", List.of(RoleCodes.PLATFORM_AUDITOR));
        String billingToken = jwtService.issuePlatformToken("evaluation-billing", List.of(RoleCodes.PLATFORM_BILLING));
        mockMvc.perform(get("/platform/evaluation/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/platform/evaluation/suites/{suiteId}/cases", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/platform/evaluation/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + billingToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/platform/evaluation/suites/{suiteId}/cases", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"不可泄露的黄金挑战样本",
                                  "inputText":"SECRET_INDUSTRY_PROMPT_ASSET",
                                  "assertionType":"OUTPUT_NOT_CONTAINS",
                                  "forbiddenText":"SECRET_FORBIDDEN_CLAIM",
                                  "priority":"SAFETY",
                                  "category":"SAFETY",
                                  "caseKey":"sealed-safety-1",
                                  "reviewStatus":"APPROVED",
                                  "redactionStatus":"REDACTED",
                                  "hiddenCase":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hiddenCase").value(true));

        mockMvc.perform(post("/platform/evaluation/suites/{suiteId}/publish", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.releaseStatus").value("PUBLISHED"));

        mockMvc.perform(get("/evaluation/suites").param("agentId", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].platformOwned".formatted(suiteId)).value(true));

        mockMvc.perform(get("/evaluation/suites/{suiteId}/cases", suiteId).param("agentId", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("平台隐藏挑战用例"))
                .andExpect(jsonPath("$.data[0].inputText").value(""))
                .andExpect(jsonPath("$.data[0].expectedText").value(""))
                .andExpect(jsonPath("$.data[0].redacted").value(true));

        mockMvc.perform(put("/platform/evaluation/suites/{suiteId}", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"试图污染已发布版本",
                                  "scopeType":"APP_STANDARD",
                                  "visibility":"SEALED",
                                  "templateCode":"%s",
                                  "agentId":"%s",
                                  "mandatory":true
                                }
                                """.formatted(templateCode, agentId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("immutable")));
    }

    @Test
    void shouldKeepTenantSuiteAndCaseInsideCurrentOrganization() throws Exception {
        String tenantToken = tenantToken();
        String agentId = "tenant-eval-" + suffix();
        createAgent(tenantToken, agentId);

        MvcResult suiteResult = mockMvc.perform(post("/evaluation/suites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"agentId":"%s","name":"租户私有回归集","gateMode":"BLOCKING","minPassRate":0.95}
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeType").value("TENANT_PRIVATE"))
                .andReturn();
        long suiteId = data(suiteResult).path("id").asLong();

        MvcResult caseResult = mockMvc.perform(post("/evaluation/suites/{suiteId}/cases", suiteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s","name":"待审核生产回归","inputText":"客户手机号 13800138000",
                                  "assertionType":"STATUS_EQUALS","expectedStatus":"published-executed",
                                  "priority":"P1","category":"ANSWER_QUALITY","reviewStatus":"PENDING",
                                  "redactionStatus":"REDACTED"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn();
        long caseId = data(caseResult).path("id").asLong();

        String traceId = "eval-trace-" + suffix();
        Instant now = Instant.now();
        traceRepository.save(new AgentRunTraceEntity(
                traceId, "demo-org", "trace-user", "trace-session-" + suffix(), agentId, "web", "success",
                "生产问题", "待加入回归", "test-model", "", now.minusSeconds(1), now, 1000,
                1, 0, 0, "[]", "[]", "[]",
                """
                        {"request":{"question":"联系 13800138000，邮箱 alice@example.com，身份证 11010119900101123X，Bearer raw-secret-token"}}
                        """, now));
        mockMvc.perform(post("/evaluation/cases/from-trace")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suiteId":%d,"traceId":"%s","agentId":"%s","name":"脱敏回归用例"}
                                """.formatted(suiteId, traceId, agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.reviewRequired").value(true))
                .andExpect(jsonPath("$.data.inputText").value(org.hamcrest.Matchers.containsString("138****8000")))
                .andExpect(jsonPath("$.data.inputText").value(org.hamcrest.Matchers.containsString("a***@example.com")))
                .andExpect(jsonPath("$.data.inputText").value(org.hamcrest.Matchers.containsString("110***********123X")))
                .andExpect(jsonPath("$.data.inputText").value(org.hamcrest.Matchers.containsString("Bearer [redacted]")))
                .andExpect(jsonPath("$.data.inputText").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw-secret-token"))));

        mockMvc.perform(get("/evaluation/suites/{suiteId}/cases", suiteId).param("agentId", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].reviewStatus".formatted(caseId)).value("PENDING"));

        mockMvc.perform(put("/evaluation/suites/{suiteId}/cases/{caseId}", suiteId, caseId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s","name":"已审核生产回归","inputText":"客户手机号已脱敏",
                                  "assertionType":"STATUS_EQUALS","expectedStatus":"published-executed",
                                  "priority":"P1","category":"ANSWER_QUALITY","reviewStatus":"APPROVED",
                                  "redactionStatus":"REDACTED"
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.reviewStatus").value("APPROVED"));

        String otherAgentId = "tenant-eval-other-" + suffix();
        createAgent(tenantToken, otherAgentId);
        mockMvc.perform(post("/evaluation/issues")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tenantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s","caseId":%d,"title":"跨智能体错误引用",
                                  "rootCauseType":"PROMPT","severity":"P1"
                                }
                                """.formatted(otherAgentId, caseId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("does not belong")));
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void createAgent(String token, String agentId) throws Exception {
        mockMvc.perform(post("/agents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agentId":"%s","name":"评测控制面测试智能体","summary":"验证多租户评测治理",
                                  "model":"gpt-4.1","systemPrompt":"按组织规则回答","enabled":true,
                                  "specText":"接收输入并返回结果","channels":["web"]
                                }
                                """.formatted(agentId)))
                .andExpect(status().isOk());
    }

    private String tenantToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgId":"demo-org","mobile":"13800138188","password":"szyd1234"}
                                """))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("token").asText();
    }

    private String platformToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"admin@cloudcc.com","password":"szyd1234"}
                                """))
                .andExpect(status().isOk()).andReturn();
        return data(result).path("token").asText();
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
