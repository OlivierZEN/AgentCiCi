package com.codehouse.ciciassistant.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.codehouse.ciciassistant.ai.service.ChatSessionStateService;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
class MultitenantIsolationIntegrationTest {

    private static final String PASSWORD = "szyd1234";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AgentDefinitionRepository agentDefinitionRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private ChatSessionStateService chatSessionStateService;

    @Autowired
    private ChatSessionStateRepository chatSessionStateRepository;

    @Autowired
    private AgentRunTraceRepository agentRunTraceRepository;

    @Autowired
    private KnowledgeBaseRepository knowledgeBaseRepository;

    @Autowired
    private KbDocumentRepository documentRepository;

    @Autowired
    private KbChunkRepository chunkRepository;

    @Test
    void shouldKeepCompanyScopedDataInvisibleAndImmutableAcrossTenants() throws Exception {
        CreatedOrg orgA = registerOrg("隔离测试 A");
        CreatedOrg orgB = registerOrg("隔离测试 B");
        TenantFixture fixtureB = seedTenantFixture(orgB);

        mockMvc.perform(post("/auth/switch-company")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "companyId": "%s" }
                                """.formatted(orgB.companyId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前账号不属于该组织"));

        MvcResult profileResult = mockMvc.perform(get("/admin/company/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(orgA.companyId()))
                .andReturn();
        assertThat(profileResult.getResponse().getContentAsString()).doesNotContain(orgB.companyId());

        assertAgentIsolation(orgA, orgB, fixtureB);
        assertChatIsolation(orgA, orgB, fixtureB);
        assertRunLogIsolation(orgA, orgB, fixtureB);
        assertKnowledgeBaseIsolation(orgA, orgB, fixtureB);
        assertPlatformBoundary(orgA);
    }

    private void assertAgentIsolation(CreatedOrg orgA, CreatedOrg orgB, TenantFixture fixtureB) throws Exception {
        MvcResult listForA = mockMvc.perform(get("/agents")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(listForA.getResponse().getContentAsString())
                .doesNotContain(fixtureB.agentId())
                .doesNotContain("组织 B 私有 Agent");

        mockMvc.perform(get("/agents/{agentId}", fixtureB.agentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/agents/{agentId}", fixtureB.agentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isNotFound());

        AgentDefinitionEntity stored = agentDefinitionRepository
                .findByCompanyIdAndAgentId(orgB.companyId(), fixtureB.agentId())
                .orElseThrow();
        assertThat(stored.isEnabled()).isTrue();

        mockMvc.perform(get("/agents/{agentId}", fixtureB.agentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(fixtureB.agentId()));
    }

    private void assertChatIsolation(CreatedOrg orgA, CreatedOrg orgB, TenantFixture fixtureB) throws Exception {
        String sessionA = createSession(orgA, "cici-system");
        String sessionB = createSession(orgB, "cici-system");
        assertThat(sessionA).isNotEqualTo(sessionB);
        assertThat(UUID.fromString(sessionA)).isNotNull();
        assertThat(UUID.fromString(sessionB)).isNotNull();

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "%s",
                                  "question": "不得写入另一租户的会话"
                                }
                                """.formatted(sessionB)))
                .andExpect(status().isNotFound());

        MvcResult sessionsForA = mockMvc.perform(get("/ai/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(sessionsForA.getResponse().getContentAsString())
                .doesNotContain(fixtureB.privateSessionId())
                .doesNotContain("组织 B 会话机密");

        mockMvc.perform(get("/ai/sessions/{sessionId}/messages", fixtureB.privateSessionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/ai/sessions/{sessionId}", fixtureB.privateSessionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isNotFound());

        assertThat(chatSessionRepository.findByIdAndCompanyId(fixtureB.privateSessionId(), orgB.companyId())).isPresent();
        assertThat(chatMessageRepository.findByCompanyIdAndSessionIdOrderByCreatedAtAsc(orgB.companyId(), fixtureB.privateSessionId()))
                .extracting(ChatMessageEntity::getContent)
                .contains("组织 B 会话机密");

        mockMvc.perform(get("/ai/sessions/{sessionId}/messages", fixtureB.privateSessionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("组织 B 会话机密"));

        chatSessionStateService.mergeUserTurn(orgA.companyId(), sessionA, "cici-system", "继续上一步");
        chatSessionStateService.mergeUserTurn(orgB.companyId(), sessionB, "cici-system", "添加名单，范围是组织 B 客户");

        var stateA = chatSessionStateRepository.findBySessionIdAndCompanyId(sessionA, orgA.companyId()).orElseThrow();
        var stateB = chatSessionStateRepository.findBySessionIdAndCompanyId(sessionB, orgB.companyId()).orElseThrow();
        assertThat(stateA.getCompanyId()).isEqualTo(orgA.companyId());
        assertThat(stateB.getCompanyId()).isEqualTo(orgB.companyId());
        assertThat(stateA.getStateJson()).contains("continue_current_plan").doesNotContain("add_members");
        assertThat(stateB.getStateJson()).contains("add_members").contains("组织 B 客户").doesNotContain("continue_current_plan");
    }

    private void assertRunLogIsolation(CreatedOrg orgA, CreatedOrg orgB, TenantFixture fixtureB) throws Exception {
        MvcResult logsForA = mockMvc.perform(get("/me/agents/run-logs")
                        .queryParam("q", "组织 B 运行轨迹")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(logsForA.getResponse().getContentAsString())
                .doesNotContain(fixtureB.traceId())
                .doesNotContain("组织 B 运行轨迹");

        mockMvc.perform(get("/me/agents/run-logs/{traceId}", fixtureB.traceId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/me/agents/run-logs/{traceId}", fixtureB.traceId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(fixtureB.traceId()))
                .andExpect(jsonPath("$.data.summary").value("组织 B 运行轨迹"));
    }

    private void assertKnowledgeBaseIsolation(CreatedOrg orgA, CreatedOrg orgB, TenantFixture fixtureB) throws Exception {
        MvcResult kbListForA = mockMvc.perform(get("/kb")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(kbListForA.getResponse().getContentAsString())
                .doesNotContain(String.valueOf(fixtureB.kbId()))
                .doesNotContain("组织 B 私有知识库");

        MvcResult crossDocuments = mockMvc.perform(get("/kb/{kbId}/documents", fixtureB.kbId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(crossDocuments.getResponse().getContentAsString()).doesNotContain("组织 B 私有文档");

        mockMvc.perform(get("/kb/documents/{documentId}/chunks", fixtureB.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/kb/documents/{documentId}/disable", fixtureB.documentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());
        assertThat(documentRepository.findByIdAndCompanyId(fixtureB.documentId(), orgB.companyId()))
                .get()
                .extracting(KbDocumentEntity::isEnabled)
                .isEqualTo(true);

        mockMvc.perform(delete("/kb/chunks/{chunkId}", fixtureB.chunkId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());
        assertThat(chunkRepository.findByIdAndCompanyId(fixtureB.chunkId(), orgB.companyId()))
                .get()
                .extracting(KbChunkEntity::isEnabled)
                .isEqualTo(true);

        mockMvc.perform(delete("/kb/{kbId}", fixtureB.kbId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());
        assertThat(knowledgeBaseRepository.findByIdAndCompanyId(fixtureB.kbId(), orgB.companyId()))
                .get()
                .extracting(KnowledgeBaseEntity::getStatus)
                .isNotEqualTo("DELETED");

        mockMvc.perform(get("/kb/{kbId}/documents", fixtureB.kbId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("组织 B 私有文档"));
    }

    private void assertPlatformBoundary(CreatedOrg orgA) throws Exception {
        mockMvc.perform(get("/platform/skills")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isForbidden());

        String platformToken = platformToken();
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, bearer(platformToken)))
                .andExpect(status().isForbidden());
    }

    private TenantFixture seedTenantFixture(CreatedOrg org) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String agentId = "tenant-b-agent-" + suffix;
        AgentDefinitionEntity agent = agentDefinitionRepository.saveAndFlush(new AgentDefinitionEntity(
                org.companyId(),
                agentId,
                "组织 B 私有 Agent",
                "组织 B 私有摘要",
                "",
                "qwen3.6-plus",
                "system",
                "",
                "BALANCED",
                "COPILOT",
                "v1",
                null,
                org.userId(),
                false,
                true));

        String sessionId = UUID.randomUUID().toString();
        chatSessionRepository.saveAndFlush(new ChatSessionEntity(
                sessionId,
                org.companyId(),
                org.userId(),
                agent.getAgentId(),
                "组织 B 私有会话"));
        chatMessageRepository.saveAndFlush(new ChatMessageEntity(sessionId, org.companyId(), "user", "组织 B 会话机密"));

        String traceId = "trace-" + suffix;
        Instant now = Instant.now();
        agentRunTraceRepository.saveAndFlush(new AgentRunTraceEntity(
                traceId,
                org.companyId(),
                org.userId(),
                sessionId,
                agent.getAgentId(),
                "web",
                "SUCCESS",
                "组织 B 运行标题",
                "组织 B 运行轨迹",
                "qwen3.6-plus",
                "",
                now.minusSeconds(5),
                now,
                500,
                1,
                0,
                0,
                "[]",
                "[]",
                "[]",
                "{}",
                now));

        KnowledgeBaseEntity kb = knowledgeBaseRepository.saveAndFlush(
                new KnowledgeBaseEntity(org.companyId(), "组织 B 私有知识库", "不可被其他组织读取"));
        KbDocumentEntity document = documentRepository.saveAndFlush(
                new KbDocumentEntity(org.companyId(), kb.getId(), "组织 B 私有文档", "text/plain", "/tmp/tenant-b-secret.txt"));
        KbChunkEntity chunk = chunkRepository.saveAndFlush(new KbChunkEntity(
                org.companyId(),
                String.valueOf(kb.getId()),
                document.getId(),
                0,
                "组织 B 私有知识片段",
                "secret",
                null,
                "tenant-b-content-hash"));

        return new TenantFixture(agent.getAgentId(), sessionId, traceId, kb.getId(), document.getId(), chunk.getId());
    }

    private CreatedOrg registerOrg(String companyName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "%s",
                                  "companyName": "%s"
                                }
                                """.formatted(randomMobile(), PASSWORD, companyName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new CreatedOrg(
                data.path("companyId").asText(),
                data.path("userId").asText(),
                data.path("token").asText());
    }

    private String createSession(CreatedOrg org, String agentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/ai/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(org.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":\"%s\"}".formatted(agentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channel").value("web"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String platformToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "%s"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String randomMobile() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        return "139" + String.format("%08d", suffix);
    }

    private record CreatedOrg(String companyId, String userId, String token) {
    }

    private record TenantFixture(
            String agentId,
            String privateSessionId,
            String traceId,
            Long kbId,
            Long documentId,
            Long chunkId) {
    }
}
