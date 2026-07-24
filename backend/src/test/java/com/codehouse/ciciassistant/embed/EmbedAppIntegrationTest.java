package com.codehouse.ciciassistant.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.ai.service.MeetingMinutesService;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.billing.domain.BillingSubscriptionRepository;
import com.codehouse.ciciassistant.billing.domain.UsageMeterEventRepository;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionEntity;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionRepository;
import com.codehouse.ciciassistant.integration.service.CloudccAccessTokenService;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCredentialRepository;
import com.codehouse.ciciassistant.openapi.service.AgentApiKeyGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class EmbedAppIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentApiCredentialRepository credentialRepository;

    @Autowired
    private AgentApiKeyGenerator keyGenerator;

    @Autowired
    private MeetingSessionRepository meetingSessionRepository;

    @Autowired
    private UsageMeterEventRepository usageMeterEventRepository;

    @Autowired
    private BillingSubscriptionRepository billingSubscriptionRepository;

    @Autowired
    private IntegrationAppService integrationAppService;

    @Autowired
    private CloudccAccessTokenService cloudccAccessTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private MeetingMinutesService meetingMinutesService;

    @Test
    void shouldIssueEmbedTokenCreateSessionAndSummarizeWithOriginGuard() throws Exception {
        String adminToken = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();
        String plainKey = createPlainKey(runAsUserId);

        mockMvc.perform(put("/embed/v1/admin/apps/meeting-minutes/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "allowedOrigins": ["https://crm.example.com"],
                                  "tokenTtlSeconds": 600,
                                  "scopeOverrides": ["meeting:start", "meeting:summary", "crm:writeback"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.config.allowedOrigins[0]").value("https://crm.example.com"));

        MvcResult debugTokenResult = mockMvc.perform(post("/embed/v1/admin/apps/meeting-minutes/debug-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("https://crm.example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embedToken").isString())
                .andExpect(jsonPath("$.data.permissions[0]").value("meeting:start"))
                .andReturn();
        String debugToken = objectMapper.readTree(debugTokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("embedToken")
                .asText();

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + debugToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(org.hamcrest.Matchers.startsWith("meet_")));

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/tokens")
                        .header("X-Cici-Api-Key", plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("http://localhost:5173")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        MvcResult tokenResult = mockMvc.perform(post("/embed/v1/apps/meeting-minutes/tokens")
                        .header("X-Cici-Api-Key", plainKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("https://crm.example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.embedToken").isString())
                .andExpect(jsonPath("$.data.embedUrl").value("/embed/meeting-minutes"))
                .andExpect(jsonPath("$.data.permissions[0]").value("meeting:start"))
                .andReturn();
        String embedToken = objectMapper.readTree(tokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("embedToken")
                .asText();

        MvcResult sessionResult = mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(org.hamcrest.Matchers.startsWith("meet_")))
                .andExpect(jsonPath("$.data.objectType").value("Opportunity"))
                .andExpect(jsonPath("$.data.objectId").value("006xx000001"))
                .andReturn();
        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("sessionId")
                .asText();

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId));

        when(meetingMinutesService.summarize(eq("demo-org"), eq("华东区续费商机"), anyList()))
                .thenReturn(new MeetingMinutesService.MeetingMinutesResult(
                        "## Meeting Summary\n- 客户确认下周评审\n\n## 待办\n- 下周三前发送报价方案",
                        "ai-meeting-notetaker",
                        "AI 听记"));

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/summary", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "transcript": [
                                    {
                                      "speakerId": "1",
                                      "speakerName": "张三",
                                      "text": "客户确认下周评审。",
                                      "startMs": 0,
                                      "endMs": 1200
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(MeetingSessionEntity.STATUS_READY_TO_WRITEBACK))
                .andExpect(jsonPath("$.data.summary").value(org.hamcrest.Matchers.containsString("Meeting Summary")))
                .andExpect(jsonPath("$.data.skillCode").value("ai-meeting-notetaker"));

        MeetingSessionEntity stored = meetingSessionRepository.findById(sessionId).orElseThrow();
        assertThat(stored.getCompanyId()).isEqualTo("demo-org");
        assertThat(stored.getUserId()).isEqualTo(runAsUserId);
        assertThat(stored.getSource()).isEqualTo("cloudcc");
        assertThat(stored.getObjectType()).isEqualTo("Opportunity");
        assertThat(stored.getObjectId()).isEqualTo("006xx000001");
        assertThat(stored.getSummaryMarkdown()).contains("客户确认下周评审");
        var workflowUsage = usageMeterEventRepository
                .findBySourceTypeAndSourceId("meeting-minutes", "demo-org:" + sessionId + ":workflow_run")
                .orElseThrow();
        assertThat(workflowUsage.getBillableItemCode()).isEqualTo("workflow_credit");
        assertThat(workflowUsage.getWorkCreditQuantity()).isGreaterThan(BigDecimal.ZERO);
        assertThat(billingSubscriptionRepository.findByCompanyId("demo-org").orElseThrow().getConsumedCredits())
                .isGreaterThan(BigDecimal.ZERO);

        MvcResult previewResult = mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback-preview", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview.items[0].id").value("summary-note"))
                .andExpect(jsonPath("$.data.preview.items[1].id").value("task-1"))
                .andExpect(jsonPath("$.data.preview.items[2].id").value("field-next_step__c"))
                .andReturn();
        JsonNode preview = objectMapper.readTree(previewResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("preview");
        assertThat(preview.path("items").get(0).path("writeback").path("serviceName").asText()).isEqualTo("insert");

        List<JsonNode> cloudccCalls = Collections.synchronizedList(new ArrayList<>());
        HttpServer cloudcc = startCloudccServer(cloudccCalls, false);
        try {
            configureCloudcc(runAsUserId, cloudcc);
            mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback", sessionId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "selectedItemIds": ["summary-note", "task-1", "field-next_step__c"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(MeetingSessionEntity.STATUS_WRITTEN_BACK))
                    .andExpect(jsonPath("$.data.writeback.status").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.writeback.items[0].objectApiName").value("Note"))
                    .andExpect(jsonPath("$.data.writeback.items[1].objectApiName").value("Task"))
                    .andExpect(jsonPath("$.data.writeback.items[2].serviceName").value("update"));
        } finally {
            cloudcc.stop(0);
        }
        List<String> serviceNames = cloudccCalls.stream().map(node -> node.path("serviceName").asText()).toList();
        assertThat(serviceNames).containsExactly("insert", "insert", "update");
        assertThat(cloudccCalls.get(0).path("objectApiName").asText()).isEqualTo("Note");
        assertThat(cloudccCalls.get(1).path("objectApiName").asText()).isEqualTo("Task");
        assertThat(cloudccCalls.get(2).path("objectApiName").asText()).isEqualTo("Opportunity");

        mockMvc.perform(get("/embed/v1/admin/apps/meeting-minutes/sessions?limit=5")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sessionId").isString())
                .andExpect(jsonPath("$.data[0].objectType").value("Opportunity"));
    }

    @Test
    void shouldRejectUnknownWritebackItemAndRollbackInsertedCloudccRecords() throws Exception {
        String adminToken = loginToken("13800138111");
        String runAsUserId = userRepository.findByCompanyIdAndMobile("demo-org", "13800138111").orElseThrow().getId();

        mockMvc.perform(put("/embed/v1/admin/apps/meeting-minutes/config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "allowedOrigins": ["http://localhost:5173"],
                                  "tokenTtlSeconds": 600,
                                  "scopeOverrides": ["meeting:start", "meeting:summary", "crm:writeback"]
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult debugTokenResult = mockMvc.perform(post("/embed/v1/admin/apps/meeting-minutes/debug-token")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenRequest("http://localhost:5173")))
                .andExpect(status().isOk())
                .andReturn();
        String embedToken = objectMapper.readTree(debugTokenResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("embedToken")
                .asText();

        MvcResult sessionResult = mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = objectMapper.readTree(sessionResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("sessionId")
                .asText();

        when(meetingMinutesService.summarize(eq("demo-org"), eq("华东区续费商机"), anyList()))
                .thenReturn(new MeetingMinutesService.MeetingMinutesResult(
                        "## Meeting Summary\n- 客户确认下周评审\n\n## 待办\n- 下周三前发送报价方案",
                        "ai-meeting-notetaker",
                        "AI 听记"));
        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/summary", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transcript\":[{\"speakerId\":\"1\",\"speakerName\":\"张三\",\"text\":\"客户确认下周评审。\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback-preview", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback", sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selectedItemIds\":[\"summary-note\",\"unknown-item\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        List<JsonNode> cloudccCalls = Collections.synchronizedList(new ArrayList<>());
        HttpServer cloudcc = startCloudccServer(cloudccCalls, true);
        try {
            configureCloudcc(runAsUserId, cloudcc);
            mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions/{sessionId}/writeback", sessionId)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + embedToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"selectedItemIds\":[\"summary-note\",\"task-1\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(MeetingSessionEntity.STATUS_READY_TO_WRITEBACK))
                    .andExpect(jsonPath("$.data.writeback.status").value("FAILED"))
                    .andExpect(jsonPath("$.data.writeback.rollback[0].status").value("SUCCESS"));
        } finally {
            cloudcc.stop(0);
        }
        assertThat(cloudccCalls.stream().map(node -> node.path("serviceName").asText()).toList())
                .containsExactly("insert", "insert", "delete");
    }

    private String createPlainKey(String runAsUserId) {
        AgentApiKeyGenerator.GeneratedKey generated = keyGenerator.generate(keyGenerator.newPublicId());
        credentialRepository.save(new AgentApiCredentialEntity(
                generated.publicId(),
                "demo-org",
                "cici-system",
                "Embed test key",
                generated.keyPrefix(),
                generated.keyHash(),
                runAsUserId,
                "[]",
                "[\"embed:meeting\"]",
                30,
                3000,
                16000,
                32000,
                true,
                false,
                Instant.now().plusSeconds(3600),
                runAsUserId));
        return generated.plainKey();
    }

    private String tokenRequest(String parentOrigin) {
        return """
                {
                  "source": "cloudcc",
                  "parentOrigin": "%s",
                  "user": {
                    "externalUserId": "cloudcc-user-001",
                    "displayName": "李四"
                  },
                  "context": {
                    "objectType": "Opportunity",
                    "objectId": "006xx000001",
                    "recordName": "华东区续费商机",
                    "customerName": "某某集团",
                    "writeback": {
                      "noteObjectApiName": "Note",
                      "taskObjectApiName": "Task"
                    },
                    "fieldSuggestions": [
                      {
                        "fieldApiName": "next_step__c",
                        "label": "更新下一步动作",
                        "originalValue": "旧跟进计划",
                        "proposedValue": "下周三前发送报价方案"
                      }
                    ],
                    "participants": [
                      { "name": "张三", "role": "客户联系人" }
                    ]
                  },
                  "permissions": ["meeting:start", "meeting:summary", "crm:writeback"],
                  "ttlSeconds": 600
                }
                """.formatted(parentOrigin);
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
        JsonNode data = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        return data.path("token").asText();
    }

    private void configureCloudcc(String userId, HttpServer server) {
        jdbcTemplate.update("""
                UPDATE company_member
                SET cc_username = ?, cc_safetymark = ?
                WHERE company_id = ? AND id = ?
                """, "cloudcc-user", "cloudcc-safety", "demo-org", userId);
        integrationAppService.update("demo-org", IntegrationAppService.APP_CODE_CLOUDCC_CRM,
                true, "cloudcc", Map.of(
                        "companyId", "cloudcc-org",
                        "orgapi_switch_address", "http://localhost:%d/domain".formatted(server.getAddress().getPort()),
                        "clientId", "cloudcc-client",
                        "secretKey", "cloudcc-secret"
                ));
        cloudccAccessTokenService.invalidateSessionContext("demo-org", userId);
    }

    private HttpServer startCloudccServer(List<JsonNode> calls, boolean failTaskInsert) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/domain", exchange -> respond(exchange, """
                {"result":true,"orgapi_address":"http://localhost:%d/lightningapi"}
                """.formatted(server.getAddress().getPort())));
        server.createContext("/lightningapi/api/cauth/token", exchange -> respond(exchange, """
                {"result":true,"data":{"accessToken":"cloudcc-runtime-token"}}
                """));
        server.createContext("/lightningapi/openApi/common", exchange -> {
            JsonNode body = objectMapper.readTree(exchange.getRequestBody());
            calls.add(body);
            String serviceName = body.path("serviceName").asText();
            String objectApiName = body.path("objectApiName").asText();
            if (failTaskInsert && "insert".equals(serviceName) && "Task".equals(objectApiName)) {
                respond(exchange, """
                        {"result":false,"returnCode":"500","returnInfo":"task insert failed"}
                        """);
                return;
            }
            if ("delete".equals(serviceName)) {
                respond(exchange, """
                        {"result":true,"returnCode":"1","returnInfo":"deleted"}
                        """);
                return;
            }
            String remoteId = switch (objectApiName) {
                case "Note" -> "note-001";
                case "Task" -> "task-001";
                default -> "updated-001";
            };
            respond(exchange, """
                    {"result":true,"returnCode":"1","returnInfo":"","data":[{"id":"%s"}]}
                    """.formatted(remoteId));
        });
        server.start();
        return server;
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
