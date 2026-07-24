package com.codehouse.ciciassistant.wecom.service;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.service.ChatOrchestratorService;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfConversationRepository;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageEntity;
import com.codehouse.ciciassistant.wecom.domain.WecomKfMessageRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WecomKfConversationService {

    private final WecomKfConversationRepository conversationRepository;
    private final WecomKfMessageRepository messageRepository;
    private final AgentRunTraceRepository traceRepository;
    private final ChatOrchestratorService chatOrchestratorService;
    private final WecomKfClient client;

    public WecomKfConversationService(WecomKfConversationRepository conversationRepository,
                                      WecomKfMessageRepository messageRepository,
                                      AgentRunTraceRepository traceRepository,
                                      ChatOrchestratorService chatOrchestratorService,
                                      WecomKfClient client) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.traceRepository = traceRepository;
        this.chatOrchestratorService = chatOrchestratorService;
        this.client = client;
    }

    @Transactional
    public void acceptCustomerMessage(WecomKfConfigService.ResolvedAccount resolved, WecomKfClient.SyncedMessage message) {
        if (message == null || blank(message.msgId()).isBlank()) {
            return;
        }
        String companyId = resolved.account().getCompanyId();
        if (messageRepository.existsByCompanyIdAndMsgId(companyId, message.msgId())) {
            return;
        }
        String externalUserId = blank(message.externalUserId());
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
        Instant messageAt = messageInstant(message.sendTime());
        conversation.markCustomerMessage(messageAt);
        conversationRepository.save(conversation);

        String msgType = blank(message.msgType()).isBlank() ? "unknown" : blank(message.msgType());
        String content = "text".equalsIgnoreCase(msgType) ? blank(message.content()) : "非文本消息";
        messageRepository.save(new WecomKfMessageEntity(
                companyId,
                message.msgId(),
                resolved.account().getCorpId(),
                openKfId,
                externalUserId,
                WecomKfMessageEntity.DIRECTION_INBOUND,
                msgType,
                clip(content, 1000),
                "",
                "received"));

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
                    null);
            Object rawAnswer = result.get("answer");
            answer = rawAnswer == null ? "" : String.valueOf(rawAnswer);
            traceId = annotateLatestTrace(conversation, message.msgId(), externalUserId);
        }
        reply(resolved, conversation, message.msgId(), answer, traceId);
    }

    private void reply(WecomKfConfigService.ResolvedAccount resolved,
                       WecomKfConversationEntity conversation,
                       String inboundMsgId,
                       String answer,
                       String traceId) {
        String content = blank(answer).isBlank() ? "这次没有生成可发送的回复，请稍后再试或转人工处理。" : blank(answer);
        String status;
        if (!conversation.canReply(Instant.now())) {
            status = "window_closed";
        } else {
            try {
                client.sendText(resolved, conversation.getExternalUserId(), content);
                conversation.markReplySent();
                conversationRepository.save(conversation);
                status = "sent";
            } catch (RuntimeException ex) {
                status = "send_failed";
            }
        }
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
                status));
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((blank(corpId) + ":" + blank(openKfId) + ":" + blank(externalUserId))
                    .getBytes(StandardCharsets.UTF_8));
            return "wecom-kf:" + HexFormat.of().formatHex(bytes).substring(0, 32);
        } catch (Exception ex) {
            throw new IllegalStateException("Build WeCom session failed", ex);
        }
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
