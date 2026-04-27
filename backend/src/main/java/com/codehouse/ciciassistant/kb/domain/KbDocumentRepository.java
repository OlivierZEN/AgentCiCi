package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDocumentRepository extends JpaRepository<KbDocumentEntity, Long> {

    List<KbDocumentEntity> findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(String orgId, Long knowledgeBaseId);

    Optional<KbDocumentEntity> findByIdAndOrgId(Long id, String orgId);

    void deleteByIdAndOrgId(Long id, String orgId);
}
