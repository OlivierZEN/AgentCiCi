package com.codehouse.ciciassistant.kb.service;

import com.codehouse.ciciassistant.kb.domain.KbAnnotationSuggestionEntity;
import com.codehouse.ciciassistant.kb.domain.KbAnnotationSuggestionRepository;
import com.codehouse.ciciassistant.kb.domain.KbChunkAnnotationEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkAnnotationRepository;
import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDataSourceEntity;
import com.codehouse.ciciassistant.kb.domain.KbDataSourceRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KbMetadataFieldEntity;
import com.codehouse.ciciassistant.kb.domain.KbMetadataFieldRepository;
import com.codehouse.ciciassistant.kb.domain.KbQualityIssueEntity;
import com.codehouse.ciciassistant.kb.domain.KbQualityIssueRepository;
import com.codehouse.ciciassistant.kb.domain.KbQualityRuleEntity;
import com.codehouse.ciciassistant.kb.domain.KbQualityRuleRepository;
import com.codehouse.ciciassistant.kb.domain.KbQualityRunEntity;
import com.codehouse.ciciassistant.kb.domain.KbQualityRunRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KbDataQualityService {

    private static final Pattern URL_ONLY = Pattern.compile("^(https?://|www\\.)\\S+$", Pattern.CASE_INSENSITIVE);

    private final KnowledgeBaseRepository kbRepository;
    private final KbChunkRepository chunkRepository;
    private final KbDataSourceRepository dataSourceRepository;
    private final KbDocumentRepository documentRepository;
    private final KbMetadataFieldRepository metadataFieldRepository;
    private final KbDocumentMetadataRepository documentMetadataRepository;
    private final KbQualityRuleRepository ruleRepository;
    private final KbQualityRunRepository runRepository;
    private final KbQualityIssueRepository issueRepository;
    private final KbAnnotationSuggestionRepository suggestionRepository;
    private final KbChunkAnnotationRepository chunkAnnotationRepository;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AuditService auditService;

    public KbDataQualityService(KnowledgeBaseRepository kbRepository,
                                KbChunkRepository chunkRepository,
                                KbDataSourceRepository dataSourceRepository,
                                KbDocumentRepository documentRepository,
                                KbMetadataFieldRepository metadataFieldRepository,
                                KbDocumentMetadataRepository documentMetadataRepository,
                                KbQualityRuleRepository ruleRepository,
                                KbQualityRunRepository runRepository,
                                KbQualityIssueRepository issueRepository,
                                KbAnnotationSuggestionRepository suggestionRepository,
                                KbChunkAnnotationRepository chunkAnnotationRepository,
                                KnowledgeBaseService knowledgeBaseService,
                                AuditService auditService) {
        this.kbRepository = kbRepository;
        this.chunkRepository = chunkRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.documentRepository = documentRepository;
        this.metadataFieldRepository = metadataFieldRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
        this.issueRepository = issueRepository;
        this.suggestionRepository = suggestionRepository;
        this.chunkAnnotationRepository = chunkAnnotationRepository;
        this.knowledgeBaseService = knowledgeBaseService;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> listSources(String orgId) {
        Map<Long, KnowledgeBaseEntity> kbById = kbRepository.findByOrgIdAndStatusNotOrderByIdDesc(orgId, "DELETED")
                .stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, item -> item, (left, right) -> left));
        ArrayList<Map<String, Object>> rows = new ArrayList<>();
        for (KnowledgeBaseEntity kb : kbById.values().stream().sorted(Comparator.comparing(KnowledgeBaseEntity::getId).reversed()).toList()) {
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourceKey", "kb:" + kb.getId());
            row.put("sourceKind", "KNOWLEDGE_BASE");
            row.put("sourceType", "KNOWLEDGE_BASE");
            row.put("knowledgeBaseId", kb.getId());
            row.put("name", kb.getName());
            row.put("status", kb.getStatus());
            row.put("lastSyncedAt", "");
            rows.add(row);
        }
        for (KbDataSourceEntity source : dataSourceRepository.findByOrgIdOrderByIdDesc(orgId)) {
            KnowledgeBaseEntity kb = kbById.get(source.getKnowledgeBaseId());
            LinkedHashMap<String, Object> row = new LinkedHashMap<>();
            row.put("sourceKey", "kb-source:" + source.getId());
            row.put("sourceKind", "CONNECTOR");
            row.put("sourceType", source.getSourceType());
            row.put("knowledgeBaseId", source.getKnowledgeBaseId());
            row.put("knowledgeBaseName", kb == null ? "" : kb.getName());
            row.put("name", source.getName());
            row.put("status", source.getStatus());
            row.put("lastSyncedAt", source.getLastSyncedAt() == null ? "" : source.getLastSyncedAt().toString());
            rows.add(row);
        }
        return rows;
    }

    @Transactional
    public Map<String, Object> startScan(String orgId, Long kbId, String actorUserId, QualityScanCommand command) {
        requireKb(orgId, kbId);
        KbQualityRunEntity run = runRepository.save(new KbQualityRunEntity(
                orgId,
                kbId,
                command == null || command.triggerType() == null || command.triggerType().isBlank()
                        ? "MANUAL"
                        : command.triggerType().trim().toUpperCase(Locale.ROOT),
                actorUserId));
        try {
            List<KbChunkEntity> chunks = activeChunks(orgId, kbId);
            List<KbQualityRuleEntity> rules = ruleRepository.findByOrgIdAndKnowledgeBaseIdAndEnabledTrueOrderByIdAsc(orgId, kbId);
            int duplicates = scanDuplicates(orgId, kbId, run.getId(), chunks);
            int invalids = scanInvalidChunks(orgId, kbId, run.getId(), chunks);
            int regex = scanRegexRules(orgId, kbId, run.getId(), chunks, rules);
            run.complete(chunks.size(), duplicates, invalids, regex);
            auditService.log(orgId, actorUserId, "kb.quality.scan",
                    "kbId=" + kbId + ",runId=" + run.getId() + ",issues=" + run.getTotalIssueCount());
        } catch (RuntimeException ex) {
            run.fail(ex.getMessage());
            throw ex;
        } finally {
            runRepository.save(run);
        }
        return runPayload(run);
    }

    public List<Map<String, Object>> listRuns(String orgId, Long kbId) {
        requireKb(orgId, kbId);
        return runRepository.findTop20ByOrgIdAndKnowledgeBaseIdOrderByCreatedAtDesc(orgId, kbId).stream()
                .map(this::runPayload)
                .toList();
    }

    public List<Map<String, Object>> listIssues(String orgId, Long kbId, String status) {
        requireKb(orgId, kbId);
        String normalized = status == null || status.isBlank() ? "OPEN" : status.trim().toUpperCase(Locale.ROOT);
        return issueRepository.findTop100ByOrgIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(orgId, kbId, normalized)
                .stream()
                .map(this::issuePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> markIssue(String orgId, Long issueId, String actorUserId, String status) {
        KbQualityIssueEntity issue = issueRepository.findByIdAndOrgId(issueId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Quality issue not found"));
        String normalized = switch ((status == null ? "" : status).trim().toUpperCase(Locale.ROOT)) {
            case "IGNORED" -> "IGNORED";
            case "RESOLVED" -> "RESOLVED";
            default -> throw new IllegalArgumentException("Unsupported issue status");
        };
        issue.mark(normalized, actorUserId);
        issueRepository.save(issue);
        auditService.log(orgId, actorUserId, "kb.quality.issue." + normalized.toLowerCase(Locale.ROOT),
                "kbId=" + issue.getKnowledgeBaseId() + ",issueId=" + issue.getId());
        return issuePayload(issue);
    }

    @Transactional
    public Map<String, Object> createRule(String orgId, Long kbId, String actorUserId, QualityRuleCommand command) {
        requireKb(orgId, kbId);
        String name = required(command == null ? null : command.name(), "Rule name is required");
        String type = normalizeRuleType(command.ruleType());
        String pattern = command.pattern() == null ? "" : command.pattern().trim();
        validatePatternIfNeeded(type, pattern);
        KbQualityRuleEntity rule = ruleRepository.save(new KbQualityRuleEntity(
                orgId,
                kbId,
                name,
                type,
                pattern,
                command.replacement(),
                command.enabled() == null || command.enabled(),
                actorUserId));
        auditService.log(orgId, actorUserId, "kb.quality.rule.create", "kbId=" + kbId + ",ruleId=" + rule.getId());
        return rulePayload(rule);
    }

    public List<Map<String, Object>> listRules(String orgId, Long kbId) {
        requireKb(orgId, kbId);
        return ruleRepository.findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(orgId, kbId).stream()
                .map(this::rulePayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> updateRule(String orgId, Long ruleId, String actorUserId, QualityRuleCommand command) {
        KbQualityRuleEntity rule = ruleRepository.findByIdAndOrgId(ruleId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Quality rule not found"));
        String name = required(command == null ? null : command.name(), "Rule name is required");
        String type = normalizeRuleType(command.ruleType());
        String pattern = command.pattern() == null ? "" : command.pattern().trim();
        validatePatternIfNeeded(type, pattern);
        rule.update(name, type, pattern, command.replacement(), command.enabled() == null || command.enabled());
        ruleRepository.save(rule);
        auditService.log(orgId, actorUserId, "kb.quality.rule.update",
                "kbId=" + rule.getKnowledgeBaseId() + ",ruleId=" + rule.getId());
        return rulePayload(rule);
    }

    public Map<String, Object> previewRule(String orgId, Long ruleId, QualityApplyCommand command) {
        KbQualityRuleEntity rule = ruleRepository.findByIdAndOrgId(ruleId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Quality rule not found"));
        List<KbChunkEntity> chunks = targetChunks(orgId, rule.getKnowledgeBaseId(), command);
        int limit = sanitizeLimit(command == null ? null : command.limit(), 50);
        List<Map<String, Object>> items = new ArrayList<>();
        for (KbChunkEntity chunk : chunks) {
            String after = applyRuleToText(rule, chunk.getContent());
            if (!Objects.equals(chunk.getContent(), after)) {
                items.add(Map.of(
                        "chunkId", chunk.getId(),
                        "documentId", chunk.getDocumentId() == null ? "" : chunk.getDocumentId(),
                        "contentHash", chunk.getContentHash() == null ? "" : chunk.getContentHash(),
                        "before", chunk.getContent(),
                        "after", after,
                        "changed", true));
                if (items.size() >= limit) {
                    break;
                }
            }
        }
        return Map.of(
                "rule", rulePayload(rule),
                "previewCount", items.size(),
                "items", items);
    }

    @Transactional
    public Map<String, Object> applyRule(String orgId, Long ruleId, String actorUserId, QualityApplyCommand command) {
        KbQualityRuleEntity rule = ruleRepository.findByIdAndOrgId(ruleId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Quality rule not found"));
        List<KbChunkEntity> chunks = targetChunks(orgId, rule.getKnowledgeBaseId(), command);
        Map<Long, String> expectedHashes = command == null || command.expectedContentHashes() == null
                ? Map.of()
                : command.expectedContentHashes();
        int updated = 0;
        int skipped = 0;
        List<Long> updatedChunkIds = new ArrayList<>();
        for (KbChunkEntity chunk : chunks) {
            String expected = expectedHashes.get(chunk.getId());
            if (expected != null && !expected.isBlank() && !expected.equals(chunk.getContentHash())) {
                throw new IllegalStateException("Chunk content changed since preview: " + chunk.getId());
            }
            String after = applyRuleToText(rule, chunk.getContent()).trim();
            if (after.isBlank() || Objects.equals(after, chunk.getContent())) {
                skipped++;
                continue;
            }
            knowledgeBaseService.updateChunk(orgId, chunk.getId(), after);
            updated++;
            updatedChunkIds.add(chunk.getId());
        }
        if (command != null && command.issueIds() != null && !command.issueIds().isEmpty()) {
            for (KbQualityIssueEntity issue : issueRepository.findByIdInAndOrgIdAndKnowledgeBaseId(
                    command.issueIds(), orgId, rule.getKnowledgeBaseId())) {
                if (updatedChunkIds.contains(issue.getChunkId())) {
                    issue.mark("APPLIED", actorUserId);
                    issueRepository.save(issue);
                }
            }
        }
        auditService.log(orgId, actorUserId, "kb.quality.rule.apply",
                "kbId=" + rule.getKnowledgeBaseId() + ",ruleId=" + rule.getId() + ",updated=" + updated);
        return Map.of(
                "ruleId", rule.getId(),
                "updatedCount", updated,
                "skippedCount", skipped,
                "updatedChunkIds", updatedChunkIds);
    }

    @Transactional
    public Map<String, Object> suggestAnnotations(String orgId, Long kbId, String actorUserId, AnnotationSuggestCommand command) {
        requireKb(orgId, kbId);
        String targetType = command == null || command.targetType() == null || command.targetType().isBlank()
                ? "CHUNK"
                : command.targetType().trim().toUpperCase(Locale.ROOT);
        String fieldKey = normalizeFieldKey(command == null ? null : command.fieldKey(), "topic");
        ensureMetadataField(orgId, kbId, fieldKey, fieldKey);
        int limit = sanitizeLimit(command == null ? null : command.limit(), 50);
        List<KbAnnotationSuggestionEntity> created = new ArrayList<>();
        if ("DOCUMENT".equals(targetType)) {
            for (KbDocumentEntity doc : documentRepository.findByOrgIdAndKnowledgeBaseIdAndStatusNotOrderByIdDesc(orgId, kbId, "DELETED")) {
                Optional<LabelSuggestion> label = suggestLabel(doc.getName(), fieldKey);
                label.ifPresent(value -> created.add(suggestionRepository.save(new KbAnnotationSuggestionEntity(
                        orgId, kbId, "DOCUMENT", doc.getId(), doc.getId(), null, fieldKey,
                        value.value(), value.confidence(), "HEURISTIC", value.rationale()))));
                if (created.size() >= limit) {
                    break;
                }
            }
        } else {
            for (KbChunkEntity chunk : activeChunks(orgId, kbId)) {
                Optional<LabelSuggestion> label = suggestLabel(chunk.getContent(), fieldKey);
                label.ifPresent(value -> created.add(suggestionRepository.save(new KbAnnotationSuggestionEntity(
                        orgId, kbId, "CHUNK", chunk.getId(), chunk.getDocumentId(), chunk.getId(), fieldKey,
                        value.value(), value.confidence(), "HEURISTIC", value.rationale()))));
                if (created.size() >= limit) {
                    break;
                }
            }
        }
        auditService.log(orgId, actorUserId, "kb.annotation.suggest",
                "kbId=" + kbId + ",targetType=" + targetType + ",count=" + created.size());
        return Map.of(
                "createdCount", created.size(),
                "items", created.stream().map(this::suggestionPayload).toList());
    }

    public List<Map<String, Object>> listSuggestions(String orgId, Long kbId, String status) {
        requireKb(orgId, kbId);
        String normalized = status == null || status.isBlank() ? "PENDING" : status.trim().toUpperCase(Locale.ROOT);
        return suggestionRepository.findTop100ByOrgIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(orgId, kbId, normalized)
                .stream()
                .map(this::suggestionPayload)
                .toList();
    }

    @Transactional
    public Map<String, Object> acceptSuggestion(String orgId, Long suggestionId, String actorUserId, AnnotationReviewCommand command) {
        KbAnnotationSuggestionEntity suggestion = suggestionRepository.findByIdAndOrgId(suggestionId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Annotation suggestion not found"));
        String value = command == null || command.value() == null || command.value().isBlank()
                ? suggestion.getSuggestedValue()
                : command.value().trim();
        if ("DOCUMENT".equals(suggestion.getTargetType())) {
            upsertDocumentMetadata(orgId, suggestion.getKnowledgeBaseId(), suggestion.getDocumentId(), suggestion.getFieldKey(), value);
        } else {
            upsertChunkAnnotation(orgId, suggestion, value, actorUserId);
        }
        suggestion.accept(actorUserId, value);
        suggestionRepository.save(suggestion);
        auditService.log(orgId, actorUserId, "kb.annotation.accept",
                "kbId=" + suggestion.getKnowledgeBaseId() + ",suggestionId=" + suggestion.getId());
        return suggestionPayload(suggestion);
    }

    @Transactional
    public Map<String, Object> rejectSuggestion(String orgId, Long suggestionId, String actorUserId) {
        KbAnnotationSuggestionEntity suggestion = suggestionRepository.findByIdAndOrgId(suggestionId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Annotation suggestion not found"));
        suggestion.reject(actorUserId);
        suggestionRepository.save(suggestion);
        auditService.log(orgId, actorUserId, "kb.annotation.reject",
                "kbId=" + suggestion.getKnowledgeBaseId() + ",suggestionId=" + suggestion.getId());
        return suggestionPayload(suggestion);
    }

    public List<Map<String, Object>> listChunkAnnotations(String orgId, Long kbId) {
        requireKb(orgId, kbId);
        return chunkAnnotationRepository.findTop100ByOrgIdAndKnowledgeBaseIdOrderByUpdatedAtDesc(orgId, kbId)
                .stream()
                .map(this::chunkAnnotationPayload)
                .toList();
    }

    private int scanDuplicates(String orgId, Long kbId, Long runId, List<KbChunkEntity> chunks) {
        Map<String, List<KbChunkEntity>> byHash = chunks.stream()
                .filter(chunk -> chunk.getContentHash() != null && !chunk.getContentHash().isBlank())
                .collect(Collectors.groupingBy(KbChunkEntity::getContentHash));
        int count = 0;
        for (List<KbChunkEntity> duplicates : byHash.values()) {
            if (duplicates.size() < 2) {
                continue;
            }
            List<Long> ids = duplicates.stream().map(KbChunkEntity::getId).sorted().toList();
            for (KbChunkEntity chunk : duplicates) {
                issueRepository.save(new KbQualityIssueEntity(
                        orgId, kbId, runId, "DUPLICATE", "MEDIUM", chunk.getId(), chunk.getDocumentId(), null,
                        chunk.getContentHash(), "Same content hash appears in chunks " + ids));
                count++;
            }
        }
        return count;
    }

    private int scanInvalidChunks(String orgId, Long kbId, Long runId, List<KbChunkEntity> chunks) {
        int count = 0;
        for (KbChunkEntity chunk : chunks) {
            for (String reason : invalidReasons(chunk.getContent())) {
                issueRepository.save(new KbQualityIssueEntity(
                        orgId, kbId, runId, reason, "HIGH", chunk.getId(), chunk.getDocumentId(), null,
                        chunk.getContentHash(), "Detected invalid content reason: " + reason));
                count++;
            }
        }
        return count;
    }

    private int scanRegexRules(String orgId, Long kbId, Long runId, List<KbChunkEntity> chunks, List<KbQualityRuleEntity> rules) {
        int count = 0;
        for (KbQualityRuleEntity rule : rules) {
            if (!requiresPattern(rule.getRuleType()) || rule.getPattern() == null || rule.getPattern().isBlank()) {
                continue;
            }
            Pattern pattern = Pattern.compile(rule.getPattern(), Pattern.MULTILINE);
            for (KbChunkEntity chunk : chunks) {
                if (pattern.matcher(chunk.getContent()).find()) {
                    issueRepository.save(new KbQualityIssueEntity(
                            orgId, kbId, runId, "REGEX_MATCH", "MEDIUM", chunk.getId(), chunk.getDocumentId(), rule.getId(),
                            chunk.getContentHash(), "Rule `" + rule.getName() + "` matched chunk content."));
                    count++;
                }
            }
        }
        return count;
    }

    private List<KbChunkEntity> activeChunks(String orgId, Long kbId) {
        return chunkRepository.findByOrgIdAndKnowledgeBaseIdAndStatusNot(orgId, String.valueOf(kbId), "DELETED").stream()
                .filter(KbChunkEntity::isSearchable)
                .toList();
    }

    private List<KbChunkEntity> targetChunks(String orgId, Long kbId, QualityApplyCommand command) {
        if (command != null && command.issueIds() != null && !command.issueIds().isEmpty()) {
            List<Long> chunkIds = issueRepository.findByIdInAndOrgIdAndKnowledgeBaseId(command.issueIds(), orgId, kbId)
                    .stream()
                    .filter(issue -> "OPEN".equals(issue.getStatus()))
                    .map(KbQualityIssueEntity::getChunkId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            return chunkRepository.findByIdInAndOrgId(chunkIds, orgId).stream().filter(KbChunkEntity::isSearchable).toList();
        }
        if (command != null && command.chunkIds() != null && !command.chunkIds().isEmpty()) {
            return chunkRepository.findByIdInAndOrgId(command.chunkIds(), orgId).stream()
                    .filter(chunk -> String.valueOf(kbId).equals(chunk.getKnowledgeBaseId()))
                    .filter(KbChunkEntity::isSearchable)
                    .toList();
        }
        return activeChunks(orgId, kbId);
    }

    private List<String> invalidReasons(String content) {
        String text = content == null ? "" : content.trim();
        ArrayList<String> reasons = new ArrayList<>();
        if (text.isBlank()) {
            reasons.add("EMPTY");
            return reasons;
        }
        if (text.length() < 12) {
            reasons.add("TOO_SHORT");
        }
        if (text.length() > 3800) {
            reasons.add("TOO_LONG");
        }
        if (URL_ONLY.matcher(text).matches()) {
            reasons.add("URL_ONLY");
        }
        long whitespace = text.chars().filter(Character::isWhitespace).count();
        if (text.length() > 80 && whitespace > text.length() * 0.55) {
            reasons.add("EXCESSIVE_WHITESPACE");
        }
        long lettersOrDigits = text.chars().filter(Character::isLetterOrDigit).count();
        if (text.length() > 20 && lettersOrDigits < text.length() * 0.25) {
            reasons.add("NOISY_SYMBOLS");
        }
        return reasons;
    }

    private String applyRuleToText(KbQualityRuleEntity rule, String input) {
        String text = input == null ? "" : input;
        return switch (rule.getRuleType()) {
            case "REGEX_REMOVE" -> Pattern.compile(rule.getPattern(), Pattern.MULTILINE).matcher(text).replaceAll("");
            case "REGEX_REPLACE" -> Pattern.compile(rule.getPattern(), Pattern.MULTILINE).matcher(text)
                    .replaceAll(rule.getReplacement() == null ? "" : rule.getReplacement());
            case "TRIM" -> text.trim();
            case "COLLAPSE_WHITESPACE" -> text.replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            case "REMOVE_EMPTY_LINES" -> text.replaceAll("(?m)^[ \\t]*\\r?\\n", "").trim();
            default -> text;
        };
    }

    private Optional<LabelSuggestion> suggestLabel(String input, String fieldKey) {
        String text = (input == null ? "" : input).toLowerCase(Locale.ROOT);
        LinkedHashMap<String, List<String>> labels = new LinkedHashMap<>();
        labels.put("私有云", List.of("私有云", "专有云", "本地部署", "离线部署", "private cloud", "on-premise", "on premise"));
        labels.put("公有云", List.of("公有云", "saas", "云服务", "public cloud"));
        labels.put("产品功能", List.of("功能", "模块", "能力", "产品"));
        labels.put("安全合规", List.of("安全", "权限", "审计", "合规", "脱敏"));
        labels.put("运维部署", List.of("部署", "运维", "升级", "备份", "回滚", "deployment", "deploy", "upgrade", "backup", "rollback"));
        labels.put("集成接口", List.of("api", "接口", "webhook", "集成", "连接器"));
        labels.put("计费套餐", List.of("计费", "套餐", "额度", "credits", "账单"));
        for (Map.Entry<String, List<String>> entry : labels.entrySet()) {
            for (String term : entry.getValue()) {
                if (text.contains(term.toLowerCase(Locale.ROOT))) {
                    return Optional.of(new LabelSuggestion(entry.getKey(), 0.78,
                            "Field `" + fieldKey + "` matched keyword `" + term + "`."));
                }
            }
        }
        if (text.length() > 80) {
            return Optional.of(new LabelSuggestion("待归类", 0.42, "Content is long enough for manual topic review."));
        }
        return Optional.empty();
    }

    private void upsertDocumentMetadata(String orgId, Long kbId, Long documentId, String fieldKey, String value) {
        ensureMetadataField(orgId, kbId, fieldKey, fieldKey);
        KbDocumentMetadataEntity item = documentMetadataRepository
                .findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndFieldKey(orgId, kbId, documentId, fieldKey)
                .orElseGet(() -> new KbDocumentMetadataEntity(orgId, kbId, documentId, fieldKey, value));
        item.setStringValue(value);
        documentMetadataRepository.save(item);
    }

    private void upsertChunkAnnotation(String orgId, KbAnnotationSuggestionEntity suggestion, String value, String actor) {
        KbChunkAnnotationEntity item = chunkAnnotationRepository
                .findByOrgIdAndChunkIdAndFieldKey(orgId, suggestion.getChunkId(), suggestion.getFieldKey())
                .orElseGet(() -> new KbChunkAnnotationEntity(
                        orgId,
                        suggestion.getKnowledgeBaseId(),
                        suggestion.getChunkId(),
                        suggestion.getDocumentId(),
                        suggestion.getFieldKey(),
                        value,
                        "ACCEPTED_SUGGESTION",
                        actor));
        item.updateValue(value, "ACCEPTED_SUGGESTION", actor);
        chunkAnnotationRepository.save(item);
    }

    private void ensureMetadataField(String orgId, Long kbId, String fieldKey, String fieldName) {
        metadataFieldRepository.findByOrgIdAndKnowledgeBaseIdAndFieldKey(orgId, kbId, fieldKey)
                .orElseGet(() -> metadataFieldRepository.save(new KbMetadataFieldEntity(
                        orgId, kbId, fieldKey, fieldName == null || fieldName.isBlank() ? fieldKey : fieldName, "STRING")));
    }

    private KnowledgeBaseEntity requireKb(String orgId, Long kbId) {
        return kbRepository.findByIdAndOrgId(kbId, orgId)
                .orElseThrow(() -> new IllegalArgumentException("Knowledge base not found"));
    }

    private String normalizeRuleType(String type) {
        String normalized = required(type, "Rule type is required").toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of("REGEX_REMOVE", "REGEX_REPLACE", "TRIM", "COLLAPSE_WHITESPACE", "REMOVE_EMPTY_LINES");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported rule type: " + type);
        }
        return normalized;
    }

    private void validatePatternIfNeeded(String ruleType, String pattern) {
        if (!requiresPattern(ruleType)) {
            return;
        }
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Regex pattern is required");
        }
        try {
            Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            throw new IllegalArgumentException("Invalid regex pattern: " + ex.getMessage());
        }
    }

    private boolean requiresPattern(String ruleType) {
        return "REGEX_REMOVE".equals(ruleType) || "REGEX_REPLACE".equals(ruleType);
    }

    private String normalizeFieldKey(String fieldKey, String fallback) {
        String value = fieldKey == null || fieldKey.isBlank() ? fallback : fieldKey.trim().toLowerCase(Locale.ROOT);
        String normalized = value.replaceAll("[^a-z0-9_\\-]", "_");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Field key is required");
        }
        return normalized.substring(0, Math.min(64, normalized.length()));
    }

    private int sanitizeLimit(Integer limit, int fallback) {
        return Math.min(200, Math.max(1, limit == null ? fallback : limit));
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private Map<String, Object> rulePayload(KbQualityRuleEntity rule) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", rule.getId());
        row.put("knowledgeBaseId", rule.getKnowledgeBaseId());
        row.put("name", rule.getName());
        row.put("ruleType", rule.getRuleType());
        row.put("pattern", rule.getPattern() == null ? "" : rule.getPattern());
        row.put("replacement", rule.getReplacement() == null ? "" : rule.getReplacement());
        row.put("enabled", rule.isEnabled());
        row.put("createdBy", rule.getCreatedBy() == null ? "" : rule.getCreatedBy());
        row.put("createdAt", rule.getCreatedAt().toString());
        row.put("updatedAt", rule.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> runPayload(KbQualityRunEntity run) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", run.getId());
        row.put("knowledgeBaseId", run.getKnowledgeBaseId());
        row.put("status", run.getStatus());
        row.put("triggerType", run.getTriggerType());
        row.put("scannedChunkCount", run.getScannedChunkCount());
        row.put("duplicateIssueCount", run.getDuplicateIssueCount());
        row.put("invalidIssueCount", run.getInvalidIssueCount());
        row.put("regexIssueCount", run.getRegexIssueCount());
        row.put("totalIssueCount", run.getTotalIssueCount());
        row.put("errorMessage", run.getErrorMessage() == null ? "" : run.getErrorMessage());
        row.put("startedAt", run.getStartedAt().toString());
        row.put("finishedAt", run.getFinishedAt() == null ? "" : run.getFinishedAt().toString());
        row.put("createdBy", run.getCreatedBy() == null ? "" : run.getCreatedBy());
        return row;
    }

    private Map<String, Object> issuePayload(KbQualityIssueEntity issue) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", issue.getId());
        row.put("knowledgeBaseId", issue.getKnowledgeBaseId());
        row.put("runId", issue.getRunId());
        row.put("issueType", issue.getIssueType());
        row.put("severity", issue.getSeverity());
        row.put("targetType", issue.getTargetType());
        row.put("targetId", issue.getTargetId());
        row.put("documentId", issue.getDocumentId() == null ? "" : issue.getDocumentId());
        row.put("chunkId", issue.getChunkId() == null ? "" : issue.getChunkId());
        row.put("ruleId", issue.getRuleId() == null ? "" : issue.getRuleId());
        row.put("contentHash", issue.getContentHash() == null ? "" : issue.getContentHash());
        row.put("evidence", issue.getEvidence() == null ? "" : issue.getEvidence());
        row.put("status", issue.getStatus());
        row.put("createdAt", issue.getCreatedAt().toString());
        row.put("updatedAt", issue.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> suggestionPayload(KbAnnotationSuggestionEntity suggestion) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", suggestion.getId());
        row.put("knowledgeBaseId", suggestion.getKnowledgeBaseId());
        row.put("targetType", suggestion.getTargetType());
        row.put("targetId", suggestion.getTargetId());
        row.put("documentId", suggestion.getDocumentId() == null ? "" : suggestion.getDocumentId());
        row.put("chunkId", suggestion.getChunkId() == null ? "" : suggestion.getChunkId());
        row.put("fieldKey", suggestion.getFieldKey());
        row.put("suggestedValue", suggestion.getSuggestedValue());
        row.put("confidence", suggestion.getConfidence());
        row.put("source", suggestion.getSource());
        row.put("rationale", suggestion.getRationale() == null ? "" : suggestion.getRationale());
        row.put("status", suggestion.getStatus());
        row.put("createdAt", suggestion.getCreatedAt().toString());
        row.put("updatedAt", suggestion.getUpdatedAt().toString());
        return row;
    }

    private Map<String, Object> chunkAnnotationPayload(KbChunkAnnotationEntity item) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.getId());
        row.put("knowledgeBaseId", item.getKnowledgeBaseId());
        row.put("chunkId", item.getChunkId());
        row.put("documentId", item.getDocumentId() == null ? "" : item.getDocumentId());
        row.put("fieldKey", item.getFieldKey());
        row.put("value", item.getStringValue());
        row.put("source", item.getSource());
        row.put("createdBy", item.getCreatedBy() == null ? "" : item.getCreatedBy());
        row.put("updatedAt", item.getUpdatedAt().toString());
        return row;
    }

    public record QualityScanCommand(String triggerType) {
    }

    public record QualityRuleCommand(String name, String ruleType, String pattern, String replacement, Boolean enabled) {
    }

    public record QualityApplyCommand(List<Long> chunkIds, List<Long> issueIds, Map<Long, String> expectedContentHashes, Integer limit) {
    }

    public record AnnotationSuggestCommand(String targetType, String fieldKey, Integer limit) {
    }

    public record AnnotationReviewCommand(String value) {
    }

    private record LabelSuggestion(String value, double confidence, String rationale) {
    }
}
