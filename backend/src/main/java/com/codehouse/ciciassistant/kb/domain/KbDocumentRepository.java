package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, Long> {

    List<KbDocumentEntity> findByCompanyIdAndKnowledgeBaseIdOrderByIdDesc(String companyId, Long knowledgeBaseId);

    List<KbDocumentEntity> findByCompanyIdAndKnowledgeBaseIdAndStatusNotOrderByIdDesc(String companyId, Long knowledgeBaseId, String status);

    List<KbDocumentEntity> findByCompanyIdAndKnowledgeBaseIdAndStatusNot(String companyId, Long knowledgeBaseId, String status);

    List<KbDocumentEntity> findByCompanyIdAndStatusNot(String companyId, String status);

    List<KbDocumentEntity> findByIdInAndCompanyId(List<Long> ids, String companyId);

    Optional<KbDocumentEntity> findByIdAndCompanyId(Long id, String companyId);

    long countByCompanyIdAndKnowledgeBaseIdAndStatusNot(String companyId, Long knowledgeBaseId, String status);

    long countByCompanyIdAndKnowledgeBaseIdAndStatus(String companyId, Long knowledgeBaseId, String status);

    void deleteByIdAndCompanyId(Long id, String companyId);
}
