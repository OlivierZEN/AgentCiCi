package com.codehouse.ciciassistant.feishu.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lark.oapi.Client;
import com.lark.oapi.service.im.v1.model.CreateMessageReq;
import com.lark.oapi.service.im.v1.model.CreateMessageReqBody;
import com.lark.oapi.service.im.v1.model.CreateMessageResp;
import com.lark.oapi.service.im.v1.model.ReplyMessageReq;
import com.lark.oapi.service.im.v1.model.ReplyMessageReqBody;
import com.lark.oapi.service.im.v1.model.ReplyMessageResp;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeishuBotMessenger {

    private static final Logger log = LoggerFactory.getLogger(FeishuBotMessenger.class);

    private final ObjectMapper objectMapper;

    public FeishuBotMessenger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void replyText(FeishuBotConfigService.FeishuBotConfig config, String messageId, String text) {
        String normalizedText = normalizeText(text);
        if (messageId == null || messageId.isBlank() || normalizedText.isBlank()) {
            return;
        }
        try {
            Client client = Client.newBuilder(config.appId(), config.appSecret()).build();
            ReplyMessageReq req = ReplyMessageReq.newBuilder()
                    .messageId(messageId)
                    .replyMessageReqBody(ReplyMessageReqBody.newBuilder()
                            .content(objectMapper.writeValueAsString(Map.of("text", normalizedText)))
                            .msgType("text")
                            .replyInThread(false)
                            .uuid(UUID.randomUUID().toString())
                            .build())
                    .build();
            ReplyMessageResp resp = client.im().v1().message().reply(req);
            if (!resp.success()) {
                throw new IllegalArgumentException("飞书回复失败: code=" + resp.getCode() + ", msg=" + resp.getMsg());
            }
        } catch (Exception ex) {
            log.error("Failed to reply Feishu message {}", messageId, ex);
            throw new IllegalArgumentException("飞书回复失败: " + ex.getMessage());
        }
    }

    public void sendTextToOpenId(FeishuBotConfigService.FeishuBotConfig config, String openId, String text) {
        String normalizedText = normalizeText(text);
        if (openId == null || openId.isBlank() || normalizedText.isBlank()) {
            return;
        }
        try {
            Client client = Client.newBuilder(config.appId(), config.appSecret()).build();
            CreateMessageReq req = CreateMessageReq.newBuilder()
                    .receiveIdType("open_id")
                    .createMessageReqBody(CreateMessageReqBody.newBuilder()
                            .receiveId(openId)
                            .content(objectMapper.writeValueAsString(Map.of("text", normalizedText)))
                            .msgType("text")
                            .uuid(UUID.randomUUID().toString())
                            .build())
                    .build();
            CreateMessageResp resp = client.im().v1().message().create(req);
            if (!resp.success()) {
                throw new IllegalArgumentException("飞书主动发送失败: code=" + resp.getCode() + ", msg=" + resp.getMsg());
            }
        } catch (Exception ex) {
            log.error("Failed to proactively send Feishu DM to openId={}", openId, ex);
            throw new IllegalArgumentException("飞书主动发送失败: " + ex.getMessage());
        }
    }

    private String normalizeText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() > 6000) {
            return normalized.substring(0, 6000);
        }
        return normalized;
    }
}
