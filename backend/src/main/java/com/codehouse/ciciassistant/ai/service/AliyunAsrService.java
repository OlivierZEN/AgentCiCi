package com.codehouse.ciciassistant.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AliyunAsrService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String asrModel;

    public AliyunAsrService(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            @Value("${app.model.aliyun.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
                            @Value("${app.model.aliyun.api-key:}") String apiKey,
                            @Value("${app.voice.aliyun.asr-model:qwen3-asr-flash}") String asrModel) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.asrModel = asrModel;
    }

    public String transcribe(byte[] audioBytes, String contentType) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Aliyun ASR API key is not configured");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("Audio bytes are empty");
        }

        String mime = normalizeMime(contentType);
        String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audioBytes);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", asrModel);
        payload.put("temperature", 0);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", "You are an ASR engine. Return transcript text only."),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "input_audio", "input_audio", Map.of("data", dataUrl)),
                        Map.of("type", "text", "text", "请将音频转写为文字，仅返回识别文本。")
                ))
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(Map.class);

        if (response == null) {
            throw new IllegalArgumentException("Aliyun ASR returned empty response");
        }
        return parseTranscript(response);
    }

    private String parseTranscript(Map<String, Object> response) {
        JsonNode root = objectMapper.valueToTree(response);
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalArgumentException("Aliyun ASR returned no choices");
        }
        JsonNode msg = choices.get(0).path("message");
        JsonNode content = msg.path("content");
        if (content.isTextual()) {
            return content.asText("").trim();
        }
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                if ("text".equals(part.path("type").asText())) {
                    sb.append(part.path("text").asText(""));
                } else if (part.has("text")) {
                    sb.append(part.path("text").asText(""));
                }
            }
            return sb.toString().trim();
        }
        return "";
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "audio/webm";
        }
        String v = contentType.trim().toLowerCase();
        if (!v.startsWith("audio/")) {
            return "audio/webm";
        }
        return v;
    }
}

