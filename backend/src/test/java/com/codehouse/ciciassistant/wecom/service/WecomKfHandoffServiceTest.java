package com.codehouse.ciciassistant.wecom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.codehouse.ciciassistant.common.crypto.SecretCipherService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfAccountEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfHandoffOperationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfHandoffOperationRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class WecomKfHandoffServiceTest {

    @Test
    void shouldReserveFenceTransferAndVerifyHumanReadback() throws Exception {
        WecomKfConversationRepository conversations = mock(WecomKfConversationRepository.class);
        WecomKfHandoffOperationRepository operations = mock(WecomKfHandoffOperationRepository.class);
        WecomKfClient client = mock(WecomKfClient.class);
        TransactionTemplate transactions = immediateTransactions();
        WecomKfConversationEntity conversation = conversation();
        setId(conversation, 7L);
        AtomicReference<WecomKfHandoffOperationEntity> savedOperation = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();

        when(conversations.findByIdForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(operations.findByCompanyIdAndConversationIdAndActorUserIdAndIdempotencyKey(
                "org-1", 7L, "agent-1", "idem-1")).thenReturn(Optional.empty());
        when(operations.save(any())).thenAnswer(invocation -> {
            WecomKfHandoffOperationEntity operation = invocation.getArgument(0);
            savedOperation.set(operation);
            return operation;
        });
        when(operations.findByOperationId(any())).thenAnswer(invocation -> Optional.ofNullable(savedOperation.get()));
        when(client.listServicers(any())).thenReturn(java.util.List.of(new WecomKfClient.Servicer("agent-1", 0)));
        when(client.getServiceState(any(), any())).thenAnswer(invocation -> reads.getAndIncrement() == 0
                ? new WecomKfClient.ServiceState(1, "")
                : new WecomKfClient.ServiceState(3, "agent-1"));

        WecomKfHandoffService service = new WecomKfHandoffService(conversations, operations, client, transactions);
        WecomKfHandoffService.HandoffReceipt receipt = service.takeover(
                resolved(), 7L, "agent-1", "idem-1", "corr-1", 0, "mobile_force_takeover");

        assertThat(receipt.status()).isEqualTo(WecomKfHandoffOperationEntity.SUCCEEDED);
        assertThat(receipt.readbackState()).isEqualTo(3);
        assertThat(receipt.state().ownerMode()).isEqualTo(WecomKfConversationEntity.OWNER_HUMAN);
        assertThat(conversation.aiOwned()).isFalse();
        verify(client).transferServiceState(any(), org.mockito.ArgumentMatchers.eq("external-1"),
                org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.eq("agent-1"));
    }

    @Test
    void shouldSuppressAiSendAfterRevisionChanges() throws Exception {
        WecomKfConversationRepository conversations = mock(WecomKfConversationRepository.class);
        WecomKfConversationEntity conversation = conversation();
        setId(conversation, 7L);
        conversation.reserveHandoff("customer_request");
        when(conversations.findByIdForUpdate(7L)).thenReturn(Optional.of(conversation));
        WecomKfClient client = mock(WecomKfClient.class);
        WecomKfHandoffService service = new WecomKfHandoffService(
                conversations, mock(WecomKfHandoffOperationRepository.class), client, immediateTransactions());

        WecomKfHandoffService.AiSendReceipt receipt = service.sendAiReply(resolved(), 7L, 0, "late answer");

        assertThat(receipt.status()).isEqualTo("suppressed_owner_changed");
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void shouldFailClosedOnRevisionConflictWithoutCallingWecom() throws Exception {
        WecomKfConversationRepository conversations = mock(WecomKfConversationRepository.class);
        WecomKfHandoffOperationRepository operations = mock(WecomKfHandoffOperationRepository.class);
        WecomKfConversationEntity conversation = conversation();
        setId(conversation, 7L);
        conversation.synchronizeRemoteState(1, null, "existing_change", java.time.Instant.now());
        when(conversations.findByIdForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(operations.findByCompanyIdAndConversationIdAndActorUserIdAndIdempotencyKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(operations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WecomKfClient client = mock(WecomKfClient.class);
        WecomKfHandoffService service = new WecomKfHandoffService(conversations, operations, client, immediateTransactions());

        WecomKfHandoffService.HandoffReceipt receipt = service.takeover(
                resolved(), 7L, "agent-1", "idem-conflict", "corr-conflict", 0, "mobile_force_takeover");

        assertThat(receipt.status()).isEqualTo(WecomKfHandoffOperationEntity.CONFLICT);
        assertThat(receipt.errorCode()).isEqualTo("REVISION_CONFLICT");
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void shouldReplayCompletedIdempotentOperationWithoutRemoteMutation() throws Exception {
        WecomKfConversationRepository conversations = mock(WecomKfConversationRepository.class);
        WecomKfHandoffOperationRepository operations = mock(WecomKfHandoffOperationRepository.class);
        WecomKfConversationEntity conversation = conversation();
        setId(conversation, 7L);
        WecomKfHandoffOperationEntity prior = new WecomKfHandoffOperationEntity(
                "org-1", 7L, "agent-1", "idem-replay", "corr-replay", 0, 0, 3, "mobile_force_takeover");
        prior.succeed(3, 2);
        when(conversations.findByIdForUpdate(7L)).thenReturn(Optional.of(conversation));
        when(operations.findByCompanyIdAndConversationIdAndActorUserIdAndIdempotencyKey(
                "org-1", 7L, "agent-1", "idem-replay")).thenReturn(Optional.of(prior));
        WecomKfClient client = mock(WecomKfClient.class);
        WecomKfHandoffService service = new WecomKfHandoffService(conversations, operations, client, immediateTransactions());

        WecomKfHandoffService.HandoffReceipt receipt = service.takeover(
                resolved(), 7L, "agent-1", "idem-replay", "different-correlation-is-ignored", 0, "mobile_force_takeover");

        assertThat(receipt.status()).isEqualTo(WecomKfHandoffOperationEntity.SUCCEEDED);
        assertThat(receipt.operationId()).isEqualTo(prior.getOperationId());
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    void shouldRejectConversationOutsideAccountScope() throws Exception {
        WecomKfConversationRepository conversations = mock(WecomKfConversationRepository.class);
        WecomKfConversationEntity conversation = new WecomKfConversationEntity(
                "other-org", "ww-demo", "wk-demo", "external-1", "session-1", "after-sales-agent", "user-1");
        setId(conversation, 7L);
        when(conversations.findByIdForUpdate(7L)).thenReturn(Optional.of(conversation));
        WecomKfClient client = mock(WecomKfClient.class);
        WecomKfHandoffService service = new WecomKfHandoffService(
                conversations, mock(WecomKfHandoffOperationRepository.class), client, immediateTransactions());

        assertThatThrownBy(() -> service.takeover(
                resolved(), 7L, "agent-1", "idem-scope", "corr-scope", 0, "mobile_force_takeover"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private TransactionTemplate immediateTransactions() {
        TransactionTemplate template = mock(TransactionTemplate.class);
        when(template.execute(any())).thenAnswer(invocation -> {
            TransactionCallback callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        return template;
    }

    private WecomKfConversationEntity conversation() {
        return new WecomKfConversationEntity("org-1", "ww-demo", "wk-demo", "external-1",
                "session-1", "after-sales-agent", "user-1");
    }

    private WecomKfConfigService.ResolvedAccount resolved() {
        SecretCipherService cipher = new SecretCipherService("");
        SecretCipherService.EncryptedSecret secret = cipher.encryptUtf8("kf-secret");
        SecretCipherService.EncryptedSecret aes = cipher.encryptUtf8("abcdefghijklmnopqrstuvwxyzABCDEFG1234567890");
        WecomKfAccountEntity account = new WecomKfAccountEntity("org-1", "ww-demo", "wk-demo", "售后客服",
                secret.cipherBase64(), secret.ivBase64(), "token", aes.cipherBase64(), aes.ivBase64(),
                "after-sales-agent", "user-1");
        return new WecomKfConfigService.ResolvedAccount(account, "kf-secret", "aes", "app-secret");
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
