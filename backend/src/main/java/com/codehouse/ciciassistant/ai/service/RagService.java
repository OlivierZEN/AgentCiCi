package com.codehouse.ciciassistant.ai.service;

import com.codehouse.ciciassistant.kb.domain.KbChunkEntity;
import com.codehouse.ciciassistant.kb.domain.KbChunkRepository;
import com.codehouse.ciciassistant.kb.service.EmbeddingService;
import com.codehouse.ciciassistant.kb.service.VectorStoreClient;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RagService {

    private final KbChunkRepository kbChunkRepository;
    private final VectorStoreClient vectorStoreClient;
    private final EmbeddingService embeddingService;

    public RagService(KbChunkRepository kbChunkRepository,
                      VectorStoreClient vectorStoreClient,
                      EmbeddingService embeddingService) {
        this.kbChunkRepository = kbChunkRepository;
        this.vectorStoreClient = vectorStoreClient;
        this.embeddingService = embeddingService;
    }

    public List<String> retrieveContext(String orgId, List<String> knowledgeBaseIds, String query) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> vectorRecall = vectorStoreClient.search(
                orgId,
                knowledgeBaseIds,
                query,
                embeddingService.embed(query),
                5);
        if (!vectorRecall.isEmpty()) {
            return vectorRecall;
        }
        return kbChunkRepository.findTop5ByOrgIdAndKnowledgeBaseIdIn(orgId, knowledgeBaseIds).stream()
                .map(KbChunkEntity::getContent)
                .collect(Collectors.toList());
    }
}
