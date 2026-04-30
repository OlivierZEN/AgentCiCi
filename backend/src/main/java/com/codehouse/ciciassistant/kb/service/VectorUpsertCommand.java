package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public record VectorUpsertCommand(
        String orgId,
        String knowledgeBaseId,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String content,
        String contentHash,
        List<Float> embedding
) {
}
