package com.codehouse.ciciassistant.kb.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbQualityIssueRepository extends JpaRepository<KbQualityIssueEntity, Long> {

    List<KbQualityIssueEntity> findTop100ByCompanyIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(
            String companyId, Long knowledgeBaseId, String status);

    List<KbQualityIssueEntity> findByIdInAndCompanyIdAndKnowledgeBaseId(Collection<Long> ids, String companyId, Long knowledgeBaseId);

    Optional<KbQualityIssueEntity> findByIdAndCompanyId(Long id, String companyId);
}
