package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbQualityRunRepository extends JpaRepository<KbQualityRunEntity, Long> {

    List<KbQualityRunEntity> findTop20ByOrgIdAndKnowledgeBaseIdOrderByCreatedAtDesc(String orgId, Long knowledgeBaseId);
}
