package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkRepository extends JpaRepository<KbChunkEntity, Long> {

    List<KbChunkEntity> findTop5ByCompanyIdAndKnowledgeBaseIdIn(String companyId, List<String> knowledgeBaseIds);

    List<KbChunkEntity> findTop50ByCompanyIdAndKnowledgeBaseIdInAndStatusAndEnabledTrueOrderByIdDesc(
            String companyId,
            List<String> knowledgeBaseIds,
            String status);

    List<KbChunkEntity> findByCompanyIdAndDocumentIdAndStatusNot(String companyId, Long documentId, String status);

    List<KbChunkEntity> findByCompanyIdAndKnowledgeBaseIdAndStatusNot(String companyId, String knowledgeBaseId, String status);

    List<KbChunkEntity> findByCompanyIdAndStatusNot(String companyId, String status);

    List<KbChunkEntity> findByCompanyIdAndDocumentIdAndStatusNotOrderByChunkIndexAscIdAsc(String companyId, Long documentId, String status);

    List<KbChunkEntity> findByIdInAndCompanyId(List<Long> ids, String companyId);

    Optional<KbChunkEntity> findByIdAndCompanyId(Long id, String companyId);

    long countByCompanyIdAndDocumentIdAndStatusAndEnabledTrue(String companyId, Long documentId, String status);

    long countByCompanyIdAndKnowledgeBaseIdAndStatusAndEnabledTrue(String companyId, String knowledgeBaseId, String status);
}
