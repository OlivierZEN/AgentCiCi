package com.codehouse.ciciassistant.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.agent.domain.AgentChannelBindingRepository;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionEntity;
import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.agent.domain.AgentPermission;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigEntity;
import com.codehouse.ciciassistant.agent.domain.AgentPublishConfigRepository;
import com.codehouse.ciciassistant.agent.service.AgentAccessControlService;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
import com.codehouse.ciciassistant.auth.service.JwtService;
import com.codehouse.ciciassistant.embed.domain.EmbedAppDefinitionEntity;
import com.codehouse.ciciassistant.embed.service.EmbedAppService;
import com.codehouse.ciciassistant.embed.service.PublicWebWidgetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PublicWebWidgetServiceTest {

    private static final String WIDGET_KEY = "ww_1234567890abcdef12345678";
    private static final String COMPANY_ID = "org-demo";
    private static final String AGENT_ID = "sales-agent";
    private static final String USER_ID = "member-1";
    private static final String ORIGIN = "https://portal.example.test";
    private static final String AGENT_AVATAR = "data:image/webp;base64,d2ViLXdpZGdldC1hdmF0YXI=";

    private AgentPublishConfigRepository configs;
    private AgentDefinitionRepository agents;
    private AgentChannelBindingRepository channels;
    private UserRepository users;
    private AgentAccessControlService access;
    private JwtService jwt;
    private EmbedAppService embedApps;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private PublicWebWidgetService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        configs = mock(AgentPublishConfigRepository.class);
        agents = mock(AgentDefinitionRepository.class);
        channels = mock(AgentChannelBindingRepository.class);
        users = mock(UserRepository.class);
        access = mock(AgentAccessControlService.class);
        jwt = mock(JwtService.class);
        embedApps = mock(EmbedAppService.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.opsForValue()).thenReturn(values);
        when(redis.expire(any(String.class), any(Duration.class))).thenReturn(true);
        EmbedAppDefinitionEntity definition = mock(EmbedAppDefinitionEntity.class);
        when(definition.getStatus()).thenReturn(EmbedAppDefinitionEntity.STATUS_ENABLED);
        when(embedApps.requireDefinition("sisi")).thenReturn(definition);
        when(embedApps.supportedSources(definition)).thenReturn(List.of("cloudcc", "website"));
        service = new PublicWebWidgetService(configs, agents, channels, users, access, jwt, embedApps,
                new ObjectMapper(), provider);
        stubReadyWidget(true, true, true);
    }

    @Test
    void exposesOnlyPublicConfigAndIssuesBoundShortLivedChatToken() {
        when(embedApps.normalizeOrigin(ORIGIN)).thenReturn(ORIGIN);
        when(embedApps.originAllowed(List.of(ORIGIN), ORIGIN)).thenReturn(true);
        when(values.increment(any(String.class))).thenReturn(1L);
        when(jwt.issueToken(any(String.class), any(), eq(600L))).thenReturn("signed-widget-token");
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn(ORIGIN);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.10");

        assertThat(service.publicConfig(WIDGET_KEY))
                .containsEntry("assistantName", "售前跟进智能体")
                .containsEntry("agentAvatarBase64", AGENT_AVATAR)
                .containsEntry("sdkUrl", "/sdk/sisi@1.1.0.js")
                .doesNotContainKeys("companyId", "runAsUserId");

        PublicWebWidgetService.TokenView token = service.issueToken(WIDGET_KEY,
                new PublicWebWidgetService.TokenCommand(
                        "11111111-1111-4111-8111-111111111111",
                        "22222222-2222-4222-8222-222222222222", ORIGIN, "/global/solutions", "zh-CN"),
                request);

        assertThat(token.embedToken()).isEqualTo("signed-widget-token");
        assertThat(token.permissions()).containsExactly("chat:read", "chat:write");
        assertThat(token.ttlSeconds()).isEqualTo(600);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> claims = ArgumentCaptor.forClass(Map.class);
        verify(jwt).issueToken(any(String.class), claims.capture(), eq(600L));
        assertThat(claims.getValue()).doesNotContainKey("agentAvatarBase64");
        assertThat((Map<String, Object>) claims.getValue().get("context"))
                .containsEntry("visitId", "22222222-2222-4222-8222-222222222222")
                .doesNotContainKey("agentAvatarBase64");
    }

    @Test
    void hidesWidgetWhenAgentIsNotPublishedOrWebChannelIsDisabled() {
        stubReadyWidget(false, true, true);
        assertNotFound();
        stubReadyWidget(true, false, true);
        assertNotFound();
    }

    @Test
    void hidesWidgetWhenRunAsIdentityIsInactiveOrCannotRunAgent() {
        stubReadyWidget(true, true, false);
        assertNotFound();

        stubReadyWidget(true, true, true);
        UserEntity inactive = mock(UserEntity.class);
        when(inactive.getId()).thenReturn(USER_ID);
        when(inactive.getMemberStatus()).thenReturn("PENDING_ACTIVATION");
        when(inactive.getRoleCode()).thenReturn("ORG_USER");
        when(users.findByIdAndCompany_Id(USER_ID, COMPANY_ID)).thenReturn(Optional.of(inactive));
        assertNotFound();
    }

    @Test
    void rejectsDuplicateWidgetKeyAndUnavailableRateLimiter() {
        AgentPublishConfigEntity config = webConfig();
        when(configs.findByChannelIdOrderByUpdatedAtDesc("web")).thenReturn(List.of(config, config));
        assertNotFound();

        stubReadyWidget(true, true, true);
        when(embedApps.normalizeOrigin(ORIGIN)).thenReturn(ORIGIN);
        when(embedApps.originAllowed(List.of(ORIGIN), ORIGIN)).thenReturn(true);
        when(values.increment(any(String.class))).thenThrow(new IllegalStateException("redis unavailable"));
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Origin")).thenReturn(ORIGIN);
        when(request.getRemoteAddr()).thenReturn("203.0.113.12");
        assertThatThrownBy(() -> service.issueToken(WIDGET_KEY, command(ORIGIN), request))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void rejectsOriginMismatchAndRateLimitOverflow() {
        when(embedApps.normalizeOrigin(ORIGIN)).thenReturn(ORIGIN);
        when(embedApps.normalizeOrigin("https://wrong.example.test")).thenReturn("https://wrong.example.test");
        when(embedApps.originAllowed(List.of(ORIGIN), ORIGIN)).thenReturn(true);
        HttpServletRequest wrongOrigin = mock(HttpServletRequest.class);
        when(wrongOrigin.getHeader("Origin")).thenReturn("https://wrong.example.test");
        assertThatThrownBy(() -> service.issueToken(WIDGET_KEY,
                command(ORIGIN), wrongOrigin))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        HttpServletRequest allowed = mock(HttpServletRequest.class);
        when(allowed.getHeader("Origin")).thenReturn(ORIGIN);
        when(allowed.getRemoteAddr()).thenReturn("203.0.113.11");
        when(values.increment(any(String.class))).thenReturn(21L);
        assertThatThrownBy(() -> service.issueToken(WIDGET_KEY, command(ORIGIN), allowed))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    private PublicWebWidgetService.TokenCommand command(String origin) {
        return new PublicWebWidgetService.TokenCommand(
                "11111111-1111-4111-8111-111111111111", origin, "/", "zh-CN");
    }

    private void assertNotFound() {
        assertThatThrownBy(() -> service.publicConfig(WIDGET_KEY))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private void stubReadyWidget(boolean published, boolean webEnabled, boolean canRun) {
        AgentPublishConfigEntity config = webConfig();
        AgentDefinitionEntity agent = new AgentDefinitionEntity(
                COMPANY_ID, AGENT_ID, "售前跟进 Agent", "summary", "hello", "model", "prompt", "handoff",
                "BALANCED", "COPILOT", "v1", AGENT_AVATAR, false, true);
        if (published) agent.setPublishedVersionId(1L);
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(USER_ID);
        when(user.getMemberStatus()).thenReturn(UserEntity.STATUS_ACTIVE);
        when(user.getRoleCode()).thenReturn("ORG_USER");
        when(configs.findByChannelIdOrderByUpdatedAtDesc("web")).thenReturn(List.of(config));
        when(agents.findByCompanyIdAndAgentIdAndEnabledTrue(COMPANY_ID, AGENT_ID)).thenReturn(Optional.of(agent));
        when(channels.existsByCompanyIdAndAgentIdAndChannelIdAndEnabledTrue(COMPANY_ID, AGENT_ID, "web"))
                .thenReturn(webEnabled);
        when(users.findByIdAndCompany_Id(USER_ID, COMPANY_ID)).thenReturn(Optional.of(user));
        when(access.can(eq(COMPANY_ID), eq(USER_ID), anyList(), eq(AGENT_ID), eq(AgentPermission.RUN)))
                .thenReturn(canRun);
        when(embedApps.normalizeOrigin(ORIGIN)).thenReturn(ORIGIN);
    }

    private AgentPublishConfigEntity webConfig() {
        return new AgentPublishConfigEntity(COMPANY_ID, AGENT_ID, "web", """
                {
                  "enabled": true,
                  "widgetKey": "ww_1234567890abcdef12345678",
                  "allowedOrigins": ["https://portal.example.test"],
                  "runAsUserId": "member-1",
                  "assistantName": "售前跟进智能体",
                  "launcherLabel": "咨询售前",
                  "welcomeMessage": "你好，请告诉我你的业务场景。",
                  "defaultOpen": false,
                  "tokenTtlSeconds": 600,
                  "rateLimitPerMinute": 20
                }
                """);
    }
}
