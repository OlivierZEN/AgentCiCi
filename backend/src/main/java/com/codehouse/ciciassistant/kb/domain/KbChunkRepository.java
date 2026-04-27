package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkRepository extends JpaRepository<KbChunkEntity, Long> {

    List<KbChunkEntity> findTop5ByOrgIdAndKnowledgeBaseIdIn(String orgId, List<String> knowledgeBaseIds);
}
