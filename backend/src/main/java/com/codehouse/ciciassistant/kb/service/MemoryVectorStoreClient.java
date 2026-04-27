package com.codehouse.ciciassistant.kb.service;

import java.util.ArrayList;
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

    private final Map<String, List<Entry>> store = new ConcurrentHashMap<>();

    @Override
    public String upsert(String orgId, String knowledgeBaseId, String content, List<Float> embedding) {
        String vectorId = UUID.randomUUID().toString();
        String key = orgId + "::" + knowledgeBaseId;
        store.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Entry(vectorId, content, embedding));
        return vectorId;
    }

    @Override
    public List<String> search(String orgId, List<String> knowledgeBaseIds, String query, List<Float> queryEmbedding, int topK) {
        ArrayList<Scored> all = new ArrayList<>();
        for (String kbId : knowledgeBaseIds) {
            for (Entry entry : store.getOrDefault(orgId + "::" + kbId, List.of())) {
                all.add(new Scored(entry.content(), cosine(queryEmbedding, entry.embedding())));
            }
        }
        return all.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(Math.max(1, topK))
                .map(Scored::content)
                .toList();
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

    private record Entry(String vectorId, String content, List<Float> embedding) {
    }

    private record Scored(String content, double score) {
    }
}
