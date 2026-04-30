package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkRepository extends JpaRepository<KbChunkEntity, Long> {

    List<KbChunkEntity> findTop5ByOrgIdAndKnowledgeBaseIdIn(String orgId, List<String> knowledgeBaseIds);

    List<KbChunkEntity> findTop50ByOrgIdAndKnowledgeBaseIdInAndStatusAndEnabledTrueOrderByIdDesc(
            String orgId,
            List<String> knowledgeBaseIds,
            String status);

    List<KbChunkEntity> findByOrgIdAndDocumentIdAndStatusNot(String orgId, Long documentId, String status);

    List<KbChunkEntity> findByOrgIdAndKnowledgeBaseIdAndStatusNot(String orgId, String knowledgeBaseId, String status);

    List<KbChunkEntity> findByOrgIdAndDocumentIdAndStatusNotOrderByChunkIndexAscIdAsc(String orgId, Long documentId, String status);

    Optional<KbChunkEntity> findByIdAndOrgId(Long id, String orgId);

    long countByOrgIdAndDocumentIdAndStatusAndEnabledTrue(String orgId, Long documentId, String status);

    long countByOrgIdAndKnowledgeBaseIdAndStatusAndEnabledTrue(String orgId, String knowledgeBaseId, String status);
}
