package com.codehouse.ciciassistant.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCallLogRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiUsageDailyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class AgentOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Autowired
    private AgentWorkflowVersionRepository agentWorkflowVersionRepository;

    @Autowired
    private AgentChannelBindingRepository agentChannelBindingRepository;

    @Autowired
    private AgentApiCredentialRepository credentialRepository;

    @Autowired
    private AgentApiSessionMapRepository sessionMapRepository;

    @Autowired
    private AgentApiCallLogRepository callLogRepository;

    @Autowired
    private AgentApiUsageDailyRepository usageDailyRepository;

    @Autowired
    private AgentRunTraceRepository traceRepository;

    @MockBean
    private ChatOrchestratorService chatOrchestratorService;

    @Test
    void shouldCreateRotateRevokeApiKeyWithoutStoringPlainKey() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByOrgIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-key-agent";
        preparePublishedApiAgent(agentId);

        MvcResult createResult = mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "CRM production",
                                  "runAsUserId": "%s",
                                  "allowedIps": ["127.0.0.1/32"],
                                  "rateLimitPerMinute": 30,
                                  "dailyQuota": 3000,
                                  "allowStream": true
                                }
                                """.formatted(runAsUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.plainKey").isString())
                .andExpect(jsonPath("$.data.credential.keyPrefix").value(org.hamcrest.Matchers.startsWith("cici_ak_live_")))
                .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String plainKey = created.path("plainKey").asText();
        long credentialId = created.path("credential").path("id").asLong();
        AgentApiCredentialEntity stored = credentialRepository.findById(credentialId).orElseThrow();
        assertThat(stored.getKeyHash()).isNotBlank();
        assertThat(stored.getKeyHash()).doesNotContain(plainKey);
        assertThat(stored.getKeyHash()).isNotEqualTo(plainKey);

        mockMvc.perform(get("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].plainKey").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));

        mockMvc.perform(put("/agents/{agentId}/api-keys/{credentialId}", agentId, credentialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "CRM paused",
                                  "status": "PAUSED",
                                  "dailyQuota": 4000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("CRM paused"))
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.dailyQuota").value(4000));

        MvcResult rotateResult = mockMvc.perform(post("/agents/{agentId}/api-keys/{credentialId}/rotate", agentId, credentialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.credential.status").value("ACTIVE"))
                .andReturn();
        String rotatedPlainKey = objectMapper.readTree(rotateResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("plainKey")
                .asText();
        assertThat(rotatedPlainKey).startsWith("cici_ak_live_").isNotEqualTo(plainKey);

        mockMvc.perform(post("/agents/{agentId}/api-keys/{credentialId}/revoke", agentId, credentialId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REVOKED"));
    }

    @Test
    void shouldValidateOpenApiHealthWithoutTreatingApiKeyAsJwt() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByOrgIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-health-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);

        mockMvc.perform(get("/openapi/v1/agents/{agentId}/health", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agentId").value(agentId))
                .andExpect(jsonPath("$.data.apiChannelEnabled").value(true));

        mockMvc.perform(get("/openapi/v1/agents/{agentId}/health", agentId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("agent_api_key_missing"));

        String disabledAgentId = "openapi-disabled-channel-agent";
        preparePublishedAgent(disabledAgentId, false);
        String disabledAgentKey = createPlainKey(token, disabledAgentId, runAsUserId);
        mockMvc.perform(get("/openapi/v1/agents/{agentId}/health", disabledAgentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + disabledAgentKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("agent_channel_disabled"));
    }

    @Test
    void shouldRunOpenApiChatWithMappedSessionCallLogUsageAndTraceMetadata() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByOrgIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-chat-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);
        stubChatRuntime(agentId, runAsUserId, "外部调用回答");

        MvcResult result = mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat", agentId)
                        .header("X-Cici-Api-Key", plainKey)
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "crm-customer-001",
                                  "message": "hello open api",
                                  "externalUser": {
                                    "id": "customer-001",
                                    "name": "张三"
                                  },
                                  "metadata": {
                                    "source": "crm"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("外部调用回答"))
                .andExpect(jsonPath("$.data.sessionId").value("crm-customer-001"))
                .andExpect(jsonPath("$.data.internalSessionId").value(org.hamcrest.Matchers.startsWith("api:")))
                .andExpect(jsonPath("$.data.traceId").isString())
                .andExpect(jsonPath("$.data.runtime.ragContextCount").value(0))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String requestId = data.path("requestId").asText();
        String internalSessionId = data.path("internalSessionId").asText();
        String traceId = data.path("traceId").asText();

        assertThat(sessionMapRepository.findByInternalSessionId(internalSessionId)).isPresent();
        assertThat(callLogRepository.findById(requestId)).isPresent()
                .get()
                .satisfies(log -> {
                    assertThat(log.getStatus()).isEqualTo("SUCCESS");
                    assertThat(log.getTraceId()).isEqualTo(traceId);
                    assertThat(log.getExternalUserId()).isEqualTo("customer-001");
                    assertThat(log.getInternalSessionId()).isEqualTo(internalSessionId);
                });
        AgentRunTraceEntity trace = traceRepository.findById(traceId).orElseThrow();
        assertThat(trace.getChannel()).isEqualTo("api");
        assertThat(trace.getSourceType()).isEqualTo("open_api");
        assertThat(trace.getRequestId()).isEqualTo(requestId);
        assertThat(trace.getExternalUserId()).isEqualTo("customer-001");
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(plainKeyPublicId(plainKey)).orElseThrow();
        assertThat(usageDailyRepository.findByOrgIdAndCredentialIdAndUsageDate(
                "demo-org",
                credential.getId(),
                LocalDate.now(ZoneOffset.UTC))).isPresent()
                .get()
                .satisfies(usage -> {
                    assertThat(usage.getCallCount()).isEqualTo(1);
                    assertThat(usage.getSuccessCount()).isEqualTo(1);
                });

        mockMvc.perform(get("/agents/{agentId}/api-calls", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("q", "customer-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value(requestId))
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].externalUserId").value("customer-001"));
    }

    @Test
    void shouldRunOpenApiStreamWithMetaDoneCallLogUsageAndTraceMetadata() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByOrgIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-stream-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);
        stubChatStreamRuntime(agentId, runAsUserId, "stream openapi answer");

        MvcResult asyncResult = mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat/stream", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "crm-stream-001",
                                  "message": "hello stream",
                                  "externalUser": {
                                    "id": "stream-user-001"
                                  }
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult result = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:meta")))
                .andExpect(content().string(containsString("event:delta")))
                .andExpect(content().string(containsString("stream openapi answer")))
                .andExpect(content().string(containsString("event:done")))
                .andExpect(content().string(containsString("requestId")))
                .andExpect(content().string(containsString("traceId")))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        String requestId = extractSseValue(body, "requestId");
        String traceId = extractSseValue(body, "traceId");
        assertThat(requestId).startsWith("req_");
        assertThat(traceId).startsWith("trace-");

        assertThat(callLogRepository.findById(requestId)).isPresent()
                .get()
                .satisfies(log -> {
                    assertThat(log.getStatus()).isEqualTo("SUCCESS");
                    assertThat(log.getTraceId()).isEqualTo(traceId);
                    assertThat(log.getExternalUserId()).isEqualTo("stream-user-001");
                    assertThat(log.getResponseSummary()).contains("stream openapi answer");
                });
        AgentRunTraceEntity trace = traceRepository.findById(traceId).orElseThrow();
        assertThat(trace.getChannel()).isEqualTo("api");
        assertThat(trace.getSourceType()).isEqualTo("open_api");
        assertThat(trace.getRequestId()).isEqualTo(requestId);
        assertThat(trace.getExternalUserId()).isEqualTo("stream-user-001");
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(plainKeyPublicId(plainKey)).orElseThrow();
        assertThat(usageDailyRepository.findByOrgIdAndCredentialIdAndUsageDate(
                "demo-org",
                credential.getId(),
                LocalDate.now(ZoneOffset.UTC))).isPresent()
                .get()
                .satisfies(usage -> {
                    assertThat(usage.getCallCount()).isEqualTo(1);
                    assertThat(usage.getSuccessCount()).isEqualTo(1);
                });
    }

    @Test
    void shouldRejectOpenApiChatWhenMinuteRateLimitIsExceeded() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByOrgIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-rate-agent";
        preparePublishedApiAgent(agentId);
        MvcResult createResult = mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "rate smoke",
                                  "runAsUserId": "%s",
                                  "rateLimitPerMinute": 1
                                }
                                """.formatted(runAsUserId)))
                .andExpect(status().isOk())
                .andReturn();
        String plainKey = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("plainKey")
                .asText();
        stubChatRuntime(agentId, runAsUserId, "ok");

        String body = """
                {
                  "sessionId": "rate-session",
                  "message": "hello rate limit"
                }
                """;
        mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limit_exceeded"));
    }

    private String createPlainKey(String token, String agentId, String runAsUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "health smoke",
                                  "runAsUserId": "%s"
                                }
                                """.formatted(runAsUserId)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("plainKey")
                .asText();
    }

    private void preparePublishedApiAgent(String agentId) {
        preparePublishedAgent(agentId, true);
    }

    private void preparePublishedAgent(String agentId, boolean apiChannel) {
        credentialRepository.findByOrgIdAndAgentIdOrderByCreatedAtDesc("demo-org", agentId)
                .forEach(credentialRepository::delete);
        agentChannelBindingRepository.deleteByOrgIdAndAgentId("demo-org", agentId);
        agentWorkflowVersionRepository.findByOrgIdAndAgentIdOrderByVersionNoDesc("demo-org", agentId)
                .forEach(agentWorkflowVersionRepository::delete);
        agentDefinitionRepository.findByOrgIdAndAgentId("demo-org", agentId)
                .ifPresent(agentDefinitionRepository::delete);
        agentChannelBindingRepository.flush();
        agentWorkflowVersionRepository.flush();
        agentDefinitionRepository.flush();
        AgentDefinitionEntity agent = agentDefinitionRepository.save(new AgentDefinitionEntity(
                "demo-org",
                agentId,
                "Open API Agent",
                "test",
                "hello",
                "cici-default",
                "",
                "",
                "BALANCED",
                "COPILOT",
                "v1",
                null,
                false,
                true));
        AgentWorkflowVersionEntity version = agentWorkflowVersionRepository.save(new AgentWorkflowVersionEntity(
                "demo-org",
                agentId,
                1,
                "v1",
                "test",
                "export default {}",
                "{}",
                "{}",
                "[]",
                "[]",
                "[]",
                "openapi-test",
                "[]",
                "PUBLISHED"));
        agent.setPublishedVersionId(version.getId());
        agentDefinitionRepository.save(agent);
        if (apiChannel) {
            agentChannelBindingRepository.save(new AgentChannelBindingEntity("demo-org", agentId, "api", true));
        }
        agentDefinitionRepository.flush();
        agentChannelBindingRepository.flush();
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

    private void stubChatRuntime(String agentId, String runAsUserId, String answer) {
        when(chatOrchestratorService.chat(
                eq("demo-org"),
                eq(runAsUserId),
                anyString(),
                anyString(),
                any(),
                eq(agentId),
                any()))
                .thenAnswer(invocation -> {
                    String sessionId = invocation.getArgument(2);
                    String question = invocation.getArgument(3);
                    String traceId = "trace-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
                    Instant started = Instant.now().minusMillis(20);
                    traceRepository.save(new AgentRunTraceEntity(
                            traceId,
                            "demo-org",
                            runAsUserId,
                            sessionId,
                            agentId,
                            "api",
                            "COMPLETED",
                            question,
                            answer,
                            "mock-model",
                            "",
                            started,
                            Instant.now(),
                            20,
                            1,
                            0,
                            0,
                            "[]",
                            "[]",
                            "[]",
                            "{}",
                            Instant.now()));
                    return Map.of(
                            "answer", answer,
                            "model", Map.of("modelName", "mock-model"),
                            "ragContext", List.of(),
                            "resolvedSkills", List.of("general-assistant"),
                            "effectiveToolNames", List.of(),
                            "agentId", agentId);
                });
    }

    private void stubChatStreamRuntime(String agentId, String runAsUserId, String answer) {
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(2);
            String question = invocation.getArgument(3);
            SseEmitter emitter = invocation.getArgument(7);
            String traceId = "trace-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
            Instant started = Instant.now().minusMillis(20);
            traceRepository.save(new AgentRunTraceEntity(
                    traceId,
                    "demo-org",
                    runAsUserId,
                    sessionId,
                    agentId,
                    "api",
                    "COMPLETED",
                    question,
                    answer,
                    "mock-model",
                    "",
                    started,
                    Instant.now(),
                    20,
                    1,
                    0,
                    0,
                    "[]",
                    "[]",
                    "[]",
                    "{}",
                    Instant.now()));
            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", answer)));
            emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true)));
            emitter.complete();
            return null;
        }).when(chatOrchestratorService).chatStream(
                eq("demo-org"),
                eq(runAsUserId),
                anyString(),
                anyString(),
                any(),
                eq(agentId),
                any(),
                any());
    }

    private String extractSseValue(String body, String field) {
        String needle = "\"" + field + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = body.indexOf('"', valueStart);
        return end < 0 ? "" : body.substring(valueStart, end);
    }

    private String plainKeyPublicId(String plainKey) {
        String suffix = plainKey.substring("cici_ak_live_".length());
        return suffix.substring(0, suffix.indexOf('_'));
    }
}
