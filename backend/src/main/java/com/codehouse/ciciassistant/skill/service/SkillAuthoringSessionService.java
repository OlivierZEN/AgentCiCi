package com.codehouse.ciciassistant.skill.service;

import com.codehouse.ciciassistant.skill.domain.SkillAuthoringSessionEntity;
import com.codehouse.ciciassistant.skill.domain.SkillAuthoringSessionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SkillAuthoringSessionService {

    private final SkillAuthoringSessionRepository repository;
    private final ObjectMapper objectMapper;

    public SkillAuthoringSessionService(SkillAuthoringSessionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public String createActiveSession(String companyId, String generateSourceText, BuiltinSkillCreatorService.GeneratedSkillDraft draft) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String json = writeState(generateSourceText, null, draft);
        repository.save(new SkillAuthoringSessionEntity(id, companyId, SkillAuthoringSessionEntity.STATUS_ACTIVE, json, now, now));
        return id;
    }

    @Transactional
    public void updateActiveSession(String companyId, String sessionId, String refineSourceText, BuiltinSkillCreatorService.GeneratedSkillDraft draft) {
        SkillAuthoringSessionEntity entity = requireActive(companyId, sessionId);
        String priorGenerate = readGenerateSource(entity.getStateJson());
        String json = writeState(priorGenerate, refineSourceText, draft);
        entity.setStateJson(json);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional
    public void completeSession(String companyId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        SkillAuthoringSessionEntity entity = repository.findByIdAndCompanyId(sessionId.trim(), companyId).orElse(null);
        if (entity == null) {
            return;
        }
        if (!SkillAuthoringSessionEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            return;
        }
        entity.setStatus(SkillAuthoringSessionEntity.STATUS_COMPLETED);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    public void assertSessionActive(String companyId, String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        requireActive(companyId, sessionId);
    }

    private SkillAuthoringSessionEntity requireActive(String companyId, String sessionId) {
        SkillAuthoringSessionEntity entity = repository.findByIdAndCompanyId(sessionId.trim(), companyId)
                .orElseThrow(() -> new IllegalArgumentException("authoring session not found"));
        if (!SkillAuthoringSessionEntity.STATUS_ACTIVE.equals(entity.getStatus())) {
            throw new IllegalArgumentException("authoring session is not active");
        }
        return entity;
    }

    private String readGenerateSource(String json) {
        try {
            Map<String, Object> root = objectMapper.readValue(json, new TypeReference<>() {
            });
            Object v = root.get("lastGenerateSourceText");
            return v == null ? "" : String.valueOf(v);
        } catch (Exception ex) {
            return "";
        }
    }

    private String writeState(String generateSource, String refineSource, BuiltinSkillCreatorService.GeneratedSkillDraft draft) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("lastGenerateSourceText", generateSource == null ? "" : generateSource);
            root.put("lastRefineSourceText", refineSource == null ? "" : refineSource);
            root.put("skillSpec", objectMapper.convertValue(draft, new TypeReference<Map<String, Object>>() {
            }));
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize authoring session", ex);
        }
    }
}
