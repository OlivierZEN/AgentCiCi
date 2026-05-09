package com.codehouse.ciciassistant.openapi.service;

import com.codehouse.ciciassistant.openapi.domain.AgentApiCallLogEntity;
import com.codehouse.ciciassistant.openapi.domain.AgentApiCallLogRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AgentOpenApiCallLogService {

    private final AgentApiCallLogRepository callLogRepository;

    public AgentOpenApiCallLogService(AgentApiCallLogRepository callLogRepository) {
        this.callLogRepository = callLogRepository;
    }

    @Transactional
    public void start(AgentOpenApiAuthService.AuthenticatedCredential auth,
                      AgentOpenApiSessionService.SessionResolution session,
                      String requestId,
                      String externalUserId,
                      String idempotencyKey,
                      String message) {
        callLogRepository.save(new AgentApiCallLogEntity(
                requestId,
                auth.credential().getOrgId(),
                auth.credential().getId(),
                auth.credential().getAgentId(),
                auth.credential().getRunAsUserId(),
                session.externalSessionId(),
                session.internalSessionId(),
                externalUserId,
                auth.clientIp(),
                clip(idempotencyKey, 128),
                message == null ? 0 : message.length(),
                clip(message, 1200)));
    }

    @Transactional
    public void completeSuccess(Long credentialId,
                                String requestId,
                                String traceId,
                                String answer,
                                int elapsedMs) {
        callLogRepository.findByRequestIdAndCredentialId(requestId, credentialId)
                .ifPresent(entity -> entity.completeSuccess(
                        200,
                        traceId,
                        answer == null ? 0 : answer.length(),
                        elapsedMs,
                        clip(answer, 1200)));
    }

    @Transactional
    public void completeFailure(Long credentialId,
                                String requestId,
                                int httpStatus,
                                String errorCode,
                                int elapsedMs,
                                String message) {
        callLogRepository.findByRequestIdAndCredentialId(requestId, credentialId)
                .ifPresent(entity -> entity.completeFailure(
                        httpStatus,
                        errorCode,
                        elapsedMs,
                        clip(message, 1200)));
    }

    public List<CallLogView> list(String orgId,
                                  String agentId,
                                  Instant from,
                                  Instant to,
                                  Long credentialId,
                                  String status,
                                  String q) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(Duration.ofDays(7)) : from;
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return callLogRepository.findTop100ByOrgIdAndAgentIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        orgId,
                        agentId,
                        resolvedFrom,
                        resolvedTo)
                .stream()
                .filter(item -> credentialId == null || credentialId.equals(item.getCredentialId()))
                .filter(item -> normalizedStatus.isBlank() || normalizedStatus.equals(item.getStatus()))
                .filter(item -> keyword.isBlank() || matchesKeyword(item, keyword))
                .map(this::toView)
                .toList();
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }

    private boolean matchesKeyword(AgentApiCallLogEntity item, String keyword) {
        return contains(item.getRequestId(), keyword)
                || contains(item.getTraceId(), keyword)
                || contains(item.getExternalSessionId(), keyword)
                || contains(item.getInternalSessionId(), keyword)
                || contains(item.getExternalUserId(), keyword)
                || contains(item.getRequestSummary(), keyword)
                || contains(item.getResponseSummary(), keyword);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private CallLogView toView(AgentApiCallLogEntity item) {
        return new CallLogView(
                item.getRequestId(),
                item.getCredentialId(),
                item.getAgentId(),
                item.getRunAsUserId(),
                item.getExternalSessionId(),
                item.getInternalSessionId(),
                item.getExternalUserId(),
                item.getClientIp(),
                item.getStatus(),
                item.getHttpStatus(),
                item.getErrorCode(),
                item.getTraceId(),
                item.getPromptChars(),
                item.getResponseChars(),
                item.getElapsedMs(),
                item.getRequestSummary(),
                item.getResponseSummary(),
                item.getCreatedAt(),
                item.getCompletedAt());
    }

    public record CallLogView(
            String requestId,
            Long credentialId,
            String agentId,
            String runAsUserId,
            String externalSessionId,
            String internalSessionId,
            String externalUserId,
            String clientIp,
            String status,
            int httpStatus,
            String errorCode,
            String traceId,
            int promptChars,
            int responseChars,
            int elapsedMs,
            String requestSummary,
            String responseSummary,
            Instant createdAt,
            Instant completedAt
    ) {
    }
}
