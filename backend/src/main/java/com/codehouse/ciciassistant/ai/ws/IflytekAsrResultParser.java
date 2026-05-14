package com.codehouse.ciciassistant.ai.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

final class IflytekAsrResultParser {

    private IflytekAsrResultParser() {
    }

    static JsonNode parsePayload(ObjectMapper objectMapper, JsonNode root) throws Exception {
        JsonNode data = root.path("data");
        if (data.isTextual()) {
            return objectMapper.readTree(data.asText());
        }
        if (data.isObject() && (data.has("cn") || data.has("result") || data.has("st"))) {
            return data;
        }
        return root;
    }

    static ExtractionResult extractPieces(JsonNode payload, String activeSpeakerId) {
        List<TranscriptPiece> pieces = new ArrayList<>();
        String active = normalizeSpeakerId(activeSpeakerId);
        active = collectRtPieces(payload.path("cn").path("st").path("rt"), active, pieces);
        active = collectWsPieces(payload.path("result").path("ws"), payload.path("result"), active, pieces);
        active = collectWsPieces(payload.path("data").path("result").path("ws"), payload.path("data").path("result"), active, pieces);
        active = collectRtPieces(payload.path("st").path("rt"), active, pieces);
        List<TranscriptPiece> nonBlankPieces = pieces.stream()
                .filter(piece -> !piece.text().isBlank())
                .toList();
        return new ExtractionResult(nonBlankPieces, active);
    }

    static boolean isFinal(JsonNode payload) {
        JsonNode status = payload.path("data").path("status");
        if (status.isInt() && status.asInt() == 2) {
            return true;
        }
        if (payload.path("data").path("result").path("ls").asBoolean(false)) {
            return true;
        }
        String type = payload.path("cn").path("st").path("type").asText("");
        return "0".equals(type);
    }

    static String speakerDisplayName(String speakerId) {
        String value = normalizeSpeakerId(speakerId);
        if (value.isBlank()) {
            return "发言人 1";
        }
        if (value.matches("\\d+")) {
            return "发言人 " + ("0".equals(value) ? "1" : value);
        }
        return "发言人 " + value;
    }

    private static String collectRtPieces(JsonNode rt, String activeSpeakerId, List<TranscriptPiece> pieces) {
        if (!rt.isArray()) {
            return activeSpeakerId;
        }
        String active = activeSpeakerId;
        for (JsonNode item : rt) {
            String itemMarker = extractDirectSpeakerId(item);
            if (!itemMarker.isBlank() && !"0".equals(itemMarker)) {
                active = itemMarker;
            }
            active = collectWsPieces(item.path("ws"), item, active, pieces);
        }
        return active;
    }

    private static String collectWsPieces(JsonNode ws, JsonNode fallbackSpeakerNode, String activeSpeakerId, List<TranscriptPiece> pieces) {
        if (!ws.isArray()) {
            return activeSpeakerId;
        }
        String active = activeSpeakerId;
        String currentSpeaker = active;
        StringBuilder currentText = new StringBuilder();
        boolean sawExplicitMarker = false;
        for (JsonNode item : ws) {
            JsonNode cw = item.path("cw");
            if (!cw.isArray()) {
                continue;
            }
            for (JsonNode word : cw) {
                String marker = extractDirectSpeakerId(word);
                if (marker.isBlank()) {
                    marker = extractDirectSpeakerId(item);
                }
                if (marker.isBlank()) {
                    marker = extractDirectSpeakerId(fallbackSpeakerNode);
                }
                if (!marker.isBlank() && !"0".equals(marker) && !marker.equals(currentSpeaker)) {
                    if (sawExplicitMarker || currentText.isEmpty() || !currentSpeaker.equals(active)) {
                        flushPiece(currentText, currentSpeaker, pieces);
                    }
                    active = marker;
                    currentSpeaker = marker;
                    sawExplicitMarker = true;
                } else if (!marker.isBlank() && !"0".equals(marker)) {
                    active = marker;
                    currentSpeaker = marker;
                    sawExplicitMarker = true;
                } else if (currentSpeaker.isBlank() && !active.isBlank()) {
                    currentSpeaker = active;
                }
                String text = word.path("w").asText("");
                if (!text.isBlank()) {
                    currentText.append(text);
                }
            }
        }
        flushPiece(currentText, currentSpeaker, pieces);
        return active;
    }

    private static void flushPiece(StringBuilder text, String speakerId, List<TranscriptPiece> pieces) {
        String value = text.toString().trim();
        if (value.isBlank()) {
            return;
        }
        pieces.add(new TranscriptPiece(value, normalizeSpeakerId(speakerId)));
        text.setLength(0);
    }

    private static String extractDirectSpeakerId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        String direct = firstNonBlank(node.path("rl").asText(""), node.path("role").asText(""));
        direct = firstNonBlank(direct, node.path("role_id").asText(""));
        direct = firstNonBlank(direct, node.path("speaker").asText(""));
        direct = firstNonBlank(direct, node.path("speaker_id").asText(""));
        direct = firstNonBlank(direct, node.path("spk").asText(""));
        direct = firstNonBlank(direct, node.path("spkid").asText(""));
        return normalizeSpeakerId(direct);
    }

    private static String normalizeSpeakerId(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.toLowerCase().startsWith("speaker")) {
            value = value.replaceAll("(?i)speaker", "").trim();
        }
        return value.replaceAll("[^0-9A-Za-z_-]", "");
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b == null ? "" : b);
    }

    record ExtractionResult(List<TranscriptPiece> pieces, String activeSpeakerId) {
    }

    record TranscriptPiece(String text, String speakerId) {
    }
}
