package com.codehouse.ciciassistant.kb.service;

import java.util.List;

public interface VectorStoreClient {

    String upsert(VectorUpsertCommand command);

    List<VectorSearchHit> search(VectorSearchQuery query);

    VectorDeleteResult deleteByVectorIds(String companyId, List<String> vectorIds);

    VectorDeleteResult deleteByDocument(String companyId, String knowledgeBaseId, Long documentId);

    VectorDeleteResult deleteByKnowledgeBase(String companyId, String knowledgeBaseId);

    VectorStoreAuditResult auditOrgVectors(String companyId, List<String> registeredVectorIds);
}
