package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, Long> {

    List<KbDocumentEntity> findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(String orgId, Long knowledgeBaseId);

    List<KbDocumentEntity> findByOrgIdAndKnowledgeBaseIdAndStatusNotOrderByIdDesc(String orgId, Long knowledgeBaseId, String status);

    List<KbDocumentEntity> findByOrgIdAndKnowledgeBaseIdAndStatusNot(String orgId, Long knowledgeBaseId, String status);

    List<KbDocumentEntity> findByOrgIdAndStatusNot(String orgId, String status);

    List<KbDocumentEntity> findByIdInAndOrgId(List<Long> ids, String orgId);

    Optional<KbDocumentEntity> findByIdAndOrgId(Long id, String orgId);

    long countByOrgIdAndKnowledgeBaseIdAndStatusNot(String orgId, Long knowledgeBaseId, String status);

    long countByOrgIdAndKnowledgeBaseIdAndStatus(String orgId, Long knowledgeBaseId, String status);

    void deleteByIdAndOrgId(Long id, String orgId);
}
