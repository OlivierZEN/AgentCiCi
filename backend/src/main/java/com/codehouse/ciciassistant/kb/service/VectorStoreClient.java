package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public interface VectorStoreClient {

    String upsert(String orgId, String knowledgeBaseId, String content, List<Float> embedding);

    List<String> search(String orgId, List<String> knowledgeBaseIds, String query, List<Float> queryEmbedding, int topK);
}
