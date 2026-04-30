package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.domain.KbDocumentEntity;
import com.codehouse.ciciassistant.kb.domain.KbDocumentRepository;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseEntity;
import com.codehouse.ciciassistant.kb.domain.KnowledgeBaseRepository;
import com.codehouse.ciciassistant.kb.service.EmbeddingService;
import com.codehouse.ciciassistant.kb.service.VectorSearchHit;
import com.codehouse.ciciassistant.kb.service.VectorSearchQuery;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final KbChunkRepository kbChunkRepository;
    private final KbDocumentRepository kbDocumentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;

    public RagService(KbChunkRepository kbChunkRepository,
                      KbDocumentRepository kbDocumentRepository,
                      KnowledgeBaseRepository knowledgeBaseRepository,
                      VectorStoreClient vectorStoreClient,
                      EmbeddingService embeddingService) {
        this.kbChunkRepository = kbChunkRepository;
        this.kbDocumentRepository = kbDocumentRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
    }

    public List<String> retrieveContext(String orgId, List<String> knowledgeBaseIds, String query) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, Double> scoreThresholdByKb = new HashMap<>();
        int topK = 0;
        List<String> allowedKnowledgeBaseIds = new ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            if (kbId == null || kbId.isBlank() || allowedKnowledgeBaseIds.contains(kbId)) {
                continue;
            }
            Optional<Long> numericId = parseLong(kbId);
            if (numericId.isEmpty()) {
                continue;
            }
            Optional<KnowledgeBaseEntity> kb = knowledgeBaseRepository.findByIdAndOrgId(numericId.get(), orgId);
            if (kb.isEmpty() || !"ACTIVE".equals(kb.get().getStatus())) {
                continue;
            }
            allowedKnowledgeBaseIds.add(kbId);
            scoreThresholdByKb.put(kbId, kb.get().getScoreThreshold());
            topK = Math.max(topK, kb.get().getTopK());
        }
        if (allowedKnowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }
        int safeTopK = Math.max(1, Math.min(20, topK == 0 ? 5 : topK));
        List<String> vectorRecall = filterVectorHits(orgId, vectorStoreClient.search(new VectorSearchQuery(
                orgId,
                allowedKnowledgeBaseIds,
                query,
                embeddingService.embed(query),
                safeTopK)), safeTopK, scoreThresholdByKb);
        if (!vectorRecall.isEmpty()) {
            return vectorRecall;
        }
        return kbChunkRepository.findTop50ByOrgIdAndKnowledgeBaseIdInAndStatusAndEnabledTrueOrderByIdDesc(
                        orgId,
                        allowedKnowledgeBaseIds,
                        "ACTIVE").stream()
                .filter(chunk -> isChunkSearchable(orgId, chunk))
                .limit(safeTopK)
                .map(KbChunkEntity::getContent)
                .collect(Collectors.toList());
    }

    private List<String> filterVectorHits(String orgId,
                                          List<VectorSearchHit> hits,
                                          int topK,
                                          Map<String, Double> scoreThresholdByKb) {
        ArrayList<String> out = new ArrayList<>();
        for (VectorSearchHit hit : hits) {
            if (hit.chunkId() == null) {
                continue;
            }
            double threshold = scoreThresholdByKb.getOrDefault(hit.knowledgeBaseId(), 0.0);
            if (hit.score() < threshold) {
                continue;
            }
            Optional<KbChunkEntity> chunk = kbChunkRepository.findByIdAndOrgId(hit.chunkId(), orgId);
            if (chunk.isPresent() && isChunkSearchable(orgId, chunk.get())) {
                out.add(chunk.get().getContent());
            }
            if (out.size() >= topK) {
                break;
            }
        }
        return out;
    }

    private boolean isChunkSearchable(String orgId, KbChunkEntity chunk) {
        if (!chunk.isSearchable()) {
            return false;
        }
        if (!isKnowledgeBaseSearchable(orgId, chunk.getKnowledgeBaseId())) {
            return false;
        }
        Long documentId = chunk.getDocumentId();
        if (documentId == null) {
            return true;
        }
        Optional<KbDocumentEntity> document = kbDocumentRepository.findByIdAndOrgId(documentId, orgId);
        if (document.isEmpty()) {
            return false;
        }
        KbDocumentEntity doc = document.get();
        return doc.isEnabled()
                && !doc.isArchived()
                && "PUBLISHED".equals(doc.getStatus())
                && String.valueOf(doc.getKnowledgeBaseId()).equals(chunk.getKnowledgeBaseId());
    }

    private boolean isKnowledgeBaseSearchable(String orgId, String knowledgeBaseId) {
        Optional<Long> numericId = parseLong(knowledgeBaseId);
        if (numericId.isEmpty()) {
            return true;
        }
        Optional<KnowledgeBaseEntity> kb = knowledgeBaseRepository.findByIdAndOrgId(numericId.get(), orgId);
        return kb.map(item -> "ACTIVE".equals(item.getStatus())).orElse(false);
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
}
