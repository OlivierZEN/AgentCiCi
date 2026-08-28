package com.codehouse.ciciassistant.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.ai.service.ChatAttachmentService;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.embed.domain.SisiEmbedSessionEntity;
import com.codehouse.ciciassistant.embed.domain.SisiEmbedSessionRepository;
import com.codehouse.ciciassistant.embed.service.EmbedTokenService;
import com.codehouse.ciciassistant.embed.service.SisiEmbedRuntimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SisiEmbedRuntimeServiceTest {

    private static final String COMPANY_ID = "org-demo";
    private static final String USER_ID = "member-1";
    private static final String AGENT_ID = "sales-agent";
    private static final String AVATAR = "data:image/webp;base64,c2FsZXMtYWdlbnQ=";

    private SisiEmbedSessionRepository sessions;
    private AgentDefinitionRepository agents;
    private SisiEmbedRuntimeService service;

    @BeforeEach
    void setUp() {
        sessions = mock(SisiEmbedSessionRepository.class);
        agents = mock(AgentDefinitionRepository.class);
        service = new SisiEmbedRuntimeService(
                sessions,
                mock(ChatOrchestratorService.class),
                mock(ChatAttachmentService.class),
                mock(UserRepository.class),
                agents,
                new ObjectMapper());
    }

    @Test
    void exposesTheServerResolvedAgentAvatarForWebsiteSessions() {
        stubExistingSession("website");
        AgentDefinitionEntity agent = agent(AVATAR);
        when(agents.findByCompanyIdAndAgentIdAndEnabledTrue(COMPANY_ID, AGENT_ID)).thenReturn(Optional.of(agent));

        Map<String, Object> view = service.createSession(token("website"));

        assertThat(view)
                .containsEntry("productName", "售前跟进智能体")
                .containsEntry("agentAvatarBase64", AVATAR);
    }

    @Test
    void keepsTrustedPageEmbedIdentityIndependentFromWebsiteAgentAvatar() {
        stubExistingSession("cloudcc");

        Map<String, Object> view = service.createSession(token("cloudcc"));

        assertThat(view).containsEntry("agentAvatarBase64", "");
        verifyNoInteractions(agents);
    }

    private void stubExistingSession(String source) {
        SisiEmbedSessionEntity session = new SisiEmbedSessionEntity(
                "session-1", COMPANY_ID, "session-1", USER_ID, AGENT_ID,
                "tenant-1", "visitor-1", source, "WebsitePage", "/", "", "",
                "https://portal.example.test", "{}");
        when(sessions.findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserIdAndSourceAndObjectTypeAndObjectId(
                COMPANY_ID, AGENT_ID, "tenant-1", "visitor-1", source, "WebsitePage", "/"))
                .thenReturn(Optional.of(session));
    }

    private EmbedTokenService.AuthenticatedEmbedToken token(String source) {
        return new EmbedTokenService.AuthenticatedEmbedToken(
                "sisi", COMPANY_ID, USER_ID, AGENT_ID, "tenant-1", "visitor-1", source,
                "WebsitePage", "/", "", "", "https://portal.example.test",
                List.of("chat:read"), "nonce-1", Map.of("assistantName", "售前跟进智能体"));
    }

    private AgentDefinitionEntity agent(String avatar) {
        AgentDefinitionEntity agent = new AgentDefinitionEntity(
                COMPANY_ID, AGENT_ID, "客服-Mary", "summary", "hello", "model", "prompt", "handoff",
                "BALANCED", "COPILOT", "v1", avatar, false, true);
        agent.setPublishedVersionId(1L);
        return agent;
    }
}
