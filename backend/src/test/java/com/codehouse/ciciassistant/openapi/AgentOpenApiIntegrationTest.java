package com.codehouse.ciciassistant.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingEntity;
import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentAccessGrantRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentWorkflowVersionRepository;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.auth.RoleCodes;
import com.codehouse.ciciassistant.auth.domain.CompanyRepository;
import com.codehouse.ciciassistant.auth.domain.UserAccountEntity;
import com.codehouse.ciciassistant.auth.domain.UserAccountRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.billing.domain.BillingCreditLedgerRepository;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigEntity;
import com.codehouse.ciciassistant.model.domain.CompanyModelConfigRepository;
import com.codehouse.ciciassistant.model.service.ModelProviderService;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService.CloudccSessionContext;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCallLogRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiSessionMapRepository;
import com.codehouse.ciciassistant.openapi.domain.AgentApiUsageDailyRepository;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingEntity;
import com.codehouse.ciciassistant.skill.domain.AgentSkillBindingRepository;
import com.codehouse.ciciassistant.skill.domain.SkillBindingPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillEditPolicy;
import com.codehouse.ciciassistant.skill.domain.SkillSourceType;
import com.codehouse.ciciassistant.skill.domain.SkillUpdatePolicy;
import com.codehouse.ciciassistant.skill.domain.SkillVisibility;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=default",
        "app.agent-open-api.cors-allowed-origins=*"
})
class AgentOpenApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Autowired
    private AgentWorkflowVersionRepository agentWorkflowVersionRepository;

    @Autowired
    private AgentChannelBindingRepository agentChannelBindingRepository;

    @Autowired
    private AgentAccessGrantRepository agentAccessGrantRepository;

    @Autowired
    private AgentApiCredentialRepository credentialRepository;

    @Autowired
    private AgentApiSessionMapRepository sessionMapRepository;

    @Autowired
    private AgentApiCallLogRepository callLogRepository;

    @Autowired
    private AgentApiUsageDailyRepository usageDailyRepository;

    @Autowired
    private UsageMeterEventRepository usageMeterEventRepository;

    @Autowired
    private BillingCreditLedgerRepository creditLedgerRepository;

    @Autowired
    private AgentRunTraceRepository traceRepository;

    @Autowired
    private CompanyModelConfigRepository orgModelConfigRepository;

    @Autowired
    private ModelProviderService modelProviderService;

    @Autowired
    private CloudccAccessTokenService cloudccAccessTokenService;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Autowired
    private AgentSkillBindingRepository agentSkillBindingRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private ChatOrchestratorService chatOrchestratorService;

    @Test
    void shouldAllowWildcardCorsPreflightForOpenApiStream() throws Exception {
        mockMvc.perform(options("/openapi/v1/chat-messages")
                        .header(HttpHeaders.ORIGIN, "https://cnbh01.cloudcc.cn")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("authorization")));
    }

    @Test
    void shouldAllowAnyOriginCorsPreflightForOpenApi() throws Exception {
        mockMvc.perform(options("/openapi/v1/chat-messages")
                        .header(HttpHeaders.ORIGIN, "https://another.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*"));
    }

    @Test
    void shouldCreateRotateRevokeApiKeyWithoutStoringPlainKey() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
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
                .andExpect(jsonPath("$.data[0].keyType").value("standard"))
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
    void shouldRejectApiKeyCreationWhenRunAsUserLacksAgentRunPermission() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = ensureOrgUser("13900007777");
        String agentId = "openapi-run-denied-agent";
        preparePublishedApiAgent(agentId);

        mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "denied run-as",
                                  "runAsUserId": "%s"
                                }
                                """.formatted(runAsUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("runAsUserId must have target Agent RUN permission"));
    }

    @Test
    void shouldValidateOpenApiParametersWithoutTreatingApiKeyAsJwt() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-parameters-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);

        mockMvc.perform(get("/openapi/v1/parameters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.opening_statement").value("hello"))
                .andExpect(jsonPath("$.data.file_upload.enabled").value(true));

        mockMvc.perform(get("/openapi/v1/parameters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("agent_api_key_missing"));

        String disabledAgentId = "openapi-disabled-channel-agent";
        preparePublishedAgent(disabledAgentId, false);
        String disabledAgentKey = createPlainKey(token, disabledAgentId, runAsUserId);
        mockMvc.perform(get("/openapi/v1/parameters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + disabledAgentKey))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("agent_channel_disabled"));
    }

    @Test
    void shouldNotExposeLegacyOpenApiRoutes() throws Exception {
        mockMvc.perform(get("/openapi/v1/agents/{agentId}/parameters", "legacy-openapi-agent"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat-messages", "legacy-openapi-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/openapi/v1/agents/{agentId}/health", "legacy-openapi-agent"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat", "legacy-openapi-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/openapi/v1/agents/{agentId}/chat/stream", "legacy-openapi-agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRunOpenApiChatMessagesWithMappedSessionCallLogUsageAndTraceMetadata() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-chat-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);
        stubChatRuntime(agentId, runAsUserId, "外部调用回答");

        MvcResult result = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header("X-Cici-Api-Key", plainKey)
                        .header("X-Forwarded-For", "127.0.0.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "crm-customer-001",
                                  "query": "hello open api",
                                  "user": "customer-001",
                                  "metadata": {
                                    "source": "crm"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("外部调用回答"))
                .andExpect(jsonPath("$.conversation_id").value("crm-customer-001"))
                .andExpect(jsonPath("$.metadata.trace_id").isString())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String traceId = data.path("metadata").path("trace_id").asText();
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(plainKeyPublicId(plainKey)).orElseThrow();
        String internalSessionId = sessionMapRepository
                .findByCompanyIdAndCredentialIdAndAgentIdAndExternalSessionId("demo-org", credential.getId(), agentId, "crm-customer-001")
                .orElseThrow()
                .getInternalSessionId();

        assertThat(sessionMapRepository.findByInternalSessionId(internalSessionId)).isPresent();
        assertThat(callLogRepository.findAll())
                .filteredOn(log -> traceId.equals(log.getTraceId()))
                .singleElement()
                .satisfies(log -> {
                    assertThat(log.getStatus()).isEqualTo("SUCCESS");
                    assertThat(log.getTraceId()).isEqualTo(traceId);
                    assertThat(log.getExternalUserId()).isEqualTo("customer-001");
                    assertThat(log.getInternalSessionId()).isEqualTo(internalSessionId);
                });
        AgentRunTraceEntity trace = traceRepository.findById(traceId).orElseThrow();
        assertThat(trace.getChannel()).isEqualTo("api");
        assertThat(trace.getSourceType()).isEqualTo("open_api");
        assertThat(trace.getExternalUserId()).isEqualTo("customer-001");
        assertThat(usageDailyRepository.findByCompanyIdAndCredentialIdAndUsageDate(
                "demo-org",
                credential.getId(),
                LocalDate.now(ZoneOffset.UTC))).isPresent()
                .get()
                .satisfies(usage -> {
                    assertThat(usage.getCallCount()).isEqualTo(1);
                    assertThat(usage.getSuccessCount()).isEqualTo(1);
                });
        var openApiUsage = usageMeterEventRepository.findTop100ByCompanyIdOrderByOccurredAtDesc("demo-org").stream()
                .filter(item -> "open_api_chat".equals(item.getBillableDomain()))
                .filter(item -> item.getMetadataJson().contains(traceId))
                .toList();
        assertThat(openApiUsage).singleElement().satisfies(event -> {
            assertThat(event.getBillableItemCode()).isEqualTo("non_stream_chat");
            assertThat(event.getWorkCreditQuantity()).isEqualByComparingTo("2.00");
            assertThat(event.getBillingType()).isEqualTo("platform_paid");
            assertThat(event.getMetadataJson()).contains("\"officialPricingItem\":\"Credits 包\"");
        });
        assertThat(creditLedgerRepository.findByCompanyIdOrderByIdAsc("demo-org")).anySatisfy(entry -> {
            assertThat(entry.getEntryType()).isEqualTo("usage_debit");
            assertThat(entry.getCreditsDelta()).isEqualByComparingTo("-2.00");
            assertThat(entry.getSourceEventId()).isEqualTo(openApiUsage.get(0).getId());
        });

        mockMvc.perform(get("/agents/{agentId}/api-calls", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("q", "customer-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data[0].externalUserId").value("customer-001"));
    }

    @Test
    void shouldRequireCloudccContextOnlyForCloudccOpenApiKeys() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-cloudcc-context-rules";
        preparePublishedApiAgent(agentId);
        String standardKey = createPlainKey(token, agentId, runAsUserId);
        String cloudccKey = createPlainKey(token, agentId, runAsUserId, "cloudcc");
        String callerToken = futureJwt();

        MvcResult standardResult = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + standardKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-standard-session",
                                  "query": "hello",
                                  "cloudccContext": {
                                    "accessToken": "%s",
                                    "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi"
                                  }
                                }
                                """.formatted(callerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("cloudcc_context_not_allowed"))
                .andReturn();
        assertThat(standardResult.getResponse().getContentAsString()).doesNotContain(callerToken);

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cloudccKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-missing-session",
                                  "query": "hello"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("cloudcc_token_required"));
    }

    @Test
    void shouldUseCallerSuppliedCloudccTokenWithoutPersistingIt() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-cloudcc-runtime-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId, "cloudcc");
        String callerToken = futureJwt();
        AtomicReference<CloudccSessionContext> observedContext = new AtomicReference<>();
        stubChatRuntimeWithCloudccContext(agentId, runAsUserId, "CloudCC caller token accepted", observedContext);

        MvcResult result = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-runtime-session",
                                  "query": "use caller token",
                                  "cloudccContext": {
                                    "accessToken": "%s",
                                    "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi"
                                  }
                                }
                                """.formatted(callerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("CloudCC caller token accepted"))
                .andReturn();

        assertThat(observedContext.get()).isNotNull();
        assertThat(observedContext.get().accessToken()).isEqualTo(callerToken);
        assertThat(observedContext.get().baseUrl()).isEqualTo("https://szyd.apis.cloudcc.cn/lightningapi");

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String traceId = data.path("metadata").path("trace_id").asText();
        AgentApiCredentialEntity credential = credentialRepository.findByPublicId(plainKeyPublicId(plainKey)).orElseThrow();
        assertThat(credential.getKeyType()).isEqualTo("cloudcc");
        assertThat(callLogRepository.findAll())
                .filteredOn(log -> credential.getId().equals(log.getCredentialId()))
                .allSatisfy(log -> {
                    assertThat(log.getRequestSummary()).doesNotContain(callerToken);
                    assertThat(log.getResponseSummary()).doesNotContain(callerToken);
                    assertThat(String.valueOf(log.getErrorCode())).doesNotContain(callerToken);
                });
        AgentRunTraceEntity trace = traceRepository.findById(traceId).orElseThrow();
        assertThat(trace.getTitle()).doesNotContain(callerToken);
        assertThat(trace.getSummary()).doesNotContain(callerToken);
        assertThat(trace.getDetailJson()).doesNotContain(callerToken);
    }

    @Test
    void shouldRejectCloudccCallerTokenWhenBaseUrlIsDenied() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-cloudcc-url-denied-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId, "cloudcc");
        String callerToken = futureJwt();

        MvcResult result = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-denied-session",
                                  "query": "hello",
                                  "cloudccContext": {
                                    "accessToken": "%s",
                                    "baseUrl": "http://127.0.0.1:8080/lightningapi"
                                  }
                                }
                                """.formatted(callerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("cloudcc_base_url_denied"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(callerToken);

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-denied-prefix-session",
                                  "query": "hello",
                                  "cloudccContext": {
                                    "accessToken": "%s",
                                    "baseUrl": "https://szyd.apis.cloudcc.cn/lightningapi2"
                                  }
                                }
                                """.formatted(callerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("cloudcc_base_url_denied"));
    }

    @Test
    void shouldAcceptCloudccLightningDomainGatewayBaseUrl() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-cloudcc-lightning-domain-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId, "cloudcc");
        String callerToken = futureJwt();
        AtomicReference<CloudccSessionContext> observedContext = new AtomicReference<>();
        stubChatRuntimeWithCloudccContext(agentId, runAsUserId, "CloudCC lightning gateway accepted", observedContext);

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "cloudcc-lightning-session",
                                  "query": "hello",
                                  "cloudccContext": {
                                    "accessToken": "%s",
                                    "baseUrl": "https://yundong.lightning.cloudcc.cn/ccdomaingateway/apisvc"
                                  }
                                }
                                """.formatted(callerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("CloudCC lightning gateway accepted"));

        assertThat(observedContext.get()).isNotNull();
        assertThat(observedContext.get().accessToken()).isEqualTo(callerToken);
        assertThat(observedContext.get().baseUrl()).isEqualTo("https://yundong.lightning.cloudcc.cn/ccdomaingateway/apisvc");
        assertThat(observedContext.get().setupSvc()).isEqualTo("https://yundong.lightning.cloudcc.cn/ccdomaingateway/setup");
    }

    @Test
    void shouldExposeConversationApiChatMessagesHistoryFeedbackAndFiles() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-conversation-agent";
        preparePublishedApiAgent(agentId);
        String plainKey = createPlainKey(token, agentId, runAsUserId);
        stubChatRuntime(agentId, runAsUserId, "会话服务回答");

        mockMvc.perform(get("/openapi/v1/parameters")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.opening_statement").value("hello"))
                .andExpect(jsonPath("$.data.file_upload.enabled").value(true));

        MockMultipartFile rejectedFile = new MockMultipartFile(
                "file",
                "unsafe.bin",
                MediaType.APPLICATION_OCTET_STREAM_VALUE,
                "unsafe".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/openapi/v1/files/upload")
                        .file(rejectedFile)
                        .param("user", "customer-conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("file_type_not_allowed"));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "case-note.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "case note".getBytes(StandardCharsets.UTF_8));
        MvcResult uploadResult = mockMvc.perform(multipart("/openapi/v1/files/upload")
                        .file(file)
                        .param("user", "customer-conversation")
                        .param("conversation_id", "conv-conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("case-note.txt"))
                .andReturn();
        String fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("id")
                .asText();

        MvcResult chatResult = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .header("Idempotency-Key", "conversation-idempotency-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "请分析附件",
                                  "user": "customer-conversation",
                                  "responseMode": "blocking",
                                  "conversationId": "conv-conversation",
                                  "inputs": {
                                    "source": "crm"
                                  },
                                  "files": [
                                    {
                                      "type": "document",
                                      "transfer_method": "local_file",
                                      "upload_file_id": "%s"
                                    }
                                  ]
                                }
                                """.formatted(fileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").isString())
                .andExpect(jsonPath("$.message_id").isString())
                .andExpect(jsonPath("$.conversation_id").value("conv-conversation"))
                .andExpect(jsonPath("$.answer").value("会话服务回答"))
                .andExpect(jsonPath("$.metadata.usage.elapsed_ms").isNumber())
                .andReturn();
        JsonNode chat = objectMapper.readTree(chatResult.getResponse().getContentAsString(StandardCharsets.UTF_8));
        String taskId = chat.path("task_id").asText();
        String messageId = chat.path("message_id").asText();

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .header("Idempotency-Key", "conversation-idempotency-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "请分析附件",
                                  "user": "customer-conversation",
                                  "conversationId": "conv-conversation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message_id").value(messageId))
                .andExpect(jsonPath("$.metadata.idempotentReplay").value(true));

        MvcResult secondChatResult = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "继续分析附件",
                                  "user": "customer-conversation",
                                  "response_mode": "blocking",
                                  "conversation_id": "conv-conversation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversation_id").value("conv-conversation"))
                .andReturn();
        String secondMessageId = objectMapper.readTree(secondChatResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("message_id")
                .asText();

        MvcResult streamResult = mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "流式继续分析",
                                  "user": "customer-conversation",
                                  "response_mode": "streaming",
                                  "conversation_id": "conv-conversation"
                                }
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();
        String streamContent = mockMvc.perform(asyncDispatch(streamResult))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:message")))
                .andExpect(content().string(containsString("event:message_end")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(countOccurrences(streamContent, "event:message\n")).isGreaterThanOrEqualTo(2);
        assertThat(streamContent.indexOf("event:message\n")).isLessThan(streamContent.indexOf("event:message_end\n"));

        mockMvc.perform(post("/openapi/v1/messages/{messageId}/feedbacks", messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rating": "like",
                                  "content": "useful"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("success"))
                .andExpect(jsonPath("$.rating").value("like"));

        mockMvc.perform(get("/openapi/v1/messages")
                        .queryParam("conversation_id", "conv-conversation")
                        .queryParam("user", "customer-conversation")
                        .queryParam("limit", "1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].message_id").isString())
                .andExpect(jsonPath("$.data.has_more").value(true))
                .andExpect(jsonPath("$.data.limit").value(1));

        mockMvc.perform(get("/openapi/v1/messages")
                        .queryParam("conversation_id", "conv-conversation")
                        .queryParam("user", "customer-conversation")
                        .queryParam("first_id", secondMessageId)
                        .queryParam("limit", "20")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].message_id").value(messageId))
                .andExpect(jsonPath("$.data.data[0].feedback.rating").value("like"));

        mockMvc.perform(get("/openapi/v1/messages/{messageId}/suggested", messageId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").isString());

        mockMvc.perform(post("/openapi/v1/conversations/{conversationId}/name", "conv-conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "CRM 客户会话"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("CRM 客户会话"));

        mockMvc.perform(get("/openapi/v1/conversations")
                        .queryParam("user", "customer-conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].id").value("conv-conversation"));

        mockMvc.perform(post("/openapi/v1/chat-messages/{taskId}/stop", taskId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("cancel_requested"));

        mockMvc.perform(delete("/openapi/v1/conversations/{conversationId}", "conv-conversation")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("deleted"));
    }

    @Test
    void shouldReplacePlaceholderChatRouteWithConfiguredBaseModelForOpenApiChatMessages() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-model-route-agent";
        modelProviderService.agentBaseModels("demo-org");
        modelProviderService.updateSelectedModels("demo-org", "aliyun-bailian", List.of("qwen3.6-plus"));
        CompanyModelConfigEntity route = orgModelConfigRepository.findByCompanyIdAndSceneCode("demo-org", "chat")
                .orElse(new CompanyModelConfigEntity("demo-org", "chat", "mock", "cici-default"));
        route.update("mock", "cici-default");
        orgModelConfigRepository.save(route);
        preparePublishedApiAgent(agentId);
        agentDefinitionRepository.findByCompanyIdAndAgentId("demo-org", agentId)
                .ifPresent(agent -> {
                    agent.update(
                            agent.getName(),
                            agent.getSummary(),
                            agent.getGreeting(),
                            "qwen3.6-plus",
                            agent.getSystemPrompt(),
                            agent.getHandoffRule(),
                            agent.getSafetyLevel(),
                            agent.getExecutionMode(),
                            agent.getVersionLabel(),
                            agent.getAvatarBase64(),
                            true,
                            agent.isEnabled());
                    agentDefinitionRepository.save(agent);
                });
        String plainKey = createPlainKey(token, agentId, runAsUserId);
        stubChatRuntime(agentId, runAsUserId, "模型路由已补齐");

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "model-route-session",
                                  "query": "hello model route"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("模型路由已补齐"));

        assertThat(orgModelConfigRepository.findByCompanyIdAndSceneCode("demo-org", "chat")).isPresent()
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getProvider()).isEqualTo("aliyun-bailian");
                    assertThat(saved.getModelName()).isEqualTo("qwen3.6-plus");
                });
    }

    @Test
    void shouldRejectDisabledSkillBindingForOpenApiChatMessages() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-disabled-skill-agent";
        preparePublishedApiAgent(agentId);
        String skillCode = "openapi-disabled-skill-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        SkillDefinitionEntity skill = saveSkill(skillCode, true);
        transactionTemplate.executeWithoutResult(status -> {
            agentSkillBindingRepository.deleteByCompanyIdAndAgentId("demo-org", agentId);
            agentSkillBindingRepository.save(new AgentSkillBindingEntity(
                    "demo-org",
                    agentId,
                    skill.getId(),
                    "ALWAYS",
                    "",
                    1,
                    false));
        });
        String plainKey = createPlainKey(token, agentId, runAsUserId);

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "disabled-skill-session",
                                  "query": "hello",
                                  "activeSkillCode": "%s"
                                }
                                """.formatted(skillCode)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("skill_not_allowed"));
    }

    @Test
    void shouldRejectOpenApiChatMessagesWhenAnswerExceedsConfiguredLimit() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String agentId = "openapi-response-limit-agent";
        preparePublishedApiAgent(agentId);
        MvcResult createResult = mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "response limit",
                                  "runAsUserId": "%s",
                                  "maxResponseChars": 4
                                }
                                """.formatted(runAsUserId)))
                .andExpect(status().isOk())
                .andReturn();
        String plainKey = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("plainKey")
                .asText();
        stubChatRuntime(agentId, runAsUserId, "answer too long");

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "conversationId": "response-limit-session",
                                  "query": "hello"
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code").value("response_too_large"));
    }

    @Test
    void shouldRejectOpenApiChatMessagesWhenMinuteRateLimitIsExceeded() throws Exception {
        String token = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
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
                  "conversationId": "rate-session",
                  "query": "hello rate limit"
                }
                """;
        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/openapi/v1/chat-messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("rate_limit_exceeded"));
    }

    private String createPlainKey(String token, String agentId, String runAsUserId) throws Exception {
        return createPlainKey(token, agentId, runAsUserId, "standard");
    }

    private String createPlainKey(String token, String agentId, String runAsUserId, String keyType) throws Exception {
        MvcResult result = mockMvc.perform(post("/agents/{agentId}/api-keys", agentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "open api smoke",
                                  "runAsUserId": "%s",
                                  "keyType": "%s"
                                }
                                """.formatted(runAsUserId, keyType)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("plainKey")
                .asText();
    }

    private String ensureOrgUser(String mobile) {
        return transactionTemplate.execute(status -> {
            if (userRepository.findByCompanyIdAndMobile("demo-org", mobile).isPresent()) {
                return userRepository.findByCompanyIdAndMobile("demo-org", mobile).orElseThrow().getId();
            }
            UserAccountEntity account = userAccountRepository.findByPrimaryMobile(mobile)
                    .orElseGet(() -> userAccountRepository.save(new UserAccountEntity(mobile)));
            UserEntity member = new UserEntity(
                    companyRepository.findById("demo-org").orElseThrow(),
                    account,
                    RoleCodes.ORG_USER);
            return userRepository.save(member).getId();
        });
    }

    private void preparePublishedApiAgent(String agentId) {
        preparePublishedAgent(agentId, true);
    }

    private void preparePublishedAgent(String agentId, boolean apiChannel) {
        transactionTemplate.executeWithoutResult(status -> {
            credentialRepository.findByCompanyIdAndAgentIdOrderByCreatedAtDesc("demo-org", agentId)
                    .forEach(credentialRepository::delete);
            agentAccessGrantRepository.findByCompanyIdAndAgentIdAndStatus("demo-org", agentId, "ACTIVE")
                    .forEach(agentAccessGrantRepository::delete);
            agentChannelBindingRepository.deleteByCompanyIdAndAgentId("demo-org", agentId);
            agentWorkflowVersionRepository.findByCompanyIdAndAgentIdOrderByVersionNoDesc("demo-org", agentId)
                    .forEach(agentWorkflowVersionRepository::delete);
            agentDefinitionRepository.findByCompanyIdAndAgentId("demo-org", agentId)
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
        });
    }

    private SkillDefinitionEntity saveSkill(String skillCode, boolean enabled) {
        return transactionTemplate.execute(status -> {
            skillDefinitionRepository.findByCompanyIdAndSkillCode("demo-org", skillCode)
                    .ifPresent(skillDefinitionRepository::delete);
            skillDefinitionRepository.flush();
            SkillDefinitionEntity skill = new SkillDefinitionEntity(
                    "demo-org",
                    skillCode,
                    "Open API Skill",
                    "test",
                    false,
                    enabled,
                    "prompt",
                    "spec",
                    "",
                    "",
                    "",
                    "",
                    "LOW",
                    SkillSourceType.TENANT_CUSTOM,
                    SkillVisibility.VISIBLE,
                    SkillEditPolicy.EDITABLE,
                    SkillBindingPolicy.OPTIONAL,
                    SkillUpdatePolicy.MANUAL,
                    null,
                    null);
            return skillDefinitionRepository.saveAndFlush(skill);
        });
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
        stubChatStreamRuntime(agentId, runAsUserId, answer);
    }

    private void stubChatStreamRuntime(String agentId, String runAsUserId, String answer) {
        doAnswer(invocation -> {
            String sessionId = invocation.getArgument(2);
            String question = invocation.getArgument(3);
            SseEmitter emitter = invocation.getArgument(7);
            String first = answer.length() <= 1 ? answer : answer.substring(0, answer.length() / 2);
            String second = answer.length() <= 1 ? "" : answer.substring(answer.length() / 2);
            emitter.send(SseEmitter.event().name("delta").data(Map.of("text", first)));
            if (!second.isBlank()) {
                emitter.send(SseEmitter.event().name("delta").data(Map.of("text", second)));
            }
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
            emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true)));
            emitter.complete();
            return null;
        }).when(chatOrchestratorService).chatStreamBlocking(
                eq("demo-org"),
                eq(runAsUserId),
                anyString(),
                anyString(),
                any(),
                eq(agentId),
                any(),
                any(SseEmitter.class));
    }

    private void stubChatRuntimeWithCloudccContext(String agentId,
                                                   String runAsUserId,
                                                   String answer,
                                                   AtomicReference<CloudccSessionContext> observedContext) {
        when(chatOrchestratorService.chat(
                eq("demo-org"),
                eq(runAsUserId),
                anyString(),
                anyString(),
                any(),
                eq(agentId),
                any()))
                .thenAnswer(invocation -> {
                    observedContext.set(cloudccAccessTokenService.getSessionContext("demo-org", runAsUserId).orElse(null));
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

    private String futureJwt() {
        return jwtWithExp(Instant.now().plusSeconds(3600));
    }

    private String jwtWithExp(Instant expiresAt) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"exp\":" + expiresAt.getEpochSecond() + "}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    private int countOccurrences(String value, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int index = value.indexOf(needle, from);
            if (index < 0) {
                return count;
            }
            count++;
            from = index + needle.length();
        }
    }

    private String plainKeyPublicId(String plainKey) {
        String suffix = plainKey.substring("cici_ak_live_".length());
        return suffix.substring(0, suffix.indexOf('_'));
    }
}
