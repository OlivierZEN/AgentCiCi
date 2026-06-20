package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentMetadataRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.kb.service.EmbeddingService;
import com.codehouse.ciciassistant.kb.service.KbAccessControlService;
import com.codehouse.ciciassistant.kb.service.VectorSearchHit;
import com.codehouse.ciciassistant.kb.service.VectorSearchQuery;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final KbChunkRepository kbChunkRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final KbDocumentMetadataRepository kbDocumentMetadataRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;
    private final KbAccessControlService kbAccessControlService;

    public RagService(KbChunkRepository kbChunkRepository,
                      KbDocumentRepository kbDocumentRepository,
                      KbDocumentMetadataRepository kbDocumentMetadataRepository,
                      KnowledgeBaseRepository knowledgeBaseRepository,
                      VectorStoreClient vectorStoreClient,
                      EmbeddingService embeddingService,
                      KbAccessControlService kbAccessControlService) {
        this.kbChunkRepository = kbChunkRepository;
        this.kbDocumentRepository = kbDocumentRepository;
        this.kbDocumentMetadataRepository = kbDocumentMetadataRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
        this.kbAccessControlService = kbAccessControlService;
    }

    public List<String> retrieveContext(String orgId, List<String> knowledgeBaseIds, String query) {
        return retrieveDetailed(orgId, knowledgeBaseIds, query).context();
    }

    public RetrievalResult retrieveDetailed(String orgId, List<String> knowledgeBaseIds, String query) {
        return retrieveDetailed(orgId, knowledgeBaseIds, query, Map.of());
    }

    public RetrievalResult retrieveDetailed(String orgId, List<String> knowledgeBaseIds, String query, Map<String, String> metadataFilters) {
        return retrieveDetailed(orgId, knowledgeBaseIds, query, metadataFilters, KbAccessControlService.AccessPrincipal.system());
    }

    public RetrievalResult retrieveDetailed(String orgId,
                                            List<String> knowledgeBaseIds,
                                            String query,
                                            Map<String, String> metadataFilters,
                                            KbAccessControlService.AccessPrincipal principal) {
        long started = System.nanoTime();
        Map<String, Long> timingsMs = new LinkedHashMap<>();
        Map<String, String> normalizedFilters = normalizeMetadataFilters(metadataFilters);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            timingsMs.put("total", elapsedMs(started));
            return new RetrievalResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), timingsMs, false, normalizedFilters, 0);
        }
        long validationStarted = System.nanoTime();
        Map<String, Double> scoreThresholdByKb = new HashMap<>();
        Map<String, EmbeddingConfig> embeddingConfigByKb = new HashMap<>();
        int topK = 0;
        List<Long> requestedNumericIds = new ArrayList<>();
        Set<Long> seenNumericIds = new LinkedHashSet<>();
        for (String kbId : knowledgeBaseIds) {
            if (kbId == null || kbId.isBlank()) {
                continue;
            }
            Optional<Long> numericId = parseLong(kbId);
            if (numericId.isEmpty() || !seenNumericIds.add(numericId.get())) {
                continue;
            }
            requestedNumericIds.add(numericId.get());
        }
        if (requestedNumericIds.isEmpty()) {
            timingsMs.put("validation", elapsedMs(validationStarted));
            timingsMs.put("total", elapsedMs(started));
            return new RetrievalResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), timingsMs, false, normalizedFilters, 0);
        }
        Map<Long, KnowledgeBaseEntity> kbById = knowledgeBaseRepository.findByOrgIdAndIdIn(orgId, requestedNumericIds).stream()
                .collect(Collectors.toMap(KnowledgeBaseEntity::getId, item -> item));
        List<String> allowedKnowledgeBaseIds = new ArrayList<>();
        List<RetrievedKnowledgeBase> retrievedKnowledgeBases = new ArrayList<>();
        for (Long id : requestedNumericIds) {
            KnowledgeBaseEntity kb = kbById.get(id);
            if (kb == null || !"ACTIVE".equals(kb.getStatus())) {
                continue;
            }
            String kbId = String.valueOf(id);
            allowedKnowledgeBaseIds.add(kbId);
            retrievedKnowledgeBases.add(new RetrievedKnowledgeBase(kbId, kb.getName()));
            scoreThresholdByKb.put(kbId, kb.getScoreThreshold());
            embeddingConfigByKb.put(kbId, new EmbeddingConfig(
                    kb.getEmbeddingProvider(),
                    kb.getEmbeddingModel(),
                    kb.getEmbeddingDimension()));
            topK = Math.max(topK, kb.getTopK());
        }
        timingsMs.put("validation", elapsedMs(validationStarted));
        if (allowedKnowledgeBaseIds.isEmpty()) {
            timingsMs.put("total", elapsedMs(started));
            return new RetrievalResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), timingsMs, false, normalizedFilters, 0);
        }
        int safeTopK = Math.max(1, Math.min(20, topK == 0 ? 5 : topK));
        long embeddingStarted = System.nanoTime();
        Map<EmbeddingConfig, List<String>> kbIdsByEmbedding = new LinkedHashMap<>();
        for (String kbId : allowedKnowledgeBaseIds) {
            EmbeddingConfig config = embeddingConfigByKb.get(kbId);
            if (config == null) {
                config = new EmbeddingConfig("local", "local-hash", 1024);
            }
            kbIdsByEmbedding.computeIfAbsent(config, ignored -> new ArrayList<>()).add(kbId);
        }
        List<GroupEmbedding> groupEmbeddings = new ArrayList<>();
        for (Map.Entry<EmbeddingConfig, List<String>> entry : kbIdsByEmbedding.entrySet()) {
            EmbeddingConfig config = entry.getKey();
            groupEmbeddings.add(new GroupEmbedding(
                    entry.getValue(),
                    embeddingService.embed(orgId, config.provider(), config.model(), config.dimension(), query)));
        }
        timingsMs.put("embedding", elapsedMs(embeddingStarted));
        long vectorStarted = System.nanoTime();
        List<VectorSearchHit> hits = new ArrayList<>();
        for (GroupEmbedding item : groupEmbeddings) {
            hits.addAll(vectorStoreClient.search(new VectorSearchQuery(
                    orgId,
                    item.knowledgeBaseIds(),
                    query,
                    item.embedding(),
                    safeTopK)));
        }
        hits.sort((left, right) -> Double.compare(right.score(), left.score()));
        timingsMs.put("vectorSearch", elapsedMs(vectorStarted));
        long filterStarted = System.nanoTime();
        Set<String> activeKnowledgeBaseIds = new HashSet<>(allowedKnowledgeBaseIds);
        PermissionFilterCounter permissionFilterCounter = new PermissionFilterCounter();
        List<RetrievedSource> vectorSources = filterVectorHits(
                orgId,
                hits,
                safeTopK,
                scoreThresholdByKb,
                activeKnowledgeBaseIds,
                kbById,
                normalizedFilters,
                principal,
                permissionFilterCounter);
        timingsMs.put("filter", elapsedMs(filterStarted));
        if (!vectorSources.isEmpty()) {
            timingsMs.put("total", elapsedMs(started));
            return new RetrievalResult(
                    vectorSources.stream().map(RetrievedSource::content).toList(),
                    vectorSources,
                    retrievedKnowledgeBases,
                    timingsMs,
                    false,
                    normalizedFilters,
                    permissionFilterCounter.count());
        }
        long fallbackStarted = System.nanoTime();
        List<KbChunkEntity> fallbackCandidates = kbChunkRepository.findTop50ByOrgIdAndKnowledgeBaseIdInAndStatusAndEnabledTrueOrderByIdDesc(
                        orgId,
                        allowedKnowledgeBaseIds,
                        "ACTIVE");
        List<RetrievedSource> fallbackSources = filterSearchableChunks(
                        orgId,
                        fallbackCandidates,
                        safeTopK,
                        activeKnowledgeBaseIds,
                        kbById,
                        normalizedFilters,
                        principal,
                        permissionFilterCounter).stream()
                .limit(safeTopK)
                .collect(Collectors.toList());
        timingsMs.put("fallback", elapsedMs(fallbackStarted));
        timingsMs.put("total", elapsedMs(started));
        return new RetrievalResult(
                fallbackSources.stream().map(RetrievedSource::content).toList(),
                fallbackSources,
                retrievedKnowledgeBases,
                timingsMs,
                true,
                normalizedFilters,
                permissionFilterCounter.count());
    }

    private List<RetrievedSource> filterVectorHits(String orgId,
                                                   List<VectorSearchHit> hits,
                                                   int topK,
                                                   Map<String, Double> scoreThresholdByKb,
                                                   Set<String> activeKnowledgeBaseIds,
                                                   Map<Long, KnowledgeBaseEntity> kbById,
                                                   Map<String, String> metadataFilters,
                                                   KbAccessControlService.AccessPrincipal principal,
                                                   PermissionFilterCounter permissionFilterCounter) {
        ArrayList<VectorSearchHit> candidates = new ArrayList<>();
        LinkedHashSet<Long> chunkIds = new LinkedHashSet<>();
        for (VectorSearchHit hit : hits) {
            if (hit.chunkId() == null) {
                continue;
            }
            if (!activeKnowledgeBaseIds.contains(hit.knowledgeBaseId())) {
                continue;
            }
            double threshold = scoreThresholdByKb.getOrDefault(hit.knowledgeBaseId(), 0.0);
            if (hit.score() < threshold) {
                continue;
            }
            candidates.add(hit);
            chunkIds.add(hit.chunkId());
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, KbChunkEntity> chunkById = kbChunkRepository.findByIdInAndOrgId(new ArrayList<>(chunkIds), orgId).stream()
                .collect(Collectors.toMap(KbChunkEntity::getId, item -> item));
        Map<Long, KbDocumentEntity> documentById = loadDocuments(orgId, chunkById.values().stream().toList());
        ArrayList<RetrievedSource> out = new ArrayList<>();
        for (VectorSearchHit hit : candidates) {
            KbChunkEntity chunk = chunkById.get(hit.chunkId());
            KbDocumentEntity document = chunk == null ? null : documentById.get(chunk.getDocumentId());
            if (chunk != null
                    && isChunkSearchable(chunk, activeKnowledgeBaseIds, documentById)
                    && canReadChunk(orgId, chunk, document, principal, permissionFilterCounter)
                    && matchesMetadataFilters(orgId, chunk, document, metadataFilters)) {
                out.add(toRetrievedSource(chunk, document, kbById, hit.score(), "vector"));
                if (out.size() >= topK) {
                    break;
                }
            }
        }
        return out;
    }

    private List<RetrievedSource> filterSearchableChunks(String orgId,
                                                         List<KbChunkEntity> chunks,
                                                         int topK,
                                                         Set<String> activeKnowledgeBaseIds,
                                                         Map<Long, KnowledgeBaseEntity> kbById,
                                                         Map<String, String> metadataFilters,
                                                         KbAccessControlService.AccessPrincipal principal,
                                                         PermissionFilterCounter permissionFilterCounter) {
        Map<Long, KbDocumentEntity> documentById = loadDocuments(orgId, chunks);
        ArrayList<RetrievedSource> out = new ArrayList<>();
        for (KbChunkEntity chunk : chunks) {
            KbDocumentEntity document = documentById.get(chunk.getDocumentId());
            if (isChunkSearchable(chunk, activeKnowledgeBaseIds, documentById)
                    && canReadChunk(orgId, chunk, document, principal, permissionFilterCounter)
                    && matchesMetadataFilters(orgId, chunk, document, metadataFilters)) {
                out.add(toRetrievedSource(chunk, document, kbById, 0.0, "fallback"));
                if (out.size() >= topK) {
                    break;
                }
            }
        }
        return out;
    }

    private Map<Long, KbDocumentEntity> loadDocuments(String orgId, List<KbChunkEntity> chunks) {
        List<Long> documentIds = chunks.stream()
                .map(KbChunkEntity::getDocumentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return kbDocumentRepository.findByIdInAndOrgId(documentIds, orgId).stream()
                .collect(Collectors.toMap(KbDocumentEntity::getId, item -> item));
    }

    private boolean isChunkSearchable(KbChunkEntity chunk,
                                      Set<String> activeKnowledgeBaseIds,
                                      Map<Long, KbDocumentEntity> documentById) {
        if (!chunk.isSearchable()) {
            return false;
        }
        if (!activeKnowledgeBaseIds.contains(chunk.getKnowledgeBaseId())) {
            return false;
        }
        Long documentId = chunk.getDocumentId();
        if (documentId == null) {
            return true;
        }
        KbDocumentEntity doc = documentById.get(documentId);
        if (doc == null) {
            return false;
        }
        return doc.isEnabled()
                && !doc.isArchived()
                && "PUBLISHED".equals(doc.getStatus())
                && String.valueOf(doc.getKnowledgeBaseId()).equals(chunk.getKnowledgeBaseId());
    }

    private boolean matchesMetadataFilters(String orgId,
                                           KbChunkEntity chunk,
                                           KbDocumentEntity document,
                                           Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        if (chunk == null || document == null || document.getId() == null) {
            return false;
        }
        List<KbDocumentMetadataEntity> metadata = kbDocumentMetadataRepository.findByOrgIdAndKnowledgeBaseIdAndDocumentId(
                orgId,
                document.getKnowledgeBaseId(),
                document.getId());
        if (metadata.isEmpty()) {
            return false;
        }
        Map<String, String> found = metadata.stream()
                .collect(Collectors.toMap(KbDocumentMetadataEntity::getFieldKey, KbDocumentMetadataEntity::getStringValue, (a, b) -> b));
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            String actual = found.get(filter.getKey());
            if (actual == null || !actual.equalsIgnoreCase(filter.getValue())) {
                return false;
            }
        }
        return true;
    }

    private boolean canReadChunk(String orgId,
                                 KbChunkEntity chunk,
                                 KbDocumentEntity document,
                                 KbAccessControlService.AccessPrincipal principal,
                                 PermissionFilterCounter permissionFilterCounter) {
        Long knowledgeBaseId = parseLong(chunk.getKnowledgeBaseId()).orElse(null);
        boolean allowed = knowledgeBaseId != null
                && kbAccessControlService.canReadChunk(
                orgId,
                knowledgeBaseId,
                document == null ? chunk.getDocumentId() : document.getId(),
                chunk.getId(),
                principal);
        if (!allowed) {
            permissionFilterCounter.increment();
        }
        return allowed;
    }

    private RetrievedSource toRetrievedSource(KbChunkEntity chunk,
                                              KbDocumentEntity document,
                                              Map<Long, KnowledgeBaseEntity> kbById,
                                              double score,
                                              String sourceType) {
        Long kbNumericId = parseLong(chunk.getKnowledgeBaseId()).orElse(null);
        KnowledgeBaseEntity kb = kbNumericId == null ? null : kbById.get(kbNumericId);
        return new RetrievedSource(
                chunk.getContent(),
                chunk.getKnowledgeBaseId(),
                kb == null ? "" : kb.getName(),
                document == null || document.getId() == null ? null : document.getId(),
                document == null ? "" : document.getName(),
                chunk.getId(),
                chunk.getChunkIndex(),
                score,
                sourceType);
    }

    private Map<String, String> normalizeMetadataFilters(Map<String, String> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().trim().toLowerCase().replace(' ', '_');
            String value = entry.getValue().trim();
            if (!key.isBlank() && key.matches("[a-z0-9_\\-]{2,64}") && !value.isBlank()) {
                out.put(key, value);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private long elapsedMs(long startedNs) {
        return Math.max(0L, (System.nanoTime() - startedNs) / 1_000_000L);
    }

    private Optional<Long> parseLong(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public record RetrievalResult(List<String> context,
                                  List<RetrievedSource> sources,
                                  List<RetrievedKnowledgeBase> knowledgeBases,
                                  Map<String, Long> timingsMs,
                                  boolean fallbackUsed,
                                  Map<String, String> metadataFilters,
                                  long permissionFilteredCount) {
    }

    public record RetrievedKnowledgeBase(String id, String name) {
    }

    public record RetrievedSource(String content,
                                  String knowledgeBaseId,
                                  String knowledgeBaseName,
                                  Long documentId,
                                  String documentName,
                                  Long chunkId,
                                  Integer chunkIndex,
                                  double score,
                                  String sourceType) {

        public Map<String, Object> toPayload() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("knowledgeBaseId", knowledgeBaseId == null ? "" : knowledgeBaseId);
            payload.put("knowledgeBaseName", knowledgeBaseName == null ? "" : knowledgeBaseName);
            payload.put("documentId", documentId == null ? "" : documentId);
            payload.put("documentName", documentName == null ? "" : documentName);
            payload.put("chunkId", chunkId == null ? "" : chunkId);
            payload.put("chunkIndex", chunkIndex == null ? "" : chunkIndex);
            payload.put("score", score);
            payload.put("sourceType", sourceType == null ? "" : sourceType);
            payload.put("contentPreview", content == null || content.length() <= 220 ? content == null ? "" : content : content.substring(0, 219) + "…");
            return payload;
        }
    }

    private record EmbeddingConfig(String provider, String model, Integer dimension) {
    }

    private record GroupEmbedding(List<String> knowledgeBaseIds, List<Float> embedding) {
    }

    private static final class PermissionFilterCounter {
        private long count;

        private void increment() {
            count++;
        }

        private long count() {
            return count;
        }
    }
}
