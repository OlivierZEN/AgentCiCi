package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WecomKfConversationService {

    private final WecomKfConversationRepository conversationRepository;
    private final WecomKfMessageRepository messageRepository;
    private final AgentRunTraceRepository traceRepository;
    private final ChatOrchestratorService chatOrchestratorService;
    private final WecomKfHandoffService handoffService;

    public WecomKfConversationService(WecomKfConversationRepository conversationRepository,
                                      WecomKfMessageRepository messageRepository,
                                      AgentRunTraceRepository traceRepository,
                                      ChatOrchestratorService chatOrchestratorService,
                                      WecomKfHandoffService handoffService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.traceRepository = traceRepository;
        this.chatOrchestratorService = chatOrchestratorService;
        this.handoffService = handoffService;
    }

    public void acceptCustomerMessage(WecomKfConfigService.ResolvedAccount resolved, WecomKfClient.SyncedMessage message) {
        if (message == null || blank(message.msgId()).isBlank()) {
            return;
        }
        String companyId = resolved.account().getCompanyId();
        if (messageRepository.existsByCompanyIdAndMsgId(companyId, message.msgId())) {
            return;
        }
        String externalUserId = blank(message.externalUserId());
        if (externalUserId.isBlank()) {
            persistWithoutConversation(resolved, message);
            return;
        }
        String openKfId = blank(message.openKfId()).isBlank() ? resolved.account().getOpenKfId() : blank(message.openKfId());
        WecomKfConversationEntity conversation = conversationRepository
                .findByCompanyIdAndCorpIdAndOpenKfIdAndExternalUserId(companyId, resolved.account().getCorpId(), openKfId, externalUserId)
                .orElseGet(() -> new WecomKfConversationEntity(
                        companyId,
                        resolved.account().getCorpId(),
                        openKfId,
                        externalUserId,
                        sessionId(resolved.account().getCorpId(), openKfId, externalUserId),
                        resolved.account().getAgentId(),
                        resolved.account().getRunAsUserId()));
        conversation = conversationRepository.save(conversation);

        String msgType = blank(message.msgType()).isBlank() ? "unknown" : blank(message.msgType());
        String content = "text".equalsIgnoreCase(msgType) ? blank(message.content()) : "非文本消息";
        boolean customerOrigin = message.origin() == 3;
        boolean humanOrigin = message.origin() == 5;
        String direction = humanOrigin ? WecomKfMessageEntity.DIRECTION_OUTBOUND : WecomKfMessageEntity.DIRECTION_INBOUND;
        messageRepository.save(new WecomKfMessageEntity(
                companyId,
                message.msgId(),
                resolved.account().getCorpId(),
                openKfId,
                externalUserId,
                direction,
                msgType,
                clip(content, 1000),
                "",
                "received",
                message.origin(),
                message.servicerUserId(),
                message.eventType(),
                message.msgId()));

        if (message.origin() == 4 || "event".equalsIgnoreCase(msgType)) {
            if (message.eventServiceState() >= 0 && message.eventServiceState() <= 4) {
                handoffService.applyStateEvent(resolved, conversation.getId(), message.eventServiceState(),
                        message.newServicerUserId(), "wecom_event:" + blank(message.eventType()));
            } else {
                safeRefresh(resolved, conversation.getId(), "wecom_event:" + blank(message.eventType()));
            }
            return;
        }
        if (humanOrigin || !customerOrigin) {
            safeRefresh(resolved, conversation.getId(), humanOrigin ? "human_message" : "non_customer_origin");
            return;
        }

        Instant messageAt = messageInstant(message.sendTime());
        conversation.markCustomerMessage(messageAt);
        conversation = conversationRepository.save(conversation);
        WecomKfHandoffService.ConversationState current = handoffService.refreshState(
                resolved, conversation.getId(), "customer_message");

        if (humanRequested(content)) {
            handoffService.queueForHuman(resolved, conversation.getId(), "customer:" + externalUserId,
                    "customer:" + message.msgId() + ":queue", message.msgId(), current.revision(), "customer_request");
            return;
        }
        if (!WecomKfConversationEntity.OWNER_AI.equals(current.ownerMode())) {
            return;
        }
        long capturedRevision = current.revision();

        String answer = "当前版本先支持文字描述。请用文字补充订单号、手机号、序列号或问题详情。";
        String traceId = "";
        if ("text".equalsIgnoreCase(msgType) && !content.isBlank()) {
            Map<String, Object> result = chatOrchestratorService.chat(
                    companyId,
                    conversation.getRunAsUserId(),
                    conversation.getSessionId(),
                    content,
                    List.of(),
                    conversation.getAgentId(),
                    null,
                    Map.of(),
                    "wecom-kf");
            Object rawAnswer = result.get("answer");
            answer = rawAnswer == null ? "" : String.valueOf(rawAnswer);
            traceId = annotateLatestTrace(conversation, message.msgId(), externalUserId);
        }
        reply(resolved, conversation, message.msgId(), answer, traceId, capturedRevision);
    }

    private void reply(WecomKfConfigService.ResolvedAccount resolved,
                       WecomKfConversationEntity conversation,
                       String inboundMsgId,
                       String answer,
                       String traceId,
                       long expectedRevision) {
        String content = blank(answer).isBlank() ? "这次没有生成可发送的回复，请稍后再试或转人工处理。" : blank(answer);
        WecomKfHandoffService.AiSendReceipt receipt = handoffService.sendAiReply(
                resolved, conversation.getId(), expectedRevision, content);
        messageRepository.save(new WecomKfMessageEntity(
                conversation.getCompanyId(),
                outboundMessageId(inboundMsgId),
                conversation.getCorpId(),
                conversation.getOpenKfId(),
                conversation.getExternalUserId(),
                WecomKfMessageEntity.DIRECTION_OUTBOUND,
                "text",
                clip(content, 1000),
                traceId,
                receipt.status(),
                1,
                "",
                "",
                receipt.remoteMessageId()));
    }

    private void persistWithoutConversation(WecomKfConfigService.ResolvedAccount resolved,
                                            WecomKfClient.SyncedMessage message) {
        String msgType = blank(message.msgType()).isBlank() ? "unknown" : blank(message.msgType());
        messageRepository.save(new WecomKfMessageEntity(
                resolved.account().getCompanyId(), message.msgId(), resolved.account().getCorpId(),
                blank(message.openKfId()).isBlank() ? resolved.account().getOpenKfId() : blank(message.openKfId()),
                null, WecomKfMessageEntity.DIRECTION_INBOUND, msgType, "无客户标识的企业微信事件", "", "received",
                message.origin(), message.servicerUserId(), message.eventType(), message.msgId()));
    }

    private void safeRefresh(WecomKfConfigService.ResolvedAccount resolved, Long conversationId, String reason) {
        try {
            handoffService.refreshState(resolved, conversationId, reason);
        } catch (RuntimeException ignored) {
            // The callback is durable through sync cursor/message id. A later message or mobile read refreshes state again.
        }
    }

    private boolean humanRequested(String content) {
        String normalized = blank(content).replaceAll("\\s+", "");
        return normalized.matches(".*(转人工|人工客服|人工服务|找人工|真人客服|人工接待|转接人工).*?");
    }

    private String annotateLatestTrace(WecomKfConversationEntity conversation, String requestId, String externalUserId) {
        AgentRunTraceEntity trace = traceRepository
                .findFirstByCompanyIdAndSessionIdAndAgentIdOrderByStartedAtDesc(
                        conversation.getCompanyId(),
                        conversation.getSessionId(),
                        conversation.getAgentId())
                .orElse(null);
        if (trace == null) {
            return "";
        }
        trace.markExternalSource("wechat_kf", requestId, externalUserId);
        traceRepository.save(trace);
        return trace.getTraceId();
    }

    public String sessionId(String corpId, String openKfId, String externalUserId) {
        String key = "wecom-kf|" + blank(corpId) + "|" + blank(openKfId) + "|" + blank(externalUserId);
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String outboundMessageId(String inboundMsgId) {
        return "reply:" + blank(inboundMsgId);
    }

    private Instant messageInstant(long raw) {
        if (raw <= 0) {
            return Instant.now();
        }
        return raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
    }

    private String clip(String value, int max) {
        String text = blank(value);
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String blank(String value) {
        return value == null ? "" : value.trim();
    }
}
