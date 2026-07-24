package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public record VectorSearchQuery(
        String companyId,
        List<String> knowledgeBaseIds,
        String query,
        List<Float> queryEmbedding,
        int topK
) {
}
