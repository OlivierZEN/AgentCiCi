package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfHandoffOperationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfHandoffOperationRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WecomKfHandoffService {

    public static final int STATE_UNHANDLED = 0;
    public static final int STATE_AI = 1;
    public static final int STATE_PENDING = 2;
    public static final int STATE_HUMAN = 3;
    public static final int STATE_ENDED = 4;

    private final WecomKfConversationRepository conversationRepository;
    private final WecomKfHandoffOperationRepository operationRepository;
    private final WecomKfClient client;
    private final TransactionTemplate transactionTemplate;

    public WecomKfHandoffService(WecomKfConversationRepository conversationRepository,
                                 WecomKfHandoffOperationRepository operationRepository,
                                 WecomKfClient client,
                                 TransactionTemplate transactionTemplate) {
        this.conversationRepository = conversationRepository;
        this.operationRepository = operationRepository;
        this.client = client;
        this.transactionTemplate = transactionTemplate;
    }

    public ConversationState refreshState(WecomKfConfigService.ResolvedAccount resolved, Long conversationId, String reason) {
        WecomKfConversationEntity snapshot = requireConversation(resolved, conversationId);
        WecomKfClient.ServiceState remote = client.getServiceState(resolved, snapshot.getExternalUserId());
        return requireTransaction(transactionTemplate.execute(status -> {
            WecomKfConversationEntity locked = requireLocked(resolved, conversationId);
            locked.synchronizeRemoteState(remote.state(), remote.servicerUserId(), reason, Instant.now());
            conversationRepository.save(locked);
            return stateOf(locked);
        }));
    }

    public HandoffReceipt queueForHuman(WecomKfConfigService.ResolvedAccount resolved,
                                        Long conversationId,
                                        String actorUserId,
                                        String idempotencyKey,
                                        String correlationId,
                                        long expectedRevision,
                                        String reason) {
        return handoff(resolved, conversationId, actorUserId, idempotencyKey, correlationId,
                expectedRevision, STATE_PENDING, null, reason);
    }

    public HandoffReceipt takeover(WecomKfConfigService.ResolvedAccount resolved,
                                   Long conversationId,
                                   String actorUserId,
                                   String idempotencyKey,
                                   String correlationId,
                                   long expectedRevision,
                                   String reason) {
        return handoff(resolved, conversationId, actorUserId, idempotencyKey, correlationId,
                expectedRevision, STATE_HUMAN, actorUserId, reason);
    }

    public AiSendReceipt sendAiReply(WecomKfConfigService.ResolvedAccount resolved,
                                     Long conversationId,
                                     long expectedRevision,
                                     String content) {
        try {
            return requireTransaction(transactionTemplate.execute(status -> {
                WecomKfConversationEntity locked = requireLocked(resolved, conversationId);
                if (locked.getStateRevision() != expectedRevision || !locked.aiOwned()) {
                    return new AiSendReceipt("suppressed_owner_changed", "", stateOf(locked));
                }
                WecomKfClient.ServiceState remote = client.getServiceState(resolved, locked.getExternalUserId());
                locked.synchronizeRemoteState(remote.state(), remote.servicerUserId(), "pre_send_fence", Instant.now());
                if (!locked.aiOwned()) {
                    conversationRepository.save(locked);
                    return new AiSendReceipt("suppressed_remote_owner", "", stateOf(locked));
                }
                if (!locked.canReply(Instant.now())) {
                    conversationRepository.save(locked);
                    return new AiSendReceipt("window_closed", "", stateOf(locked));
                }
                WecomKfClient.SendResult sent = client.sendText(resolved, locked.getExternalUserId(), content);
                locked.markReplySent();
                conversationRepository.save(locked);
                return new AiSendReceipt("sent", sent.messageId(), stateOf(locked));
            }));
        } catch (RuntimeException ex) {
            return new AiSendReceipt("send_failed", "", null);
        }
    }

    public ConversationState applyStateEvent(WecomKfConfigService.ResolvedAccount resolved,
                                             Long conversationId,
                                             int state,
                                             String servicerUserId,
                                             String reason) {
        return requireTransaction(transactionTemplate.execute(status -> {
            WecomKfConversationEntity locked = requireLocked(resolved, conversationId);
            locked.synchronizeRemoteState(state, servicerUserId, reason, Instant.now());
            conversationRepository.save(locked);
            return stateOf(locked);
        }));
    }

    private HandoffReceipt handoff(WecomKfConfigService.ResolvedAccount resolved,
                                   Long conversationId,
                                   String actorUserId,
                                   String idempotencyKey,
                                   String correlationId,
                                   long expectedRevision,
                                   int targetState,
                                   String servicerUserId,
                                   String reason) {
        String actor = requireText(actorUserId, "actorUserId");
        String idem = requireBoundedText(idempotencyKey, "idempotencyKey", 128);
        String correlation = requireBoundedText(correlationId, "correlationId", 128);
        Reservation reservation = requireTransaction(transactionTemplate.execute(status -> reserve(
                resolved, conversationId, actor, idem, correlation, expectedRevision, targetState, reason)));
        if (reservation.replayed() && !WecomKfHandoffOperationEntity.IN_PROGRESS.equals(reservation.operation().getStatus())) {
            return receipt(reservation.operation(), reservation.state());
        }
        if (!WecomKfHandoffOperationEntity.IN_PROGRESS.equals(reservation.operation().getStatus())) {
            return receipt(reservation.operation(), reservation.state());
        }

        WecomKfClient.ServiceState readback = null;
        try {
            if (targetState == STATE_HUMAN) {
                boolean accepting = client.listServicers(resolved).stream()
                        .anyMatch(item -> item.accepting() && actor.equals(item.userId()));
                if (!accepting) {
                    throw new HandoffFailure("SERVICER_NOT_ACCEPTING");
                }
            }
            readback = client.getServiceState(resolved, reservation.externalUserId());
            boolean alreadyApplied = readback.state() == targetState
                    && (targetState != STATE_HUMAN || actor.equals(readback.servicerUserId()));
            if (!alreadyApplied) {
                client.transferServiceState(resolved, reservation.externalUserId(), targetState, servicerUserId);
                readback = client.getServiceState(resolved, reservation.externalUserId());
            }
            if (readback.state() != targetState
                    || (targetState == STATE_HUMAN && !actor.equals(readback.servicerUserId()))) {
                throw new HandoffFailure("READBACK_MISMATCH");
            }
            WecomKfClient.ServiceState verified = readback;
            return requireTransaction(transactionTemplate.execute(status -> completeSuccess(
                    resolved, reservation.operation().getOperationId(), verified, reason)));
        } catch (RuntimeException ex) {
            if (readback == null) {
                try {
                    readback = client.getServiceState(resolved, reservation.externalUserId());
                } catch (RuntimeException ignored) {
                    // Preserve the original error; the local owner is restored below when remote readback is unavailable.
                }
            }
            WecomKfClient.ServiceState observed = readback;
            String errorCode = ex instanceof HandoffFailure failure ? failure.code() : "WECOM_API_FAILED";
            return requireTransaction(transactionTemplate.execute(status -> completeFailure(
                    resolved, reservation.operation().getOperationId(), observed, errorCode)));
        }
    }

    private Reservation reserve(WecomKfConfigService.ResolvedAccount resolved,
                                Long conversationId,
                                String actor,
                                String idempotencyKey,
                                String correlationId,
                                long expectedRevision,
                                int targetState,
                                String reason) {
        WecomKfConversationEntity locked = requireLocked(resolved, conversationId);
        var existing = operationRepository.findByCompanyIdAndConversationIdAndActorUserIdAndIdempotencyKey(
                locked.getCompanyId(), locked.getId(), actor, idempotencyKey);
        if (existing.isPresent()) {
            WecomKfHandoffOperationEntity operation = existing.get();
            if (operation.getTargetState() != targetState || operation.getExpectedRevision() != expectedRevision) {
                throw new IllegalArgumentException("idempotency key was reused with different handoff parameters");
            }
            return new Reservation(operation, locked.getExternalUserId(), stateOf(locked), true);
        }
        WecomKfHandoffOperationEntity operation = new WecomKfHandoffOperationEntity(
                locked.getCompanyId(), locked.getId(), actor, idempotencyKey, correlationId,
                expectedRevision, locked.getRemoteServiceState(), targetState, clip(reason, 64));
        if (locked.getStateRevision() != expectedRevision) {
            operation.conflict("REVISION_CONFLICT", locked.getStateRevision());
            operationRepository.save(operation);
            return new Reservation(operation, locked.getExternalUserId(), stateOf(locked), false);
        }
        locked.reserveHandoff(reason);
        conversationRepository.save(locked);
        operationRepository.save(operation);
        return new Reservation(operation, locked.getExternalUserId(), stateOf(locked), false);
    }

    private HandoffReceipt completeSuccess(WecomKfConfigService.ResolvedAccount resolved,
                                           UUID operationId,
                                           WecomKfClient.ServiceState readback,
                                           String reason) {
        WecomKfHandoffOperationEntity operation = requireOperation(operationId);
        WecomKfConversationEntity locked = requireLocked(resolved, operation.getConversationId());
        locked.synchronizeRemoteState(readback.state(), readback.servicerUserId(), reason, Instant.now());
        conversationRepository.save(locked);
        operation.succeed(readback.state(), locked.getStateRevision());
        operationRepository.save(operation);
        return receipt(operation, stateOf(locked));
    }

    private HandoffReceipt completeFailure(WecomKfConfigService.ResolvedAccount resolved,
                                           UUID operationId,
                                           WecomKfClient.ServiceState readback,
                                           String errorCode) {
        WecomKfHandoffOperationEntity operation = requireOperation(operationId);
        WecomKfConversationEntity locked = requireLocked(resolved, operation.getConversationId());
        int restoredState = readback == null ? operation.getOldState() : readback.state();
        String restoredServicer = readback == null ? locked.getServicerUserId() : readback.servicerUserId();
        locked.synchronizeRemoteState(restoredState, restoredServicer, "handoff_failed", Instant.now());
        conversationRepository.save(locked);
        operation.fail(errorCode, readback == null ? null : readback.state(), locked.getStateRevision());
        operationRepository.save(operation);
        return receipt(operation, stateOf(locked));
    }

    private WecomKfConversationEntity requireConversation(WecomKfConfigService.ResolvedAccount resolved, Long conversationId) {
        WecomKfConversationEntity conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found"));
        verifyScope(resolved, conversation);
        return conversation;
    }

    private WecomKfConversationEntity requireLocked(WecomKfConfigService.ResolvedAccount resolved, Long conversationId) {
        WecomKfConversationEntity conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("conversation not found"));
        verifyScope(resolved, conversation);
        return conversation;
    }

    private void verifyScope(WecomKfConfigService.ResolvedAccount resolved, WecomKfConversationEntity conversation) {
        if (!Objects.equals(resolved.account().getCompanyId(), conversation.getCompanyId())
                || !Objects.equals(resolved.account().getOpenKfId(), conversation.getOpenKfId())) {
            throw new IllegalArgumentException("conversation is outside the customer service account scope");
        }
    }

    private WecomKfHandoffOperationEntity requireOperation(UUID operationId) {
        return operationRepository.findByOperationId(operationId)
                .orElseThrow(() -> new IllegalStateException("handoff operation not found"));
    }

    private ConversationState stateOf(WecomKfConversationEntity conversation) {
        return new ConversationState(conversation.getPublicId(), conversation.getRemoteServiceState(),
                conversation.getOwnerMode(), conversation.getServicerUserId(), conversation.getStateRevision(),
                conversation.getStateCheckedAt());
    }

    private HandoffReceipt receipt(WecomKfHandoffOperationEntity operation, ConversationState state) {
        return new HandoffReceipt(operation.getOperationId(), operation.getStatus(), operation.getCorrelationId(),
                operation.getExpectedRevision(), operation.getResultingRevision(), operation.getOldState(),
                operation.getTargetState(), operation.getReadbackState(), operation.getErrorCode(), state);
    }

    private String requireText(String value, String field) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return text;
    }

    private String requireBoundedText(String value, String field, int max) {
        String text = requireText(value, field);
        if (text.length() > max) {
            throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        }
        return text;
    }

    private String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    private <T> T requireTransaction(T value) {
        if (value == null) {
            throw new IllegalStateException("transaction returned no result");
        }
        return value;
    }

    private record Reservation(WecomKfHandoffOperationEntity operation,
                               String externalUserId,
                               ConversationState state,
                               boolean replayed) {
    }

    private static final class HandoffFailure extends RuntimeException {
        private final String code;

        private HandoffFailure(String code) {
            super(code);
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    public record ConversationState(UUID conversationId,
                                    int serviceState,
                                    String ownerMode,
                                    String servicerUserId,
                                    long revision,
                                    Instant checkedAt) {
    }

    public record HandoffReceipt(UUID operationId,
                                 String status,
                                 String correlationId,
                                 long expectedRevision,
                                 Long resultingRevision,
                                 int oldState,
                                 int targetState,
                                 Integer readbackState,
                                 String errorCode,
                                 ConversationState state) {
    }

    public record AiSendReceipt(String status, String remoteMessageId, ConversationState state) {
    }
}
