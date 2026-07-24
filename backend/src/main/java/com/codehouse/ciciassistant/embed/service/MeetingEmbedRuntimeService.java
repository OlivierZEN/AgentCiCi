package com.codehouse.ciciassistant.embed.service;

import com.codehouse.ciciassistant.ai.service.MeetingMinutesService;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.codehouse.ciciassistant.common.error.ForbiddenException;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionEntity;
import com.codehouse.ciciassistant.embed.domain.MeetingSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingEmbedRuntimeService {

    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private final MeetingSessionRepository sessionRepository;
    private final MeetingMinutesService meetingMinutesService;
    private final BillingUsageMeteringService billingUsageMeteringService;
    private final CloudccMeetingWritebackConnector cloudccWritebackConnector;
    private final ObjectMapper objectMapper;

    public MeetingEmbedRuntimeService(MeetingSessionRepository sessionRepository,
                                      MeetingMinutesService meetingMinutesService,
                                      BillingUsageMeteringService billingUsageMeteringService,
                                      CloudccMeetingWritebackConnector cloudccWritebackConnector,
                                      ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.meetingMinutesService = meetingMinutesService;
        this.billingUsageMeteringService = billingUsageMeteringService;
        this.cloudccWritebackConnector = cloudccWritebackConnector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> createSession(EmbedTokenService.AuthenticatedEmbedToken token) {
        requirePermission(token, "meeting:start");
        MeetingSessionEntity session = sessionRepository.findByTokenNonce(token.nonce())
                .orElseGet(() -> sessionRepository.save(new MeetingSessionEntity(
                        "meet_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24),
                        token.nonce(),
                        token.companyId(),
                        token.userId(),
                        token.externalUserId(),
                        token.source(),
                        token.appCode(),
                        token.objectType(),
                        token.objectId(),
                        token.recordName(),
                        token.customerName(),
                        token.parentOrigin(),
                        toJson(token.context()))));
        return sessionView(session);
    }

    @Transactional
    public Map<String, Object> summarize(EmbedTokenService.AuthenticatedEmbedToken token,
                                         String sessionId,
                                         SummaryCommand command) {
        requirePermission(token, "meeting:summary");
        MeetingSessionEntity session = requireSession(token, sessionId);
        session.markSummarizing();
        try {
            String title = command == null || isBlank(command.title())
                    ? titleFromSession(session)
                    : command.title().trim();
            MeetingMinutesService.MeetingMinutesResult result = meetingMinutesService.summarize(
                    token.companyId(),
                    title,
                    command == null ? List.of() : command.transcript());
            session.markSummaryReady(result.summary());
            billingUsageMeteringService.recordMeetingMinutesRunSafely(new BillingUsageMeteringService.MeetingMinutesMeteringInput(
                    session.getCompanyId(),
                    session.getUserId(),
                    session.getId(),
                    result.modelName(),
                    result.promptTokens(),
                    result.completionTokens(),
                    command == null || command.transcript() == null ? 0 : command.transcript().size(),
                    result.summary() == null ? 0 : result.summary().length(),
                    result.billable(),
                    Instant.now()));
            Map<String, Object> data = sessionView(session);
            data.put("summary", result.summary());
            data.put("skillCode", result.skillCode());
            data.put("skillName", result.skillName());
            data.put("segmentCount", command == null || command.transcript() == null ? 0 : command.transcript().size());
            return data;
        } catch (RuntimeException ex) {
            session.markFailed(ex.getMessage());
            throw ex;
        }
    }

    @Transactional
    public Map<String, Object> writebackPreview(EmbedTokenService.AuthenticatedEmbedToken token, String sessionId) {
        requirePermission(token, "crm:writeback");
        MeetingSessionEntity session = requireSession(token, sessionId);
        if (isBlank(session.getSummaryMarkdown())) {
            throw new IllegalArgumentException("Summary must be generated before writeback preview");
        }
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("items", buildWritebackItems(session));
        session.markWritebackPreview(toJson(preview));
        Map<String, Object> data = sessionView(session);
        data.put("preview", preview);
        return data;
    }

    @Transactional
    public Map<String, Object> writeback(EmbedTokenService.AuthenticatedEmbedToken token,
                                         String sessionId,
                                         WritebackCommand command) {
        requirePermission(token, "crm:writeback");
        MeetingSessionEntity session = requireSession(token, sessionId);
        if (isBlank(session.getWritebackPreviewJson())) {
            throw new IllegalArgumentException("Writeback preview must be generated before confirmation");
        }
        List<String> selectedIds = command == null || command.selectedItemIds() == null
                ? List.of()
                : command.selectedItemIds().stream().filter(item -> !isBlank(item)).distinct().toList();
        if (selectedIds.isEmpty()) {
            throw new IllegalArgumentException("selectedItemIds is required");
        }
        List<Map<String, Object>> selectedItems = selectedPreviewItems(session, selectedIds);
        Map<String, Object> result;
        try {
            result = cloudccWritebackConnector.writeback(session, selectedItems);
            result.put("selectedItemIds", selectedIds);
            session.markWritebackResult(toJson(result), true);
        } catch (CloudccMeetingWritebackConnector.WritebackFailedException ex) {
            result = ex.result();
            result.put("selectedItemIds", selectedIds);
            session.markWritebackResult(toJson(result), false);
        }
        Map<String, Object> data = sessionView(session);
        data.put("writeback", result);
        return data;
    }

    private List<Map<String, Object>> buildWritebackItems(MeetingSessionEntity session) {
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> context = readMap(session.getContextJson());
        WritebackConfig config = WritebackConfig.from(context);
        items.add(summaryNoteItem(session, config));
        int index = 1;
        for (String taskText : extractTaskLines(session.getSummaryMarkdown())) {
            items.add(taskItem(session, config, index++, taskText));
        }
        for (Map<String, Object> suggestion : fieldSuggestions(context)) {
            Map<String, Object> item = fieldSuggestionItem(session, suggestion);
            if (!item.isEmpty()) {
                items.add(item);
            }
        }
        return items;
    }

    private Map<String, Object> summaryNoteItem(MeetingSessionEntity session, WritebackConfig config) {
        String title = "会议纪要 - " + titleFromSession(session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(config.noteTitleField(), title);
        payload.put(config.noteBodyField(), session.getSummaryMarkdown());
        payload.put(config.noteParentField(), session.getObjectId());
        return writebackItem(
                "summary-note",
                "note",
                "写入会议纪要",
                session,
                summaryText(session.getSummaryMarkdown(), 120),
                "insert",
                config.noteObjectApiName(),
                payload);
    }

    private Map<String, Object> taskItem(MeetingSessionEntity session, WritebackConfig config, int index, String taskText) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(config.taskSubjectField(), taskText);
        payload.put(config.taskDescriptionField(), "来自会议纪要 " + session.getId() + "\n" + titleFromSession(session));
        payload.put(config.taskParentField(), session.getObjectId());
        payload.put(config.taskDueDateField(), LocalDate.now().plusDays(7).toString());
        payload.put("status", "未开始");
        payload.put("priority", "普通");
        return writebackItem(
                "task-" + index,
                "task",
                "创建跟进任务",
                session,
                taskText,
                "insert",
                config.taskObjectApiName(),
                payload);
    }

    private Map<String, Object> fieldSuggestionItem(MeetingSessionEntity session, Map<String, Object> suggestion) {
        String fieldApiName = stringValue(suggestion.get("fieldApiName"));
        String proposedValue = stringValue(suggestion.get("proposedValue"));
        if (fieldApiName.isBlank() || proposedValue.isBlank()) {
            return Map.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", session.getObjectId());
        payload.put(fieldApiName, proposedValue);
        Map<String, Object> item = writebackItem(
                "field-" + fieldApiName.replaceAll("[^A-Za-z0-9_]+", "-"),
                "field_suggestion",
                stringValue(suggestion.get("label")).isBlank() ? "更新字段建议" : stringValue(suggestion.get("label")),
                session,
                proposedValue,
                "update",
                session.getObjectType(),
                payload);
        item.put("fieldApiName", fieldApiName);
        item.put("originalValue", stringValue(suggestion.get("originalValue")));
        item.put("proposedValue", proposedValue);
        return item;
    }

    private Map<String, Object> writebackItem(String id,
                                              String type,
                                              String label,
                                              MeetingSessionEntity session,
                                              String content,
                                              String serviceName,
                                              String objectApiName,
                                              Map<String, Object> payload) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("type", type);
        item.put("label", label);
        item.put("target", Map.of(
                "source", session.getSource(),
                "objectType", session.getObjectType(),
                "objectId", session.getObjectId()
        ));
        item.put("content", content == null ? "" : content);
        item.put("writeback", Map.of(
                "serviceName", serviceName,
                "objectApiName", objectApiName,
                "data", List.of(payload)
        ));
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> selectedPreviewItems(MeetingSessionEntity session, List<String> selectedIds) {
        Map<String, Object> preview = readMap(session.getWritebackPreviewJson());
        Object rawItems = preview.get("items");
        if (!(rawItems instanceof List<?> items)) {
            throw new IllegalArgumentException("Writeback preview is invalid");
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Object raw : items) {
            if (raw instanceof Map<?, ?> map) {
                Map<String, Object> item = (Map<String, Object>) map;
                byId.put(stringValue(item.get("id")), item);
            }
        }
        List<Map<String, Object>> selected = new ArrayList<>();
        for (String id : selectedIds) {
            Map<String, Object> item = byId.get(id);
            if (item == null) {
                throw new ForbiddenException("Selected writeback item is not in the generated preview: " + id);
            }
            selected.add(item);
        }
        return selected;
    }

    private MeetingSessionEntity requireSession(EmbedTokenService.AuthenticatedEmbedToken token, String sessionId) {
        MeetingSessionEntity session = sessionRepository.findByIdAndCompanyId(sessionId, token.companyId())
                .orElseThrow(() -> new IllegalArgumentException("Meeting session not found"));
        if (!session.getTokenNonce().equals(token.nonce())
                || !session.getAppCode().equals(token.appCode())
                || !session.getObjectType().equals(token.objectType())
                || !session.getObjectId().equals(token.objectId())
                || !session.getParentOrigin().equals(token.parentOrigin())) {
            throw new ForbiddenException("Embed token does not match this meeting session");
        }
        return session;
    }

    private void requirePermission(EmbedTokenService.AuthenticatedEmbedToken token, String permission) {
        if (!token.can(permission)) {
            throw new ForbiddenException("Embed token is missing permission: " + permission);
        }
    }

    private Map<String, Object> sessionView(MeetingSessionEntity session) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sessionId", session.getId());
        data.put("companyId", session.getCompanyId());
        data.put("appCode", session.getAppCode());
        data.put("source", session.getSource());
        data.put("objectType", session.getObjectType());
        data.put("objectId", session.getObjectId());
        data.put("recordName", session.getRecordName() == null ? "" : session.getRecordName());
        data.put("customerName", session.getCustomerName() == null ? "" : session.getCustomerName());
        data.put("parentOrigin", session.getParentOrigin());
        data.put("status", session.getStatus());
        data.put("context", readMap(session.getContextJson()));
        data.put("summary", session.getSummaryMarkdown() == null ? "" : session.getSummaryMarkdown());
        data.put("writebackPreview", readMap(session.getWritebackPreviewJson()));
        data.put("writebackResult", readMap(session.getWritebackResultJson()));
        data.put("traceId", session.getTraceId() == null ? "" : session.getTraceId());
        data.put("createdAt", session.getCreatedAt().toString());
        data.put("updatedAt", session.getUpdatedAt().toString());
        return data;
    }

    private String titleFromSession(MeetingSessionEntity session) {
        if (!isBlank(session.getRecordName())) {
            return session.getRecordName();
        }
        if (!isBlank(session.getCustomerName())) {
            return session.getCustomerName() + " 会议";
        }
        return "会议纪要";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize meeting session payload", ex);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_REF);
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fieldSuggestions(Map<String, Object> context) {
        Object raw = context.get("fieldSuggestions");
        if (!(raw instanceof List<?> list)) {
            raw = context.get("crmFieldSuggestions");
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    private List<String> extractTaskLines(String summaryMarkdown) {
        if (isBlank(summaryMarkdown)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        boolean inActionSection = false;
        for (String rawLine : summaryMarkdown.split("\\R")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            String normalized = line.toLowerCase();
            if (line.startsWith("#")) {
                inActionSection = line.contains("待办")
                        || line.contains("行动")
                        || line.contains("下一步")
                        || normalized.contains("todo")
                        || normalized.contains("action");
                continue;
            }
            if (!inActionSection && !(line.contains("待办") || line.contains("跟进") || line.contains("下一步"))) {
                continue;
            }
            String cleaned = line
                    .replaceFirst("^[-*+\\d.、\\s\\[\\]xX]+", "")
                    .replaceFirst("^(待办|行动项|下一步|跟进事项)[:：\\s-]*", "")
                    .trim();
            if (cleaned.length() >= 4 && !out.contains(cleaned)) {
                out.add(cleaned.length() > 96 ? cleaned.substring(0, 96) : cleaned);
            }
            if (out.size() >= 3) {
                break;
            }
        }
        return List.copyOf(out);
    }

    private String summaryText(String value, int limit) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    public record SummaryCommand(
            String title,
            List<MeetingMinutesService.TranscriptSegment> transcript
    ) {
    }

    public record WritebackCommand(List<String> selectedItemIds) {
    }

    private record WritebackConfig(String noteObjectApiName,
                                   String noteTitleField,
                                   String noteBodyField,
                                   String noteParentField,
                                   String taskObjectApiName,
                                   String taskSubjectField,
                                   String taskDescriptionField,
                                   String taskParentField,
                                   String taskDueDateField) {

        @SuppressWarnings("unchecked")
        static WritebackConfig from(Map<String, Object> context) {
            Map<String, Object> raw = context == null ? Map.of() : context;
            Object nested = raw.get("writeback");
            if (nested instanceof Map<?, ?> map) {
                raw = (Map<String, Object>) map;
            }
            return new WritebackConfig(
                    val(raw, "noteObjectApiName", "Note"),
                    val(raw, "noteTitleField", "name"),
                    val(raw, "noteBodyField", "body"),
                    val(raw, "noteParentField", "parentid"),
                    val(raw, "taskObjectApiName", "Task"),
                    val(raw, "taskSubjectField", "subject"),
                    val(raw, "taskDescriptionField", "description"),
                    val(raw, "taskParentField", "whatid"),
                    val(raw, "taskDueDateField", "activitydate"));
        }

        private static String val(Map<String, Object> map, String key, String fallback) {
            Object value = map.get(key);
            String text = value == null ? "" : String.valueOf(value).trim();
            return text.isBlank() ? fallback : text;
        }
    }
}
