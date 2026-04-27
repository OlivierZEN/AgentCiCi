package com.codehouse.ciciassistant.feishu.service;

import com.codehouse.ciciassistant.feishu.domain.FeishuBotBindingEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.service.im.v1.model.EventMessage;
import com.lark.oapi.service.im.v1.model.EventSender;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1;
import com.lark.oapi.service.im.v1.model.P2MessageReceiveV1Data;
import com.lark.oapi.service.im.v1.model.UserId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotEventBridgeService {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotEventBridgeService.class);
    private static final Pattern PAIRING_PATTERN = Pattern.compile("^(?:配对|pair)\\s+([A-Za-z0-9]{4,12})$", Pattern.CASE_INSENSITIVE);

    private final ObjectMapper objectMapper;
    private final FeishuPairingCodeStore pairingCodeStore;
    private final FeishuBotConfigService feishuBotConfigService;
    private final FeishuBotPairingService feishuBotPairingService;
    private final FeishuUserProfileService feishuUserProfileService;
    private final FeishuBotConversationService feishuBotConversationService;
    private final FeishuBotMessenger feishuBotMessenger;

    public FeishuBotEventBridgeService(ObjectMapper objectMapper,
                                       FeishuPairingCodeStore pairingCodeStore,
                                       FeishuBotConfigService feishuBotConfigService,
                                       FeishuBotPairingService feishuBotPairingService,
                                       FeishuUserProfileService feishuUserProfileService,
                                       FeishuBotConversationService feishuBotConversationService,
                                       FeishuBotMessenger feishuBotMessenger) {
        this.objectMapper = objectMapper;
        this.pairingCodeStore = pairingCodeStore;
        this.feishuBotConfigService = feishuBotConfigService;
        this.feishuBotPairingService = feishuBotPairingService;
        this.feishuUserProfileService = feishuUserProfileService;
        this.feishuBotConversationService = feishuBotConversationService;
        this.feishuBotMessenger = feishuBotMessenger;
    }

    public void acceptMessageEvent(String orgId, P2MessageReceiveV1 event) {
        P2MessageReceiveV1Data eventData = event == null ? null : event.getEvent();
        EventMessage message = eventData == null ? null : eventData.getMessage();
        if (message == null || message.getMessageId() == null || message.getMessageId().isBlank()) {
            return;
        }
        if (!pairingCodeStore.markMessageProcessed(message.getMessageId())) {
            log.info("Ignore duplicate Feishu message {}", message.getMessageId());
            return;
        }
        java.util.concurrent.CompletableFuture.runAsync(() -> processMessage(orgId, eventData));
    }

    private void processMessage(String orgId, P2MessageReceiveV1Data eventData) {
        FeishuBotConfigService.FeishuBotConfig config = feishuBotConfigService.getEnabledConfig(orgId).orElse(null);
        if (config == null) {
            return;
        }

        EventMessage message = eventData.getMessage();
        EventSender sender = eventData.getSender();
        String messageId = message.getMessageId();
        try {
            if (!"p2p".equalsIgnoreCase(message.getChatType())) {
                feishuBotMessenger.replyText(config, messageId, "当前版本先支持与机器人单聊。");
                return;
            }
            if (!"text".equalsIgnoreCase(message.getMessageType())) {
                feishuBotMessenger.replyText(config, messageId, "当前版本先支持文本消息。你可以直接给我发送文字问题。");
                return;
            }

            String text = extractText(message.getContent());
            if (text.isBlank()) {
                feishuBotMessenger.replyText(config, messageId, "没有识别到可处理的文本内容，请直接发送文字问题。");
                return;
            }

            UserId senderId = sender == null ? null : sender.getSenderId();
            String openId = senderId == null ? "" : blankToEmpty(senderId.getOpenId());
            String unionId = senderId == null ? "" : blankToEmpty(senderId.getUnionId());
            String tenantKey = sender == null ? "" : blankToEmpty(sender.getTenantKey());
            if (openId.isBlank() || tenantKey.isBlank()) {
                log.warn("Missing Feishu sender identity, orgId={}, messageId={}", orgId, messageId);
                return;
            }

            Matcher matcher = PAIRING_PATTERN.matcher(text.trim());
            if (matcher.matches()) {
                FeishuBotBindingEntity binding = feishuBotPairingService.consumePairingCode(
                        orgId,
                        matcher.group(1),
                        tenantKey,
                        openId,
                        unionId,
                        message.getChatId()
                );
                feishuBotMessenger.replyText(config, messageId,
                        "配对成功，当前已绑定到系统智能体「" + displayAgentName(binding.getAgentCode()) + "」。后续你可以直接继续和我对话。");
                return;
            }

            FeishuBotBindingEntity binding = feishuBotPairingService.findActiveBinding(orgId, tenantKey, openId)
                    .orElseGet(() -> feishuBotPairingService.ensureAutoBinding(orgId, tenantKey, openId, unionId, message.getChatId()));
            feishuUserProfileService.fetchProfile(orgId, openId)
                    .ifPresent(profile -> feishuBotPairingService.touchProfile(binding, profile.displayName(), profile.avatarUrl()));

            feishuBotPairingService.touchMessage(binding, message.getChatId());
            String answer = feishuBotConversationService.ask(binding, tenantKey, message.getChatId(), text);
            feishuBotMessenger.replyText(config, messageId,
                    answer == null || answer.isBlank()
                            ? "这次没有生成可发送的回复，请稍后再试。"
                            : answer);
        } catch (Exception ex) {
            log.error("Failed to bridge Feishu message, orgId={}, messageId={}", orgId, messageId, ex);
            try {
                feishuBotMessenger.replyText(config, messageId, "这次处理失败了，请稍后重试，或回到系统工作台继续处理。");
            } catch (Exception replyEx) {
                log.warn("Failed to send Feishu fallback reply, messageId={}", messageId, replyEx);
            }
        }
    }

    private String extractText(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(content);
            return blankToEmpty(node.path("text").asText(""));
        } catch (Exception ignored) {
            return content;
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String displayAgentName(String agentCode) {
        return "cici".equalsIgnoreCase(agentCode) ? "CiCi" : agentCode;
    }
}
