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
    void shouldKeepOrganizationScopedDataInvisibleAndImmutableAcrossTenants() throws Exception {
        CreatedOrg orgA = registerOrg("隔离测试 A");
        CreatedOrg orgB = registerOrg("隔离测试 B");
        TenantFixture fixtureB = seedTenantFixture(orgB);

        mockMvc.perform(post("/auth/switch-organization")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "orgId": "%s" }
                                """.formatted(orgB.orgId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前账号不属于该组织"));

        MvcResult profileResult = mockMvc.perform(get("/admin/organization/profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value(orgA.orgId()))
                .andReturn();
        assertThat(profileResult.getResponse().getContentAsString()).doesNotContain(orgB.orgId());

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
                .findByOrgIdAndAgentId(orgB.orgId(), fixtureB.agentId())
                .orElseThrow();
        assertThat(stored.isEnabled()).isTrue();

        mockMvc.perform(get("/agents/{agentId}", fixtureB.agentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.agentId").value(fixtureB.agentId()));
    }

    private void assertChatIsolation(CreatedOrg orgA, CreatedOrg orgB, TenantFixture fixtureB) throws Exception {
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

        assertThat(chatSessionRepository.findByIdAndOrgId(fixtureB.privateSessionId(), orgB.orgId())).isPresent();
        assertThat(chatMessageRepository.findByOrgIdAndSessionIdOrderByCreatedAtAsc(orgB.orgId(), fixtureB.privateSessionId()))
                .extracting(ChatMessageEntity::getContent)
                .contains("组织 B 会话机密");

        mockMvc.perform(get("/ai/sessions/{sessionId}/messages", fixtureB.privateSessionId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgB.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].content").value("组织 B 会话机密"));

        String sharedWorkbenchSession = "workbench:cici-system";
        chatSessionStateService.mergeUserTurn(orgA.orgId(), sharedWorkbenchSession, "cici-system", "继续上一步");
        chatSessionStateService.mergeUserTurn(orgB.orgId(), sharedWorkbenchSession, "cici-system", "添加名单，范围是组织 B 客户");

        var stateA = chatSessionStateRepository.findBySessionIdAndOrgId(sharedWorkbenchSession, orgA.orgId()).orElseThrow();
        var stateB = chatSessionStateRepository.findBySessionIdAndOrgId(sharedWorkbenchSession, orgB.orgId()).orElseThrow();
        assertThat(stateA.getOrgId()).isEqualTo(orgA.orgId());
        assertThat(stateB.getOrgId()).isEqualTo(orgB.orgId());
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
        assertThat(documentRepository.findByIdAndOrgId(fixtureB.documentId(), orgB.orgId()))
                .get()
                .extracting(KbDocumentEntity::isEnabled)
                .isEqualTo(true);

        mockMvc.perform(delete("/kb/chunks/{chunkId}", fixtureB.chunkId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());
        assertThat(chunkRepository.findByIdAndOrgId(fixtureB.chunkId(), orgB.orgId()))
                .get()
                .extracting(KbChunkEntity::isEnabled)
                .isEqualTo(true);

        mockMvc.perform(delete("/kb/{kbId}", fixtureB.kbId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(orgA.token())))
                .andExpect(status().is4xxClientError());
        assertThat(knowledgeBaseRepository.findByIdAndOrgId(fixtureB.kbId(), orgB.orgId()))
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
                org.orgId(),
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

        String sessionId = "tenant-b-session-" + suffix;
        chatSessionRepository.saveAndFlush(new ChatSessionEntity(
                sessionId,
                org.orgId(),
                org.userId(),
                agent.getAgentId(),
                "组织 B 私有会话"));
        chatMessageRepository.saveAndFlush(new ChatMessageEntity(sessionId, org.orgId(), "user", "组织 B 会话机密"));

        String traceId = "trace-" + suffix;
        Instant now = Instant.now();
        agentRunTraceRepository.saveAndFlush(new AgentRunTraceEntity(
                traceId,
                org.orgId(),
                org.userId(),
                "web:tenant-b:" + suffix,
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
                new KnowledgeBaseEntity(org.orgId(), "组织 B 私有知识库", "不可被其他组织读取"));
        KbDocumentEntity document = documentRepository.saveAndFlush(
                new KbDocumentEntity(org.orgId(), kb.getId(), "组织 B 私有文档", "text/plain", "/tmp/tenant-b-secret.txt"));
        KbChunkEntity chunk = chunkRepository.saveAndFlush(new KbChunkEntity(
                org.orgId(),
                String.valueOf(kb.getId()),
                document.getId(),
                0,
                "组织 B 私有知识片段",
                "secret",
                null,
                "tenant-b-content-hash"));

        return new TenantFixture(agent.getAgentId(), sessionId, traceId, kb.getId(), document.getId(), chunk.getId());
    }

    private CreatedOrg registerOrg(String organizationName) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "%s",
                                  "organizationName": "%s"
                                }
                                """.formatted(randomMobile(), PASSWORD, organizationName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new CreatedOrg(
                data.path("orgId").asText(),
                data.path("userId").asText(),
                data.path("token").asText());
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

    private record CreatedOrg(String orgId, String userId, String token) {
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
