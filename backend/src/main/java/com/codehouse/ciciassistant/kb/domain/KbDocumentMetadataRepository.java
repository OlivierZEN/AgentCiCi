package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentMetadataRepository extends JpaRepository<KbDocumentMetadataEntity, Long> {

    List<KbDocumentMetadataEntity> findByOrgIdAndKnowledgeBaseIdAndDocumentId(String orgId, Long knowledgeBaseId, Long documentId);

    Optional<KbDocumentMetadataEntity> findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndFieldKey(
            String orgId,
            Long knowledgeBaseId,
            Long documentId,
            String fieldKey);

    void deleteByOrgIdAndKnowledgeBaseIdAndDocumentId(String orgId, Long knowledgeBaseId, Long documentId);
}
