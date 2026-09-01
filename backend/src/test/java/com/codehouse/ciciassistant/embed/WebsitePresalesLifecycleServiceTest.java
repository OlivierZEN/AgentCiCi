package com.codehouse.ciciassistant.embed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.domain.ChatMessageEntity;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.embed.config.WebsitePresalesProperties;
import com.codehouse.ciciassistant.embed.domain.WebsitePresalesLeadEntity;
import com.codehouse.ciciassistant.embed.domain.WebsitePresalesLeadRepository;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitSessionEntity;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitSessionRepository;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitorProfileEntity;
import com.codehouse.ciciassistant.embed.domain.WebsiteVisitorProfileRepository;
import com.codehouse.ciciassistant.embed.service.WebsitePresalesLifecycleService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebsitePresalesLifecycleServiceTest {

    private static final String COMPANY = "org-demo";
    private static final String USER = "run-member";
    private static final String AGENT = "sales-agent";
    private static final String CHAT = "11111111-1111-4111-8111-111111111111";

    private WebsiteVisitorProfileRepository profiles;
    private WebsiteVisitSessionRepository visits;
    private WebsitePresalesLeadRepository leads;
    private ChatSessionRepository chatSessions;
    private ChatMessageRepository messages;
    private WebsiteVisitorProfileEntity profile;
    private WebsiteVisitSessionEntity visit;
    private WebsitePresalesLifecycleService service;

    @BeforeEach
    void setUp() {
        profiles = mock(WebsiteVisitorProfileRepository.class);
        visits = mock(WebsiteVisitSessionRepository.class);
        leads = mock(WebsitePresalesLeadRepository.class);
        chatSessions = mock(ChatSessionRepository.class);
        messages = mock(ChatMessageRepository.class);
        WebsitePresalesProperties properties = new WebsitePresalesProperties();
        service = new WebsitePresalesLifecycleService(
                profiles, visits, leads, chatSessions, messages, new SecretCipherService(""), properties);
        profile = new WebsiteVisitorProfileEntity(COMPANY, AGENT, "tenant-demo", "visitor-demo");
        visit = new WebsiteVisitSessionEntity(profile.getId(), COMPANY, AGENT, CHAT, "visit-1", "", false);
        when(visits.findByCompanyIdAndChatSessionId(COMPANY, CHAT)).thenReturn(Optional.of(visit));
        when(profiles.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(chatSessions.findByIdAndCompanyIdAndUserId(CHAT, COMPANY, USER)).thenReturn(Optional.of(
                new ChatSessionEntity(CHAT, COMPANY, USER, AGENT, "新会话", "sisi_embed", "USER", "route")));
    }

    @Test
    void redirectsAfterSalesBeforeAnyModelOrToolCanRun() {
        WebsitePresalesLifecycleService.TurnDecision decision = service.beforeTurn(
                COMPANY, USER, CHAT, "我们已经购买，现在账号无法登录并且一直报错");

        assertThat(decision.direct()).isTrue();
        assertThat(decision.answer()).contains("登录 CloudCC 系统").contains("在线工单");
        assertThat(decision.lifecycle()).containsEntry("status", WebsiteVisitSessionEntity.SERVICE_REDIRECTED);
        verify(messages, times(2)).save(any(ChatMessageEntity.class));
        verify(leads, never()).save(any(WebsitePresalesLeadEntity.class));
    }

    @Test
    void capturesContactEncryptedAndClosesWithoutEchoingIt() {
        WebsitePresalesLifecycleService.TurnDecision decision = service.beforeTurn(
                COMPANY, USER, CHAT, "可以联系我，手机号 13800138000");

        assertThat(decision.direct()).isTrue();
        assertThat(decision.answer()).doesNotContain("13800138000");
        assertThat(decision.lifecycle())
                .containsEntry("status", WebsiteVisitSessionEntity.COMPLETED)
                .containsEntry("contactCaptured", true)
                .containsEntry("canSend", false);
        verify(leads).save(any(WebsitePresalesLeadEntity.class));
        ArgumentCaptor<ChatMessageEntity> savedMessages = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(messages, times(2)).save(savedMessages.capture());
        assertThat(savedMessages.getAllValues())
                .extracting(ChatMessageEntity::getContent)
                .noneMatch(content -> content.contains("13800138000"));
    }

    @Test
    void asksForContactAtSixthTurnAndClosesAtEighth() {
        WebsitePresalesLifecycleService.TurnDecision sixth = null;
        for (int index = 1; index <= 6; index++) {
            sixth = service.beforeTurn(COMPANY, USER, CHAT, "想了解产品能力 " + index);
        }
        assertThat(sixth).isNotNull();
        assertThat(sixth.direct()).isTrue();
        assertThat(sixth.lifecycle()).containsEntry("status", WebsiteVisitSessionEntity.CONTACT_REQUESTED);

        service.beforeTurn(COMPANY, USER, CHAT, "我再考虑一下");
        WebsitePresalesLifecycleService.TurnDecision eighth = service.beforeTurn(COMPANY, USER, CHAT, "还有一个问题");
        assertThat(eighth.direct()).isTrue();
        assertThat(eighth.lifecycle()).containsEntry("status", WebsiteVisitSessionEntity.COMPLETED);
    }

    @Test
    void startNewChoiceDropsInheritedSummary() {
        WebsiteVisitSessionEntity returningVisit = new WebsiteVisitSessionEntity(
                profile.getId(), COMPANY, AGENT, CHAT, "visit-2", "上次咨询主题：销售云", true);
        when(visits.findByCompanyIdAndChatSessionId(COMPANY, CHAT)).thenReturn(Optional.of(returningVisit));

        var lifecycle = service.choose(COMPANY, CHAT, "START_NEW");

        assertThat(lifecycle)
                .containsEntry("status", WebsiteVisitSessionEntity.ACTIVE)
                .containsEntry("priorSummary", "")
                .containsEntry("resumeChoiceRequired", false);
    }
}
