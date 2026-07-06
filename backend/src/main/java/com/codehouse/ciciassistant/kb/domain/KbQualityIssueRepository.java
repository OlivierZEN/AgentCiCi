package com.codehouse.ciciassistant.kb.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbQualityIssueRepository extends JpaRepository<KbQualityIssueEntity, Long> {

    List<KbQualityIssueEntity> findTop100ByOrgIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(
            String orgId, Long knowledgeBaseId, String status);

    List<KbQualityIssueEntity> findByIdInAndOrgIdAndKnowledgeBaseId(Collection<Long> ids, String orgId, Long knowledgeBaseId);

    Optional<KbQualityIssueEntity> findByIdAndOrgId(Long id, String orgId);
}
