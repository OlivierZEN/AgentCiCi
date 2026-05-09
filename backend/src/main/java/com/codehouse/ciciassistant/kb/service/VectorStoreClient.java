package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public interface VectorStoreClient {

    String upsert(VectorUpsertCommand command);

    List<VectorSearchHit> search(VectorSearchQuery query);

    VectorDeleteResult deleteByVectorIds(String orgId, List<String> vectorIds);

    VectorDeleteResult deleteByDocument(String orgId, String knowledgeBaseId, Long documentId);

    VectorDeleteResult deleteByKnowledgeBase(String orgId, String knowledgeBaseId);

    VectorStoreAuditResult auditOrgVectors(String orgId, List<String> registeredVectorIds);
}
