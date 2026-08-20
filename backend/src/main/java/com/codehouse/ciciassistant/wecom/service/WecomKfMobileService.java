package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageRepository;
import com.codehouse.ciciassistant.common.error.UnauthorizedException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class WecomKfMobileService {

    private static final long SERVICER_ELIGIBILITY_CACHE_SECONDS = 60;

    private final WecomKfMobileSessionStore sessionStore;
    private final WecomKfConfigService configService;
    private final WecomKfConversationRepository conversationRepository;
    private final WecomKfHandoffService handoffService;
    private final WecomKfClient client;
    private final WecomKfMessageRepository messageRepository;
    private final Map<String, Instant> eligibilityCache = new ConcurrentHashMap<>();

    public WecomKfMobileService(WecomKfMobileSessionStore sessionStore,
                                WecomKfConfigService configService,
                                WecomKfConversationRepository conversationRepository,
                                WecomKfHandoffService handoffService,
                                WecomKfClient client,
                                WecomKfMessageRepository messageRepository) {
        this.sessionStore = sessionStore;
        this.configService = configService;
        this.conversationRepository = conversationRepository;
        this.handoffService = handoffService;
        this.client = client;
        this.messageRepository = messageRepository;
    }

    public MobileContext context(String sessionToken, String pageUrl) {
        AuthenticatedOperator operator = authenticate(sessionToken);
        Map<String, String> latestSummaries = messageRepository.findLatestCustomerSummaries(
                        operator.resolved().account().getCompanyId(), operator.resolved().account().getOpenKfId())
                .stream()
                .collect(Collectors.toMap(WecomKfMessageRepository.LatestCustomerSummary::getExternalUserId,
                        item -> clip(item.getContentSummary(), 120), (left, right) -> left));
        List<ConversationSummary> conversations = conversationRepository
                .findTop100ByCompanyIdAndOpenKfIdOrderByUpdatedAtDesc(
                        operator.resolved().account().getCompanyId(), operator.resolved().account().getOpenKfId())
                .stream()
                .map(conversation -> summary(conversation, latestSummaries.get(conversation.getExternalUserId())))
                .toList();
        return new MobileContext(
                operator.resolved().account().getName(),
                operator.operatorUserId(),
                operator.resolved().account().getOpenKfId(),
                client.jsSdkBundle(operator.resolved(), pageUrl),
                conversations,
                Instant.now());
    }

    public ConversationSummary refresh(String sessionToken, UUID conversationId) {
        AuthenticatedOperator operator = authenticate(sessionToken);
        WecomKfConversationEntity conversation = requireConversation(operator, conversationId);
        handoffService.refreshState(operator.resolved(), conversation.getId(), "mobile_refresh");
        WecomKfConversationEntity refreshed = requireConversation(operator, conversationId);
        String latest = messageRepository
                .findFirstByCompanyIdAndOpenKfIdAndExternalUserIdAndOriginOrderByCreatedAtDesc(
                        refreshed.getCompanyId(), refreshed.getOpenKfId(), refreshed.getExternalUserId(), 3)
                .map(message -> message.getContentSummary())
                .orElse("");
        return summary(refreshed, latest);
    }

    public WecomKfHandoffService.HandoffReceipt takeover(String sessionToken,
                                                         UUID conversationId,
                                                         long expectedRevision,
                                                         String idempotencyKey,
                                                         String correlationId) {
        AuthenticatedOperator operator = authenticate(sessionToken);
        WecomKfConversationEntity conversation = requireConversation(operator, conversationId);
        return handoffService.takeover(operator.resolved(), conversation.getId(), operator.operatorUserId(),
                idempotencyKey, correlationId, expectedRevision, "mobile_force_takeover");
    }

    public AuthenticatedOperator authenticate(String sessionToken) {
        String token = sessionToken == null ? "" : sessionToken.trim();
        if (token.isBlank()) {
            throw new MobileUnauthorized("mobile session is required");
        }
        WecomKfMobileSessionStore.MobileSession session = sessionStore.findSession(token);
        if (session == null || session.expiresAt() == null || Instant.now().isAfter(session.expiresAt())) {
            throw new MobileUnauthorized("mobile session expired");
        }
        WecomKfConfigService.ResolvedAccount resolved = configService.findMobileEntry(session.entryId())
                .orElseThrow(() -> new MobileUnauthorized("mobile customer service entry is disabled"));
        if (!resolved.account().getCompanyId().equals(session.companyId())) {
            throw new MobileUnauthorized("mobile session scope mismatch");
        }
        Instant cachedUntil = eligibilityCache.get(token);
        boolean accepting = cachedUntil != null && cachedUntil.isAfter(Instant.now());
        if (!accepting) {
            accepting = client.listServicers(resolved).stream()
                    .anyMatch(servicer -> servicer.accepting() && session.operatorUserId().equals(servicer.userId()));
            if (accepting) {
                eligibilityCache.put(token, Instant.now().plusSeconds(SERVICER_ELIGIBILITY_CACHE_SECONDS));
            } else {
                eligibilityCache.remove(token);
            }
        }
        if (!accepting) {
            throw new MobileUnauthorized("current member is not an accepting customer service agent");
        }
        return new AuthenticatedOperator(resolved, session.operatorUserId());
    }

    private WecomKfConversationEntity requireConversation(AuthenticatedOperator operator, UUID conversationId) {
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId is required");
        }
        return conversationRepository.findByPublicIdAndCompanyIdAndOpenKfId(
                        conversationId,
                        operator.resolved().account().getCompanyId(),
                        operator.resolved().account().getOpenKfId())
                .orElseThrow(() -> new IllegalArgumentException("conversation not found"));
    }

    private ConversationSummary summary(WecomKfConversationEntity conversation, String lastCustomerSummary) {
        return new ConversationSummary(
                conversation.getPublicId(),
                maskedCustomer(conversation.getExternalUserId()),
                clip(lastCustomerSummary, 120),
                conversation.getExternalUserId(),
                conversation.getRemoteServiceState(),
                conversation.getOwnerMode(),
                conversation.getServicerUserId(),
                conversation.getStateRevision(),
                conversation.getStateCheckedAt(),
                conversation.getLastCustomerMessageAt(),
                conversation.getHandoffReason());
    }

    private String maskedCustomer(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= 8) {
            return text.isBlank() ? "未知客户" : text;
        }
        return text.substring(0, 4) + "…" + text.substring(text.length() - 4);
    }

    private String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    public record MobileContext(String accountName,
                                String operatorUserId,
                                String openKfId,
                                WecomKfClient.JsSdkBundle jsSdk,
                                List<ConversationSummary> conversations,
                                Instant generatedAt) {
    }

    public record ConversationSummary(UUID conversationId,
                                      String customerLabel,
                                      String lastCustomerSummary,
                                      String externalUserId,
                                      int serviceState,
                                      String ownerMode,
                                      String servicerUserId,
                                      long revision,
                                      Instant checkedAt,
                                      Instant lastCustomerMessageAt,
                                      String handoffReason) {
    }

    public record AuthenticatedOperator(WecomKfConfigService.ResolvedAccount resolved, String operatorUserId) {
    }

    public static class MobileUnauthorized extends UnauthorizedException {
        public MobileUnauthorized(String message) {
            super(message);
        }
    }
}
