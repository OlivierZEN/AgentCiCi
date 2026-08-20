package com.codehouse.ciciassistant.wecom.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WecomKfConversationServiceTest {

    private WecomKfConversationRepository conversations;
    private WecomKfMessageRepository messages;
    private ChatOrchestratorService orchestrator;
    private WecomKfHandoffService handoffs;
    private WecomKfConversationService service;

    @BeforeEach
    void setUp() {
        conversations = mock(WecomKfConversationRepository.class);
        messages = mock(WecomKfMessageRepository.class);
        orchestrator = mock(ChatOrchestratorService.class);
        handoffs = mock(WecomKfHandoffService.class);
        service = new WecomKfConversationService(conversations, messages, mock(AgentRunTraceRepository.class), orchestrator, handoffs);
        when(messages.existsByCompanyIdAndMsgId(any(), any())).thenReturn(false);
        when(conversations.findByCompanyIdAndCorpIdAndOpenKfIdAndExternalUserId(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(conversations.save(any())).thenAnswer(invocation -> {
            WecomKfConversationEntity conversation = invocation.getArgument(0);
            if (conversation.getId() == null) setId(conversation, 7L);
            return conversation;
        });
    }

    @Test
    void shouldRecordHumanOriginWithoutInvokingAgent() {
        service.acceptCustomerMessage(resolved(), new WecomKfClient.SyncedMessage(
                "human-1", "wk-demo", "external-1", "text", "人工已回复", 1,
                5, "agent-1", "", 0, -1, "", ""));

        verify(orchestrator, never()).chat(anyString(), anyString(), anyString(), anyString(), anyList(),
                anyString(), nullable(String.class), anyMap(), anyString());
        verify(handoffs).refreshState(any(), anyLong(), org.mockito.ArgumentMatchers.eq("human_message"));
    }

    @Test
    void shouldQueueDeterministicCustomerRequestWithoutCallingModel() {
        when(handoffs.refreshState(any(), anyLong(), any())).thenReturn(new WecomKfHandoffService.ConversationState(
                UUID.randomUUID(), 1, WecomKfConversationEntity.OWNER_AI, null, 4, Instant.now()));

        service.acceptCustomerMessage(resolved(), new WecomKfClient.SyncedMessage(
                "customer-1", "wk-demo", "external-1", "text", "请帮我转人工客服", 1));

        verify(handoffs).queueForHuman(any(), org.mockito.ArgumentMatchers.eq(7L), any(),
                org.mockito.ArgumentMatchers.eq("customer:customer-1:queue"),
                org.mockito.ArgumentMatchers.eq("customer-1"), org.mockito.ArgumentMatchers.eq(4L),
                org.mockito.ArgumentMatchers.eq("customer_request"));
        verify(orchestrator, never()).chat(anyString(), anyString(), anyString(), anyString(), anyList(),
                anyString(), nullable(String.class), anyMap(), anyString());
    }

    @Test
    void shouldPassCapturedRevisionToFinalAiSendFence() {
        when(handoffs.refreshState(any(), anyLong(), any())).thenReturn(new WecomKfHandoffService.ConversationState(
                UUID.randomUUID(), 1, WecomKfConversationEntity.OWNER_AI, null, 9, Instant.now()));
        when(orchestrator.chat(anyString(), anyString(), anyString(), anyString(), anyList(),
                anyString(), nullable(String.class), anyMap(), anyString()))
                .thenReturn(Map.of("answer", "已收到"));
        when(handoffs.sendAiReply(any(), anyLong(), anyLong(), any())).thenReturn(
                new WecomKfHandoffService.AiSendReceipt("sent", "remote-1", null));

        service.acceptCustomerMessage(resolved(), new WecomKfClient.SyncedMessage(
                "customer-2", "wk-demo", "external-1", "text", "订单还没收到", 1));

        verify(handoffs).sendAiReply(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(9L), org.mockito.ArgumentMatchers.eq("已收到"));
    }

    private WecomKfConfigService.ResolvedAccount resolved() {
        SecretCipherService cipher = new SecretCipherService("");
        var secret = cipher.encryptUtf8("kf-secret");
        var aes = cipher.encryptUtf8("abcdefghijklmnopqrstuvwxyzABCDEFG1234567890");
        WecomKfAccountEntity account = new WecomKfAccountEntity("org-1", "ww-demo", "wk-demo", "售后客服",
                secret.cipherBase64(), secret.ivBase64(), "token", aes.cipherBase64(), aes.ivBase64(),
                "after-sales-agent", "user-1");
        return new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "aes");
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
