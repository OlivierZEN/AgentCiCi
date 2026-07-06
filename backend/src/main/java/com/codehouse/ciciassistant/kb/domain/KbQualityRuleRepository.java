package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbQualityRuleRepository extends JpaRepository<KbQualityRuleEntity, Long> {

    List<KbQualityRuleEntity> findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(String orgId, Long knowledgeBaseId);

    List<KbQualityRuleEntity> findByOrgIdAndKnowledgeBaseIdAndEnabledTrueOrderByIdAsc(String orgId, Long knowledgeBaseId);

    Optional<KbQualityRuleEntity> findByIdAndOrgId(Long id, String orgId);
}
