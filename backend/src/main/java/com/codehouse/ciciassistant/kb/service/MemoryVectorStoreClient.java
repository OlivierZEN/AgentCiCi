package com.codehouse.ciciassistant.kb.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.kb.vector-store", havingValue = "memory", matchIfMissing = true)
public class MemoryVectorStoreClient implements VectorStoreClient {

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public String upsert(VectorUpsertCommand command) {
        String vectorId = UUID.randomUUID().toString();
        store.put(vectorId, new Entry(
                vectorId,
                command.orgId(),
                command.knowledgeBaseId(),
                command.documentId(),
                command.chunkId(),
                command.chunkIndex(),
                command.content(),
                command.embedding()));
        return vectorId;
    }

    @Override
    public List<VectorSearchHit> search(VectorSearchQuery query) {
        if (query.knowledgeBaseIds() == null || query.knowledgeBaseIds().isEmpty()) {
            return List.of();
        }
        return store.values().stream()
                .filter(entry -> query.orgId().equals(entry.orgId()))
                .filter(entry -> query.knowledgeBaseIds().contains(entry.knowledgeBaseId()))
                .map(entry -> new Scored(entry, cosine(query.queryEmbedding(), entry.embedding())))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(1, query.topK()))
                .map(scored -> new VectorSearchHit(
                        scored.entry().vectorId(),
                        scored.entry().knowledgeBaseId(),
                        scored.entry().documentId(),
                        scored.entry().chunkId(),
                        scored.entry().chunkIndex(),
                        scored.entry().content(),
                        scored.score()))
                .toList();
    }

    @Override
    public VectorDeleteResult deleteByVectorIds(String orgId, List<String> vectorIds) {
        if (vectorIds == null || vectorIds.isEmpty()) {
            return VectorDeleteResult.success(0, 0);
        }
        int deleted = 0;
        for (String vectorId : vectorIds) {
            Entry existing = store.get(vectorId);
            if (existing != null && orgId.equals(existing.orgId())) {
                store.remove(vectorId);
                deleted++;
            }
        }
        return VectorDeleteResult.success(vectorIds.size(), deleted);
    }

    @Override
    public VectorDeleteResult deleteByDocument(String orgId, String knowledgeBaseId, Long documentId) {
        if (documentId == null) {
            return VectorDeleteResult.success(0, 0);
        }
        List<String> ids = store.values().stream()
                .filter(entry -> orgId.equals(entry.orgId()))
                .filter(entry -> knowledgeBaseId.equals(entry.knowledgeBaseId()))
                .filter(entry -> documentId.equals(entry.documentId()))
                .map(Entry::vectorId)
                .toList();
        return deleteByVectorIds(orgId, ids);
    }

    @Override
    public VectorDeleteResult deleteByKnowledgeBase(String orgId, String knowledgeBaseId) {
        List<String> ids = store.values().stream()
                .filter(entry -> orgId.equals(entry.orgId()))
                .filter(entry -> knowledgeBaseId.equals(entry.knowledgeBaseId()))
                .map(Entry::vectorId)
                .toList();
        return deleteByVectorIds(orgId, ids);
    }

    private double cosine(List<Float> left, List<Float> right) {
        double dot = 0.0;
        double l = 0.0;
        double r = 0.0;
        int size = Math.min(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            dot += left.get(i) * right.get(i);
            l += left.get(i) * left.get(i);
            r += right.get(i) * right.get(i);
        }
        if (l == 0.0 || r == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(l) * Math.sqrt(r));
    }

    private record Entry(String vectorId,
                         String orgId,
                         String knowledgeBaseId,
                         Long documentId,
                         Long chunkId,
                         Integer chunkIndex,
                         String content,
                         List<Float> embedding) {
    }

    private record Scored(Entry entry, double score) {
    }
}
