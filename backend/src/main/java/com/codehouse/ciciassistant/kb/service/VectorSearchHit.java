package com.codehouse.ciciassistant.kb.service;

public record VectorSearchHit(
        String vectorId,
        String knowledgeBaseId,
        Long documentId,
        Long chunkId,
        Integer chunkIndex,
        String content,
        double score
) {
}
