package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbQualityRuleRepository extends JpaRepository<KbQualityRuleEntity, Long> {

    List<KbQualityRuleEntity> findByCompanyIdAndKnowledgeBaseIdOrderByIdDesc(String companyId, Long knowledgeBaseId);

    List<KbQualityRuleEntity> findByCompanyIdAndKnowledgeBaseIdAndEnabledTrueOrderByIdAsc(String companyId, Long knowledgeBaseId);

    Optional<KbQualityRuleEntity> findByIdAndCompanyId(Long id, String companyId);
}
