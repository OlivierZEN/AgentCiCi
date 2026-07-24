package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentMetadataRepository extends JpaRepository<KbDocumentMetadataEntity, Long> {

    List<KbDocumentMetadataEntity> findByCompanyIdAndKnowledgeBaseIdAndDocumentId(String companyId, Long knowledgeBaseId, Long documentId);

    Optional<KbDocumentMetadataEntity> findByCompanyIdAndKnowledgeBaseIdAndDocumentIdAndFieldKey(
            String companyId,
            Long knowledgeBaseId,
            Long documentId,
            String fieldKey);

    void deleteByCompanyIdAndKnowledgeBaseIdAndDocumentId(String companyId, Long knowledgeBaseId, Long documentId);
}
