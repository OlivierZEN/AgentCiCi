package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.ai.domain.ChatSessionStateEntity;
import com.codehouse.ciciassistant.ai.domain.ChatSessionStateRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionStateService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final int MAX_SUMMARY_LENGTH = 480;
    private static final Pattern CAMPAIGN_NAME_PATTERN = Pattern.compile("([\\p{IsHan}A-Za-z0-9_-]{2,30})(活动|campaign)");

    private final ChatSessionStateRepository repository;
    private final ObjectMapper objectMapper;

    public ChatSessionStateService(ChatSessionStateRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Optional<ChatSessionStateEntity> get(String orgId, String sessionId) {
        return repository.findBySessionIdAndOrgId(sessionId, orgId);
    }

    public String buildPromptBlock(ChatSessionStateEntity state) {
        Map<String, Object> payload = parseState(state.getStateJson());
        String confirmed = listToLine(payload.get("confirmed_actions"));
        String deferred = listToLine(payload.get("deferred_actions"));
        String missing = listToLine(payload.get("missing_fields"));
        if (missing.isBlank()) {
            missing = "无";
        }
        String currentObject = valueAsText(payload.get("current_object_name"));
        if (currentObject.isBlank()) {
            currentObject = valueAsText(payload.get("current_object_id"));
        }
        String targetSegment = valueAsText(payload.get("target_segment_summary"));
        String nextAction = valueAsText(payload.get("next_action"));
        String summary = state.getSummary() == null ? "" : state.getSummary();

        StringBuilder block = new StringBuilder("## 当前会话执行状态\n");
        if (!summary.isBlank()) {
            block.append("- 状态摘要：").append(summary).append("\n");
        }
        block.append("- 已确认动作：").append(confirmed.isBlank() ? "无" : confirmed).append("\n");
        block.append("- 暂缓动作：").append(deferred.isBlank() ? "无" : deferred).append("\n");
        block.append("- 当前对象：").append(currentObject.isBlank() ? "未确定" : currentObject).append("\n");
        block.append("- 当前目标范围：").append(targetSegment.isBlank() ? "未确定" : targetSegment).append("\n");
        block.append("- 缺失字段：").append(missing).append("\n");
        if (!nextAction.isBlank()) {
            block.append("- 下一步动作：").append(nextAction).append("\n");
        }
        block.append("\n规则：\n")
                .append("- 已确认的信息不得重复追问。\n")
                .append("- 只有 missing_fields 非空或状态冲突时才允许追问。\n")
                .append("- 追问时只问缺失项，不要回退到重新澄清整段需求。");
        return block.toString();
    }

    public void mergeUserTurn(String orgId, String sessionId, String agentId, String question) {
        Map<String, Object> state = loadState(orgId, sessionId);
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        if (normalized.contains("先不要") || normalized.contains("暂不") || normalized.contains("不要发")) {
            appendDistinct(state, "deferred_actions", "hold_action");
            appendDistinct(state, "deferred_actions", "send_email");
        }
        if (normalized.contains("先") || normalized.contains("继续") || normalized.contains("按刚才")) {
            appendDistinct(state, "confirmed_actions", "continue_current_plan");
        }
        if (normalized.contains("添加名单") || normalized.contains("加名单")) {
            appendDistinct(state, "confirmed_actions", "add_members");
            state.put("next_action", "call add_campaign_member");
        }
        if (normalized.contains("不要再重复问") || normalized.contains("别再问")) {
            state.put("no_repeat_questions", true);
            appendDistinct(state, "confirmed_actions", "no_repeat_confirmed_info");
            removeDistinct(state, "missing_fields", "target_segment");
        }
        enrichStateFromUserQuestion(state, question);
        updateMissingFields(state);
        upsert(orgId, sessionId, agentId, state, deriveSummary(state));
    }

    public void mergeToolResult(String orgId, String sessionId, String agentId, String toolName, String toolResult) {
        Map<String, Object> state = loadState(orgId, sessionId);
        String normalizedTool = toolName == null ? "" : toolName.toLowerCase(Locale.ROOT);
        if (normalizedTool.contains("lead")) {
            Integer leadCount = extractFirstInteger(toolResult);
            if (leadCount != null) {
                state.put("candidate_lead_count", leadCount);
            }
            appendDistinct(state, "confirmed_actions", "inspect_leads");
        }
        if (normalizedTool.contains("campaign") && normalizedTool.contains("member")) {
            appendDistinct(state, "confirmed_actions", "add_members");
            state.put("next_action", "evaluate_next_step");
            removeDistinct(state, "missing_fields", "target_segment");
        }
        if (normalizedTool.contains("email") || normalizedTool.contains("mail")) {
            appendDistinct(state, "confirmed_actions", "send_email");
            removeDistinct(state, "deferred_actions", "hold_action");
            removeDistinct(state, "deferred_actions", "send_email");
        }
        updateMissingFields(state);
        upsert(orgId, sessionId, agentId, state, deriveSummary(state));
    }

    private void enrichStateFromUserQuestion(Map<String, Object> state, String question) {
        if (question == null || question.isBlank()) {
            return;
        }
        String text = question.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        if ((normalized.contains("活动") || normalized.contains("campaign"))
                && !normalized.contains("不要再重复问")) {
            state.put("current_object_type", "campaign");
            String campaignName = extractCampaignName(text);
            if (!campaignName.isBlank()) {
                state.put("current_object_name", campaignName);
            }
        }
        if (text.contains("范围")) {
            String segment = extractSegmentSummary(text);
            if (!segment.isBlank()) {
                state.put("target_segment_summary", segment);
            }
        }
        if (normalized.contains("继续上一步") || normalized.contains("继续")) {
            state.put("next_action", "execute_confirmed_action");
        }
    }

    private void updateMissingFields(Map<String, Object> state) {
        String targetSegment = valueAsText(state.get("target_segment_summary"));
        List<String> confirmed = toMutableStringList(state.get("confirmed_actions"));
        if (confirmed.contains("add_members") && targetSegment.isBlank()) {
            appendDistinct(state, "missing_fields", "target_segment");
            state.put("next_action", "ask_for_target_segment");
        }
        if (!targetSegment.isBlank()) {
            removeDistinct(state, "missing_fields", "target_segment");
        }
    }

    private static String extractCampaignName(String text) {
        Matcher matcher = CAMPAIGN_NAME_PATTERN.matcher(text);
        if (matcher.find()) {
            String prefix = matcher.group(1);
            String suffix = matcher.group(2);
            return (prefix + suffix).trim();
        }
        return "";
    }

    private static String extractSegmentSummary(String text) {
        int idx = text.indexOf("范围");
        if (idx < 0) {
            return "";
        }
        String tail = text.substring(idx);
        int sepIdx = Math.max(tail.indexOf("是"), tail.indexOf("："));
        if (sepIdx >= 0 && sepIdx + 1 < tail.length()) {
            String candidate = tail.substring(sepIdx + 1).trim();
            candidate = candidate.replace("，先不要发邮件", "")
                    .replace("，不要发邮件", "")
                    .replace("。", "")
                    .trim();
            return clip(candidate, 120);
        }
        return "";
    }

    private void upsert(String orgId, String sessionId, String agentId, Map<String, Object> state, String summary) {
        Instant now = Instant.now();
        String safeSummary = summary == null || summary.isBlank() ? "会话进行中" : clip(summary, MAX_SUMMARY_LENGTH);
        String stateJson = stringifyState(state);
        ChatSessionStateEntity entity = repository.findBySessionIdAndOrgId(sessionId, orgId)
                .orElseGet(() -> new ChatSessionStateEntity(sessionId, orgId, agentId, safeSummary, stateJson, now));
        entity.setAgentId(agentId);
        entity.setSummary(safeSummary);
        entity.setStateJson(stateJson);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    private Map<String, Object> loadState(String orgId, String sessionId) {
        return repository.findBySessionIdAndOrgId(sessionId, orgId)
                .map(item -> parseState(item.getStateJson()))
                .orElseGet(() -> {
                    Map<String, Object> state = new LinkedHashMap<>();
                    state.put("confirmed_actions", new ArrayList<String>());
                    state.put("deferred_actions", new ArrayList<String>());
                    state.put("missing_fields", new ArrayList<String>());
                    return state;
                });
    }

    private Map<String, Object> parseState(String stateJson) {
        if (stateJson == null || stateJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(stateJson, MAP_TYPE);
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private String stringifyState(Map<String, Object> state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (Exception ex) {
            return "{\"status\":\"encode_failed\"}";
        }
    }

    private static void appendDistinct(Map<String, Object> state, String key, String value) {
        List<String> items = toMutableStringList(state.get(key));
        if (!items.contains(value)) {
            items.add(value);
        }
        state.put(key, items);
    }

    private static void removeDistinct(Map<String, Object> state, String key, String value) {
        List<String> items = toMutableStringList(state.get(key));
        items.remove(value);
        state.put(key, items);
    }

    private static List<String> toMutableStringList(Object value) {
        List<String> items = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null) {
                    items.add(item.toString());
                }
            }
        }
        return items;
    }

    private static String listToLine(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (Object item : list) {
            if (item != null && !item.toString().isBlank()) {
                items.add(item.toString());
            }
        }
        return String.join(" / ", items);
    }

    private static String valueAsText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static Integer extractFirstInteger(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (Character.isDigit(ch)) {
                digits.append(ch);
            } else if (!digits.isEmpty()) {
                break;
            }
        }
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String deriveSummary(Map<String, Object> state) {
        String confirmed = listToLine(state.get("confirmed_actions"));
        String deferred = listToLine(state.get("deferred_actions"));
        if (!confirmed.isBlank() && !deferred.isBlank()) {
            return "已确认: " + confirmed + "；暂缓: " + deferred;
        }
        if (!confirmed.isBlank()) {
            return "已确认: " + confirmed;
        }
        if (!deferred.isBlank()) {
            return "暂缓: " + deferred;
        }
        return "会话进行中";
    }

    private static String clip(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }
}
